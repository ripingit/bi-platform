/**
 * Copyright (c) 2014 Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baidu.rigel.biplatform.tesseract.qsservice.query.impl;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baidu.rigel.biplatform.ac.exception.MiniCubeQueryException;
import com.baidu.rigel.biplatform.ac.minicube.MiniCubeMember;
import com.baidu.rigel.biplatform.ac.model.Cube;
import com.baidu.rigel.biplatform.ac.query.data.DataModel;
import com.baidu.rigel.biplatform.ac.query.data.DataSourceInfo;
import com.baidu.rigel.biplatform.ac.query.model.ConfigQuestionModel;
import com.baidu.rigel.biplatform.ac.query.model.PageInfo;
import com.baidu.rigel.biplatform.ac.query.model.QuestionModel;
import com.baidu.rigel.biplatform.ac.query.model.SortRecord;
import com.baidu.rigel.biplatform.ac.util.DataModelUtils;
import com.baidu.rigel.biplatform.ac.util.MetaNameUtil;
import com.baidu.rigel.biplatform.tesseract.datasource.DataSourcePoolService;
import com.baidu.rigel.biplatform.tesseract.exception.MetaException;
import com.baidu.rigel.biplatform.tesseract.exception.OverflowQueryConditionException;
import com.baidu.rigel.biplatform.tesseract.isservice.exception.IndexAndSearchException;
import com.baidu.rigel.biplatform.tesseract.isservice.search.service.SearchService;
import com.baidu.rigel.biplatform.tesseract.meta.MetaDataService;
import com.baidu.rigel.biplatform.tesseract.model.MemberNodeTree;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.QueryContextBuilder;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.QueryContextSplitService;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.QueryContextSplitService.QueryContextSplitStrategy;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.QueryRequestBuilder;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.QueryService;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.vo.QueryContext;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.vo.QueryContextSplitResult;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.vo.QueryRequest;
import com.baidu.rigel.biplatform.tesseract.resultset.TesseractResultSet;
import com.baidu.rigel.biplatform.tesseract.util.DataModelBuilder;

/**
 * 查询接口实现
 * 
 * @author xiaoming.chen
 *
 */
@Service
public class QueryServiceImpl implements QueryService {

    /**
     * Logger
     */
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * searchService
     */
    @Resource
    private SearchService searchService;

    /**
     * metaDataService
     */
    @Resource
    private MetaDataService metaDataService;

    /**
     * dataSourcePoolService
     */
    @Resource
    private DataSourcePoolService dataSourcePoolService;

    /**
     * queryContextSplitService
     */
    @Resource
    private QueryContextSplitService queryContextSplitService;
    
    @Resource
    private QueryContextBuilder queryContextBuilder;

    @Override
    public DataModel query(QuestionModel questionModel, QueryContext queryContext,
            QueryContextSplitStrategy preSplitStrategy) throws MiniCubeQueryException {
        long current = System.currentTimeMillis();
        if (questionModel == null) {
            throw new IllegalArgumentException("questionModel is null");
        }
        DataSourceInfo dataSourceInfo = null;
        Cube cube = null;
        // 如果是
        if (questionModel instanceof ConfigQuestionModel) {
            ConfigQuestionModel configQuestionModel = (ConfigQuestionModel) questionModel;
            dataSourceInfo = configQuestionModel.getDataSourceInfo();
            cube = configQuestionModel.getCube();
            // 如果是配置端查询的话，默认不使用cache
//            questionModel.setUseIndex(false);
        }
        if (cube == null) {
            cube = metaDataService.getCube(questionModel.getCubeId());
        }
        if (dataSourceInfo == null) {
            dataSourceInfo = dataSourcePoolService.getDataSourceInfo(questionModel.getDataSourceInfoKey());
        }
        logger.info("cost :" + (System.currentTimeMillis() - current) + " to get datasource and other data");
        current = System.currentTimeMillis();
        try {
            queryContext =
                    queryContextBuilder.buildQueryContext(questionModel, dataSourceInfo, cube, queryContext);
        } catch (MetaException e1) {
            e1.printStackTrace();
            throw new MiniCubeQueryException(e1);
        }
        logger.info("cost :" + (System.currentTimeMillis() - current) + " to build query context.");
        // 条件笛卡尔积，计算查询中条件数和根据汇总条件填充汇总条件
        int conditionDescartes = stateQueryContextConditionCount(queryContext, questionModel.isNeedSummary());
        logger.info("query condition descarte:" + conditionDescartes);
        logger.debug("question model:" + questionModel);
        if (questionModel.getQueryConditionLimit().isWarningAtOverFlow()
                && conditionDescartes > questionModel.getQueryConditionLimit().getWarnningConditionSize()) {
            StringBuilder sb = new StringBuilder();
            sb.append("condition descartes :").append(conditionDescartes).append(" over :")
                    .append(questionModel.getQueryConditionLimit()).append("");
            logger.error(sb.toString());
            throw new OverflowQueryConditionException(sb.toString());
        }
        // 调用拆解自动进行拆解
        QueryContextSplitResult splitResult = queryContextSplitService.split(questionModel, dataSourceInfo, cube, queryContext, preSplitStrategy);
        DataModel result = null;
        // 无法拆分或者 拆分出的结果为空，说明直接处理本地就行
        if (splitResult != null && !splitResult.getCompileContexts().isEmpty()) {
            DataSourceInfo dsInfo = dataSourceInfo;
            Cube finalCube = cube;
            // TODO 抛出到其它节点去,后续需要修改成调用其它节点的方法
            splitResult.getConditionQueryContext().forEach((con, context) -> {
                        splitResult.getDataModels().put(
                                con,
                                executeQuery(dsInfo, finalCube, context, questionModel.isUseIndex(),
                                        questionModel.getPageInfo()));
            });
            
            result = queryContextSplitService.mergeDataModel(splitResult);
        } else {
            
            result = executeQuery(dataSourceInfo, cube, queryContext,questionModel.isUseIndex(), questionModel.getPageInfo());
        }
        if (result != null) {
            result = sortAndTrunc(result, questionModel.getSortRecord());
        }
        return result;

    }

    private DataModel executeQuery(DataSourceInfo dataSourceInfo, Cube cube,
            QueryContext queryContext,boolean useIndex, PageInfo pageInfo) throws MiniCubeQueryException {
        long current = System.currentTimeMillis();
        QueryRequest queryRequest =
                QueryRequestBuilder.buildQueryRequest(dataSourceInfo, cube, queryContext, useIndex,pageInfo);
        logger.info("transfer queryContext:{} to queryRequest:{} cost:{} ", queryContext, queryRequest, System.currentTimeMillis() - current);
        if (statDimensionNode(queryContext.getRowMemberTrees(), false, false) == 0
                || (statDimensionNode(queryContext.getColumnMemberTrees(), false, false) == 0 && CollectionUtils
                        .isEmpty(queryContext.getQueryMeasures()))) {
            return new DataModelBuilder(null, queryContext).build();
        }
        logger.info("cost :" + (System.currentTimeMillis() - current) + " to build query request.");
        current = System.currentTimeMillis();
        DataModel result = null;
        try {
            TesseractResultSet resultSet = searchService.query(queryRequest);
            result = new DataModelBuilder(resultSet, queryContext).build();
        } catch (IndexAndSearchException e) {
            logger.error("query occur when search queryRequest：" + queryContext, e);
            throw new MiniCubeQueryException(e);
        }
        logger.info("cost :" + (System.currentTimeMillis() - current) + " to execute query.");
        return result;
    }

    /**
     * 排序并截断结果集，默认显示500条纪录
     * @param result
     * @param sortRecord
     * @return DataModel
     */
    private DataModel sortAndTrunc(DataModel result, SortRecord sortRecord) {
    		if (sortRecord != null) {
    			DataModelUtils.sortDataModelBySort(result, sortRecord);
    		}
    		int recordSize = sortRecord == null ? 500 : sortRecord.getRecordSize();
		return DataModelUtils.truncModel(result, recordSize); 
	}

	private int stateQueryContextConditionCount(QueryContext context, boolean needSummary) {
        if (context == null) {
            throw new IllegalArgumentException("querycontext is null.");
        }
        // 统计行上的总条件数
        int rowConditionCount = statDimensionNode(context.getRowMemberTrees(), needSummary, true);
        // 列上的维度叶子数
        int columnConditionCount = statDimensionNode(context.getColumnMemberTrees(), needSummary, false);

        int filterConditionCount = 1;
        if (MapUtils.isNotEmpty(context.getFilterMemberValues())) {
            for (Set<String> nodeIds : context.getFilterMemberValues().values()) {
                filterConditionCount *= nodeIds.size();
            }
        }

        return rowConditionCount * columnConditionCount * filterConditionCount;
    }

    /**
     * 统计维值信息，根据是否需要查询汇总节点，补全汇总节点查询条件
     * 
     * @param treeNodes
     * @param needSummary
     * @return
     */
    private int statDimensionNode(List<MemberNodeTree> treeNodes, boolean needSummary, boolean isRow) {
        int rowConditionCount = 0;
        if (CollectionUtils.isNotEmpty(treeNodes)) {
            for (MemberNodeTree nodeTree : treeNodes) {
                int dimensionLeafIdCount = 0;
                // 如果根节点的name不为空，那么说明根节点是汇总节点，只需要获取根节点就可以
                if (StringUtils.isBlank(nodeTree.getName()) || MetaNameUtil.isAllMemberName(nodeTree.getName())) {
                    // 统计节点下孩子节点对应的叶子数，如果需要展现汇总节点的话，那么还需要将子节点的叶子节点合并到一起构造汇总节点的查询条件
                    for (MemberNodeTree child : nodeTree.getChildren()) {
                        // 暂时只支持在行上汇总，列上汇总有点怪怪的。。需要再开启
                        if (isRow && needSummary) {
                            nodeTree.setName(MiniCubeMember.SUMMARY_NODE_NAME);
                            nodeTree.setCaption(MiniCubeMember.SUMMARY_NODE_CAPTION);
                            nodeTree.setQuerySource(child.getQuerySource());
                            nodeTree.getLeafIds().addAll(child.getLeafIds());
                        }
                        if (nodeTree.getLeafIds().size() == 1
                                && MetaNameUtil.isAllMemberName(nodeTree.getLeafIds().iterator().next())) {
                            continue;
                        } else {
                            dimensionLeafIdCount += child.getLeafIds().size();
                        }
                    }
                } else {
                    dimensionLeafIdCount = nodeTree.getLeafIds().size();
                }
                if (rowConditionCount == 0) {
                    rowConditionCount = dimensionLeafIdCount;
                }else{
                    // 需要保证dimensionLeafIdCount不能为0
                    rowConditionCount *= (dimensionLeafIdCount == 0 ? 1 : dimensionLeafIdCount);
                }
            }
        }
        return rowConditionCount;
    }

    

    

}
