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
package com.baidu.rigel.biplatform.ma.resource;

import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.baidu.rigel.biplatform.ac.exception.MiniCubeQueryException;
import com.baidu.rigel.biplatform.ac.minicube.MiniCube;
import com.baidu.rigel.biplatform.ac.minicube.TimeDimension;
import com.baidu.rigel.biplatform.ac.model.Cube;
import com.baidu.rigel.biplatform.ac.model.Dimension;
import com.baidu.rigel.biplatform.ac.model.Member;
import com.baidu.rigel.biplatform.ac.model.OlapElement;
import com.baidu.rigel.biplatform.ac.query.data.DataModel;
import com.baidu.rigel.biplatform.ac.query.data.HeadField;
import com.baidu.rigel.biplatform.ac.query.model.SortRecord;
import com.baidu.rigel.biplatform.ac.util.DeepcopyUtils;
import com.baidu.rigel.biplatform.ac.util.HttpRequest;
import com.baidu.rigel.biplatform.ac.util.MetaNameUtil;
import com.baidu.rigel.biplatform.ma.ds.exception.DataSourceOperationException;
import com.baidu.rigel.biplatform.ma.ds.service.DataSourceService;
import com.baidu.rigel.biplatform.ma.model.builder.Director;
import com.baidu.rigel.biplatform.ma.model.consts.Constants;
import com.baidu.rigel.biplatform.ma.model.service.CubeBuildService;
import com.baidu.rigel.biplatform.ma.model.service.PositionType;
import com.baidu.rigel.biplatform.ma.model.service.StarModelBuildService;
import com.baidu.rigel.biplatform.ma.model.utils.GsonUtils;
import com.baidu.rigel.biplatform.ma.report.exception.CacheOperationException;
import com.baidu.rigel.biplatform.ma.report.exception.PivotTableParseException;
import com.baidu.rigel.biplatform.ma.report.exception.QueryModelBuildException;
import com.baidu.rigel.biplatform.ma.report.model.ExtendArea;
import com.baidu.rigel.biplatform.ma.report.model.ExtendAreaType;
import com.baidu.rigel.biplatform.ma.report.model.Item;
import com.baidu.rigel.biplatform.ma.report.model.LiteOlapExtendArea;
import com.baidu.rigel.biplatform.ma.report.model.LogicModel;
import com.baidu.rigel.biplatform.ma.report.model.MeasureTopSetting;
import com.baidu.rigel.biplatform.ma.report.model.ReportDesignModel;
import com.baidu.rigel.biplatform.ma.report.query.QueryAction;
import com.baidu.rigel.biplatform.ma.report.query.QueryContext;
import com.baidu.rigel.biplatform.ma.report.query.ReportRuntimeModel;
import com.baidu.rigel.biplatform.ma.report.query.ResultSet;
import com.baidu.rigel.biplatform.ma.report.query.chart.DIReportChart;
import com.baidu.rigel.biplatform.ma.report.query.chart.SeriesInputInfo.SeriesUnitType;
import com.baidu.rigel.biplatform.ma.report.query.pivottable.PivotTable;
import com.baidu.rigel.biplatform.ma.report.query.pivottable.RowDefine;
import com.baidu.rigel.biplatform.ma.report.query.pivottable.RowHeadField;
import com.baidu.rigel.biplatform.ma.report.service.AnalysisChartBuildService;
import com.baidu.rigel.biplatform.ma.report.service.ChartBuildService;
import com.baidu.rigel.biplatform.ma.report.service.QueryBuildService;
import com.baidu.rigel.biplatform.ma.report.service.ReportDesignModelService;
import com.baidu.rigel.biplatform.ma.report.service.ReportModelQueryService;
import com.baidu.rigel.biplatform.ma.report.utils.QueryUtils;
import com.baidu.rigel.biplatform.ma.report.utils.ReportDesignModelUtils;
import com.baidu.rigel.biplatform.ma.resource.cache.ReportModelCacheManager;
import com.baidu.rigel.biplatform.ma.resource.utils.DataModelUtils;
import com.baidu.rigel.biplatform.ma.resource.utils.ResourceUtils;
import com.baidu.rigel.biplatform.ma.resource.view.vo.DimensionMemberViewObject;
import com.baidu.rigel.biplatform.ma.rt.ExtendAreaContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


/**
 * CubeTable的页面交互
 * 
 * @author zhongyi
 * 
 *         2014-7-30
 */
@RestController
@RequestMapping("/silkroad/reports")
public class QueryDataResource extends BaseResource {
    
    /**
     * logger
     */
    private Logger logger = LoggerFactory.getLogger(QueryDataResource.class);
    
    /**
     * reportModelCacheManager
     */
    @Resource
    private ReportModelCacheManager reportModelCacheManager;
    
    /**
     * cubeBuildService
     */
    @Resource
    private CubeBuildService cubeBuildService;
    
    /**
     * starModelBuildService
     */
    @Resource
    private StarModelBuildService starModelBuildService;
    
    /**
     * reportDesignModelService
     */
    @Resource(name = "reportDesignModelService")
    private ReportDesignModelService reportDesignModelService;
    
    /**
     * queryBuildService
     */
    @Resource
    private QueryBuildService queryBuildService;
    
    /**
     * analysisChartBuildService
     */
    @Resource
    private AnalysisChartBuildService analysisChartBuildService;
    
    /**
     * 报表数据查询服务
     */
    @Resource
    private ReportModelQueryService reportModelQueryService;
    
    /**
     * chartBuildService
     */
    @Resource
    private ChartBuildService chartBuildService;
    
    /**
     * director
     */
    @Resource
    private Director director;
    
    /**
     * dsService
     */
    @Resource
    private DataSourceService dsService;
    
    /**
     * 初始化查询参数,初始化查询区域参数
     * @param reportId
     * @param request
     * @return ResponseResult
     */
    @RequestMapping(value = "/{reportId}/init_params", method = { RequestMethod.POST })
    public ResponseResult initParams(@PathVariable("reportId") String reportId,
            HttpServletRequest request) {
    		long begin = System.currentTimeMillis();
    		logger.info("[INFO]--- ---begin init params with report id {}", reportId);
    		String[] areaIds = request.getParameter("paramList").split(",");
    		if (areaIds == null || areaIds.length == 0) {
    			ResponseResult rs = new ResponseResult();
    			rs.setStatus(0);
    			logger.info("[INFO]--- --- not needed init global params");
    	        return rs;
    		}
    		final ReportDesignModel model = reportModelCacheManager.getReportModel(reportId);
    		Map<String, Map<String, List<Map<String, String>>>> datas = Maps.newConcurrentMap();
    		for (final String areaId : areaIds) {
			ExtendArea area = model.getExtendById(areaId);
			if (area != null && isQueryComp(area.getType())
					&& !area.listAllItems().isEmpty()) {
				Item item = area.listAllItems().values()
						.toArray(new Item[0])[0];
				Cube cube = model.getSchema().getCubes()
						.get(area.getCubeId());
				Cube tmpCube = QueryUtils.transformCube(cube);
				String dimId = item.getOlapElementId();
				Dimension dim = cube.getDimensions().get(dimId);
				if (dim != null) {
					List<Map<String, String>> values;
					try {
						List<Member> members = reportModelQueryService
								.getMembers(tmpCube, 
										tmpCube.getDimensions().get(dim.getName()),
										Maps.newHashMap()).get(0);
						values = Lists.newArrayList();
						members.forEach(m -> {
							Map<String, String> tmp = Maps
									.newHashMap();
							tmp.put("value", m.getUniqueName());
							tmp.put("text", m.getCaption());
							values.add(tmp);
						});
						Map<String, List<Map<String, String>>> datasource = Maps.newHashMap();
						datasource.put("datasource", values);
						datas.put(areaId, datasource);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
    		}
        ResponseResult rs = new ResponseResult();
        rs.setStatus(0);
        rs.setData(datas);
        rs.setStatusInfo("OK");
        logger.info("[INFO]--- --- successfully init params, cost {} ms", (System.currentTimeMillis() - begin));
        return rs;
    }
    
    /**
     * 
     * @param type 区域类型
     * @return boolean
     */
    private boolean isQueryComp(ExtendAreaType type) {
    		return QueryUtils.isFilterArea(type);
    }
    
    /**
     * 
     * @param reportId
     * @param request
     * @return
     */
    @RequestMapping(value = "/{reportId}/report_id", method = { RequestMethod.GET })
    public ResponseResult getReport(@PathVariable("reportId") String reportId,
            HttpServletRequest request) {
    		long begin = System.currentTimeMillis();
    		logger.info("[INFO] --- --- begin query report model");
        ReportDesignModel model = null;
        try {
            model = reportModelCacheManager.getReportModel(reportId);
        } catch (CacheOperationException e1) {
        		logger.info("[INFO]--- --- can't not get report form cache", e1.getMessage());
            return ResourceUtils.getErrorResult(e1.getMessage(), ResponseResult.FAILED);
        }
        reportModelCacheManager.loadReportModelToCache(reportId);
        ResponseResult rs = ResourceUtils.getCorrectResult("OK", model);
        logger.info("[INFO] --- --- query report model successuffly, cost {} ms", (System.currentTimeMillis() - begin));
        return rs;
    }
    
    @RequestMapping(value = "/{reportId}/report_vm", method = { RequestMethod.GET },
            produces = "text/html;charset=utf-8")
    public String queryVM(@PathVariable("reportId") String reportId, HttpServletRequest request,
            HttpServletResponse response) {
    		long begin = System.currentTimeMillis();
        ReportDesignModel model = null;
        try {
            model = reportModelCacheManager.loadReleaseReportModelToCache(reportId);
        } catch (CacheOperationException e1) {
            logger.info("[INFO]--- ---Fail in loading release report model into cache. ", e1);
            throw new IllegalStateException();
        }
        ReportRuntimeModel runtimeModel = reportModelCacheManager.loadRunTimeModelToCache(reportId);
        // modify by jiangyichao at 2014-10-10 
        // 将url参数添加到全局上下文中
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String paramName = params.nextElement();
            runtimeModel.getContext().put(paramName, request.getParameter(paramName));
        }
        // 添加cookie内容
        runtimeModel.getContext().put(HttpRequest.COOKIE_PARAM_NAME, request.getHeader("Cookie"));
        
        if (model == null) {
        		logger.info("[INFO]--- --- can't get model form cache, please check it!");
            return "";
        }
        reportModelCacheManager.updateReportModelToCache(reportId, model);
        reportModelCacheManager.updateRunTimeModelToCache(reportId, runtimeModel);;
        // TODO 临时方案，以后前端做
        String vm = model.getVmContent();
        String js = "<script type='text/javascript'>" + "\r\n" + "        (function(NS) {" + "\r\n"
                + "            NS.xui.XView.start(" + "\r\n"
                + "                'di.product.display.ui.LayoutPage'," + "\r\n"
                + "                {" + "\r\n" + "                    externalParam: {" + "\r\n"
                + "                    'reportId':'"
                + reportId
                + "','phase':'dev','token':'tieba'},"
                + "\r\n"
                + "                    globalType: 'PRODUCT',"
                + "\r\n"
                + "                    diAgent: '',"
                + "\r\n"
                + "                    reportId: '"
                + reportId
                + "',"
                + "\r\n"
                + "                    webRoot: '/silkroad',"
                + "\r\n"
                + "                    phase: 'dev',"
                + "\r\n"
                + "                    serverTime: ' " + new Date().getTime() + "',"
                + "\r\n"
                + "                    funcAuth: null,"
                + "\r\n"
                + "                    extraOpt: (window.__$DI__NS$__ || {}).OPTIONS"
                + "\r\n"
                + "                }"
                + "\r\n"
                + "            );"
                + "\r\n"
                + "        })(window);"
                + "\r\n" + "    </script>" + "\r\n";
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>");
        builder.append("<html>");
        builder.append("<head>");
        builder.append("<meta content='text/html' 'charset=UTF-8'>");
        builder.append("<link rel='stylesheet' href='/silkroad/asset/css/-di-product-min.css'/>");
        builder.append("</head>");
        builder.append("<body>");
        builder.append(vm);
        
        builder.append("<script src='/silkroad/asset/-di-product-min.js'>");
        builder.append("</script>");
        builder.append(js);
        builder.append("</body>");
        builder.append("</html>");
        response.setCharacterEncoding("utf-8");
        logger.info("[INFO] query vm operation successfully, cost {} ms", (System.currentTimeMillis() - begin));
        return builder.toString();
    }
    
    @RequestMapping(value = "/{reportId}/report_json", method = { RequestMethod.GET },
            produces = "text/plain;charset=utf-8")
    public String queryJson(@PathVariable("reportId") String reportId, HttpServletRequest request,
            HttpServletResponse response) {
    		long begin = System.currentTimeMillis();
        ReportDesignModel model = null;
        try {
            model = reportModelCacheManager.getReportModel(reportId);
        } catch (CacheOperationException e) {
            logger.info("[INFO]--- ---There are no such model in cache. Report Id: " + reportId, e);
            throw new IllegalStateException();
        }
        if (model == null) {
        		logger.info("[INFO]--- --- can't get model form cache, please check it!");
            return "";
        }
        String json = model.getJsonContent();
        response.setCharacterEncoding("utf-8");
        logger.info("[INFO] query json operation successfully, cost {} ms", (System.currentTimeMillis() - begin));
        return json;
    }
    
    /**
     * 
     * @param reportId
     * @param request
     * @return ResponseResult
     */
    @RequestMapping(value = "/{reportId}/runtime_model", method = { RequestMethod.POST })
    public ResponseResult initRunTimeModel(@PathVariable("reportId") String reportId,
            HttpServletRequest request) {
    		long begin = System.currentTimeMillis();
    		logger.info("[INFO]--- ---begin init runtime env");
        boolean edit = Boolean.valueOf(request.getParameter("isEdit"));
        ReportDesignModel model = null;
        if (edit) {
            /**
             * 编辑报表
             */
            model = reportModelCacheManager.loadReportModelToCache(reportId);
        } else {
            /**
             * 如果是新建的报表，从缓存中找
             */
            try {
                model = reportModelCacheManager.getReportModel(reportId);
            } catch (CacheOperationException e) {
                logger.info("[INFO]There are no such model in cache. Report Id: " + reportId, e);
                return ResourceUtils.getErrorResult("缓存中不存在的报表！id: " + reportId, 1);
            }
        }
        ReportRuntimeModel runtimeModel = new ReportRuntimeModel(reportId);
        runtimeModel.init(model, true);
        for (String key : request.getParameterMap().keySet()) {
            String value = request.getParameter(key);
            if (value != null) {
                /**
                 * value 不能是null，但可以为空字符串，空字符串可能有含义
                 */
                runtimeModel.getContext().put(key,value);
            }
            
        }
        reportModelCacheManager.updateRunTimeModelToCache(reportId, runtimeModel);
        reportModelCacheManager.updateReportModelToCache(reportId, model);
        ResponseResult rs = ResourceUtils.getCorrectResult("OK", "");
        logger.info("[INFO] successfully init runtime evn, cost {} ms", (System.currentTimeMillis() - begin));
        return rs;
    }
    
    /**
     * 更新上下文 将格式态的报表模型转化成运形态的报表模型存入缓存 或者依据用户查询逻辑更新运形态报表模型
     * 
     * @param reportId
     * @param areaId
     * @param request
     * @return
     */
    @RequestMapping(value = "/{reportId}/runtime/context", method = { RequestMethod.POST })
    public ResponseResult updateContext(@PathVariable("reportId") String reportId,
            HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        logger.info("[INFO]------begin update global runtime context");
        Map<String, String[]> contextParams = request.getParameterMap();
        ReportRuntimeModel runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        // modify by jiangyichao at 2014-11-06 对时间条件进行特殊处理
        Map<String, Object> oldParams = runTimeModel.getContext().getParams(); 
        Map<String, Object> newParams = Maps.newHashMap();
        for (String key : oldParams.keySet()) {
	        	String value = oldParams.get(key).toString();
	        	if (!(value.contains("start") && value.contains("end"))) {
	        		newParams.put(key, value);
	        	}
        }
        runTimeModel.getContext().reset();
        runTimeModel.getContext().setParams(newParams);
        ReportDesignModel model = reportModelCacheManager.getReportModel(reportId);
        for (String key : contextParams.keySet()) {
            /**
             * 更新runtimeModel的全局上下文参数
             */
        		
            String[] value = contextParams.get(key);
            if (value != null && value.length > 0) {
                runTimeModel.getContext().put(getRealKey(model, key), value[0]);
            }
        }
        reportModelCacheManager.updateRunTimeModelToCache(reportId, runTimeModel);
        ResponseResult rs = ResourceUtils.getResult("Success Getting VM of Report",
                "Fail Getting VM of Report", "");
        logger.info("[INFO]current context params status {}", runTimeModel.getContext().getParams());
        logger.info("[INFO]successfully update global context, cost {} ms", (System.currentTimeMillis() - begin));
        return rs;
    }
    
    /**
     * 
     * @param model {@link ReportDesignModel}
     * @param key String
     * @return String real key
     */
    private String getRealKey(ReportDesignModel model, String key) {
    		if (model != null && model.getExtendById(key) != null) {
    			if (model.getExtendById(key).listAllItems().isEmpty()) {
    				return key;
    			} 
    			return model.getExtendById(key).listAllItems().keySet().toArray(new String[0])[0];
    		}
		return key;
	}

	private Map<String, Object> updateLocalContextAndReturn(ReportRuntimeModel runTimeModel,
            String areaId, Map<String, String[]> contextParams) {
        /**
         * 查询区域的时候，会按照当前的参数更新区域上下文
         */
        QueryContext localContext = runTimeModel.getLocalContextByAreaId(areaId);
        for (String key : contextParams.keySet()) {
            /**
             * 更新runtimeModel的区域上下文参数
             */
            String[] value = contextParams.get(key);
            if (value != null && value.length > 0) {
                localContext.put(key, value[0]);
            }
        }
        /**
         * 查询参数，首先载入全局上下文，再覆盖局部上下文
         */
        final Map<String, Object> queryParams = Maps.newHashMap();
        /**
         * TODO 暂时用全局的覆盖本地的参数，以后考虑是否会有问题
         */
        Map<String, Object> localParams = localContext.getParams();
        
        if ("true".equals(localParams.get("isOverride"))) {
        		queryParams.putAll(localParams);
        		
        		runTimeModel.getContext().getParams().forEach((key, value) -> {
        			if (!queryParams.containsKey(key)) {
        				queryParams.put(key, value);
        			}
        		});
        		return queryParams;
        }
        /**
         * 仅保留一个时间条件
         */
        for (String key : localParams.keySet()) {
	        	String value = localParams.get(key).toString();
	        	if (value.contains("start") && value.contains("end")) {
	        		localParams.remove(key);
	        	}
        }
        queryParams.putAll(localParams);
        if (runTimeModel.getContext() != null) {
            queryParams.putAll(runTimeModel.getContext().getParams());
        } else {
            throw new RuntimeException("没有初始化？？");
        }
        return queryParams;
    }
    
    /**
     * 
     * @param reportId
     * @param areaId
     * @param request
     * @return
     */
    @RequestMapping(value = "/{reportId}/runtime/extend_area/{areaId}", method = { RequestMethod.POST })
    public ResponseResult queryArea(@PathVariable("reportId") String reportId,
            @PathVariable("areaId") String areaId, HttpServletRequest request) {
    		long begin = System.currentTimeMillis();
    		logger.info("[INFO] begin query data");
        /**
         * 1. 获取缓存DesignModel对象
         */
        ReportDesignModel model;
        try {
            model = reportModelCacheManager.getReportModel(reportId);
        } catch (CacheOperationException e) {
            logger.info("[INFO]Report model is not in cache! ", e);
            ResponseResult rs = ResourceUtils.getErrorResult("缓存中不存在的报表，ID " + reportId, 1);
            return rs;
        }
        /**
         * 2. 获取区域对象
         */
        ExtendArea targetArea = model.getExtendById(areaId);
        if (targetArea == null) {
            throw new IllegalStateException("can't get report define");
        }
        /**
         * 3. 获取运行时对象
         */
        ReportRuntimeModel runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        /**
         * 4. 更新区域本地的上下文
         */
        ExtendAreaContext areaContext = getAreaContext(areaId, request, targetArea, runTimeModel);
        /**
         * 5. 生成查询动作QueryAction
         */
        QueryAction action = null;
        if (targetArea.getType() == ExtendAreaType.CHART 
                || targetArea.getType() == ExtendAreaType.LITEOLAP_CHART) {
            String[] indNames = new String[0];
            if (StringUtils.hasText(request.getParameter("indNames"))) {
                indNames = request.getParameter("indNames").split(",");
            }
            try {
            		String topSetting = request.getParameter(Constants.TOP);
            		if (!StringUtils.isEmpty(topSetting)) {
            			model.getExtendById(areaId).getLogicModel().
            				setTopSetting(GsonUtils.fromJson(topSetting, MeasureTopSetting.class));
            		}
                action = queryBuildService.generateChartQueryAction(model, areaId, 
                			areaContext.getParams(), indNames, runTimeModel);
                if (action != null) {
                    action.setChartQuery(true);
                }
            } catch (QueryModelBuildException e) {
                String msg = "没有配置时间维度，不能使用liteOlap趋势分析图！";
                logger.warn(msg);
                DIReportChart chart = new DIReportChart();
                return ResourceUtils.getCorrectResult(msg, chart);
            }
        } else {
            action = queryBuildService.generateTableQueryAction(model, areaId, areaContext.getParams());
            if (action != null) {
                action.setChartQuery(false);
            }
        }
        /**
         * 6. 完成查询
         */
        ResultSet result;
        try {
            if (action == null || CollectionUtils.isEmpty(action.getRows()) || CollectionUtils.isEmpty(action.getColumns())) {
                return ResourceUtils.getErrorResult("单次查询至少需要包含一个横轴、一个纵轴元素", 1);
            }
            result = reportModelQueryService.queryDatas(model, action, true, true, areaContext.getParams(), securityKey);
        } catch (DataSourceOperationException e1) {
            logger.info("获取数据源失败！", e1);
            return ResourceUtils.getErrorResult("获取数据源失败！", 1);
        } catch (QueryModelBuildException e1) {
            logger.info("构建问题模型失败！", e1);
            return ResourceUtils.getErrorResult("构建问题模型失败！", 1);
        } catch (MiniCubeQueryException e1) {
            logger.info("查询数据失败！", e1);
            return ResourceUtils.getErrorResult("没有查询到相关数据", 1);
        }
        PivotTable table = null;
        Map<String, Object> resultMap = Maps.newHashMap();
        try {
            table = queryBuildService.parseToPivotTable(result.getDataModel());
        } catch (PivotTableParseException e) {
            logger.info(e.getMessage(), e);
            return ResourceUtils.getErrorResult("Fail in parsing result. ", 1);
        }
		DataModelUtils.decorateTable(targetArea.getFormatModel(), table);
        if (targetArea.getType() == ExtendAreaType.TABLE || targetArea.getType() == ExtendAreaType.LITEOLAP_TABLE) {
            /**
             * 每次查询以后，清除选中行，设置新的
             */
            runTimeModel.getSelectedRowIds().clear();
            for (RowDefine rowDefine : table.getRowDefine()) {
                if (rowDefine.isSelected()) {
                    runTimeModel.getSelectedRowIds().add(rowDefine.getUniqueName());
                }
            }
            String[] dims = new String[0];
            if (table.getDataSourceColumnBased().size() == 0) {
            		ResponseResult rs = new ResponseResult();
            		rs.setStatus(1);
            		rs.setStatusInfo("未查到任何数据");
            		return rs;
            } else {
            		resultMap.put("pivottable", table);
            }
            resultMap.put("rowCheckMin", 1);
            resultMap.put("rowCheckMax", 5);
            resultMap.put("mainDimNodes", dims);
            resultMap.put("reportTemplateId", reportId);
            resultMap.put("totalSize", table.getDataRows());
            resultMap.put("currentSize", table.getDataSourceRowBased().size());
            List<Map<String, String>> mainDims = Lists.newArrayList();
            Map<String, String> root = areaContext.getCurBreadCrumPath();
            if (root == null) {
            		root = genRootDimCaption(table);
            		areaContext.setCurBreadCrumPath(root);
            }
            // 在运行时上下文保存当前区域的根节点名称 方便面包屑展示路径love
            if (!root.get("uniqName").toLowerCase().contains("all")) {
                String vertualDimKey = "[vertual_all]";
                root.put("uniqName", vertualDimKey);
                root.put("showName", "全部");
                runTimeModel.getContext().put(vertualDimKey, action);
            }
            mainDims.add(root);
            resultMap.put("mainDimNodes", mainDims);
//            runTimeModel.getContext().put(areaId, root);
            Collections.reverse(mainDims);
            areaContext.setCurBreadCrumPath(root);
        } else if (targetArea.getType() == ExtendAreaType.CHART 
                || targetArea.getType() == ExtendAreaType.LITEOLAP_CHART) {
            DIReportChart chart = null;
            Map<String, String> chartType = getChartTypeWithExtendArea(model, targetArea);
            if (action.getRows().size() == 1) {
                Item item = action.getRows().keySet().toArray(new Item[0])[0];
                OlapElement element = ReportDesignModelUtils.getDimOrIndDefineWithId(model.getSchema(),
                        targetArea.getCubeId(), item.getOlapElementId());
                if (element instanceof TimeDimension) {
                		chart = chartBuildService.parseToChart(table, chartType, true);
                } else {
                		chart = chartBuildService.parseToChart(table, chartType, false);
                }
            } else {
                chart = chartBuildService.parseToChart(table, chartType, false);
            }
            QueryUtils.decorateChart(chart, targetArea, model.getSchema());
            resultMap.put("reportChart", chart);
        }
        areaContext.getQueryStatus().add(result);
        reportModelCacheManager.updateAreaContext(targetArea.getId(), areaContext);
        runTimeModel.updateDatas(action, result);
        reportModelCacheManager.updateRunTimeModelToCache(reportId, runTimeModel);
        logger.info("[INFO] successfully query data operation. cost {} ms", (System.currentTimeMillis() - begin));
        ResponseResult rs = ResourceUtils.getResult("Success", "Fail", resultMap);
        return rs;
    }

	/**
	 * @param areaId
	 * @param request
	 * @param targetArea
	 * @param runTimeModel
	 * @return ExtendAreaContext
	 */
	private ExtendAreaContext getAreaContext(String areaId,
			HttpServletRequest request, ExtendArea targetArea,
			ReportRuntimeModel runTimeModel) {
		Map<String, Object> queryParams = updateLocalContextAndReturn(runTimeModel, areaId, request.getParameterMap());
        runTimeModel.getLocalContextByAreaId(areaId).getParams().putAll(queryParams);
        ExtendAreaContext areaContext = reportModelCacheManager.getAreaContext(targetArea.getId());
        areaContext.getParams().clear();
        areaContext.getParams().putAll(queryParams);
		return areaContext;
	}

    /**
     * 获取扩展区域中定义的chartType
     * @param targetArea ExtendArea
     * @return SeriesUnitType
     */
    private Map<String, String> getChartTypeWithExtendArea(ReportDesignModel model, ExtendArea targetArea) {
    		Map<String, String> chartTypes = Maps.newHashMap();
    		if (targetArea.getType() == ExtendAreaType.LITEOLAP_CHART) {
    			chartTypes.put("null", SeriesUnitType.LINE.name());
    			return chartTypes;
//    			return new String[]{SeriesUnitType.LINE.name()};
    		}
//    		List<String> types = Lists.newArrayList();
    		targetArea.listAllItems().values().stream().filter(item -> {
    			return item.getPositionType() == PositionType.Y 
    					|| item.getPositionType() == PositionType.CAND_IND;
    		}).forEach(item -> {
    			OlapElement element = ReportDesignModelUtils.getDimOrIndDefineWithId(model.getSchema(),
                        targetArea.getCubeId(), item.getOlapElementId());
    			Object chartType = item.getParams().get("chartType");
    			if (chartType == null) {
    				chartTypes.put(element.getUniqueName(), SeriesUnitType.COLUMN.name());
    			} else {
    				chartTypes.put(element.getUniqueName(), chartType.toString());
    			}
    		});
//    		.forEach(str -> {
//    			if (StringUtils.isEmpty(str)) {
//    				types.add(SeriesUnitType.COLUMN.name());
//    			} else {
//    				types.add(str.toString().toUpperCase());
//    			}
//    		});
		return chartTypes;
	}

	/**
	 * 
     * @param table
     * @return Map<String, String>
     * 
     */
    private Map<String, String> genRootDimCaption(PivotTable table) {
        RowHeadField rowHeadField = table.getRowHeadFields().get(0).get(0);
        String uniqueName = rowHeadField.getUniqueName();
        Map<String, String> root = Maps.newHashMap();
        String realUniqueName = uniqueName.replace("}", "").replace("{", "");
        root.put("uniqName", realUniqueName);
        root.put("showName", rowHeadField.getV());
        return root;
    }
    
    /**
     * 选中行
     * 
     * @param reportId
     *            报表id
     * @param areaId
     *            区域id
     * @param request
     *            请求对象
     * @return 操作结果
     */
    @RequestMapping(value = "/{reportId}/runtime/extend_area/{areaId}/selected_row/", method = { RequestMethod.POST })
    public ResponseResult selectRow(@PathVariable("reportId") String reportId,
            @PathVariable("areaId") String areaId, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        logger.info("[INFO] begin select row operation");
        String rowId = request.getParameter("rowId");
        if (!StringUtils.hasText(rowId)) {
            logger.info("[INFO]no rowid for input! ");
            return ResourceUtils.getErrorResult("no rowid for input! ", 1);
        }
        ReportRuntimeModel runTimeModel = null;
        try {
            runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        } catch (CacheOperationException e) {
            logger.info("no such runtime model found for id: " + reportId);
            return ResourceUtils.getErrorResult("no such runtime model found for id: " + reportId, 1);
        }
        runTimeModel.getSelectedRowIds().add(rowId);
        reportModelCacheManager.updateRunTimeModelToCache(reportId, runTimeModel);
        logger.info("[INFO]------successfully execute select row, cost {} ms", (System.currentTimeMillis() - begin));
        return ResourceUtils.getCorrectResult("Success adding selectedRow. ", "");
    }
    
    /**
     * 选中行
     * 
     * @param reportId
     *            报表id
     * @param areaId
     *            区域id
     * @param request
     *            请求对象
     * @return 操作结果
     */
    @RequestMapping(value = "/{reportId}/runtime/extend_area/{areaId}/selected_row/{rowId}", method = { RequestMethod.DELETE })
    public ResponseResult deselectRow(@PathVariable("reportId") String reportId,
            @PathVariable("areaId") String areaId, @PathVariable("rowId") String rowId, 
            HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        if (!StringUtils.hasText(rowId)) {
            logger.info("[INFO] --- ---no rowid for input! ");
            return ResourceUtils.getErrorResult("no rowid for input! ", 1);
        }
        ReportRuntimeModel runTimeModel = null;
        try {
            runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        } catch (CacheOperationException e) {
            logger.info("[INFO]--- ---no such runtime model found for id: " + reportId);
            return ResourceUtils.getErrorResult("no such runtime model found for id: " + reportId, 1);
        }
        runTimeModel.getSelectedRowIds().remove(rowId);
        logger.info("[INFO]successfully deslect row operation, cost {} ms", (System.currentTimeMillis() - begin));
        return ResourceUtils.getCorrectResult("Success removing selectedRow. ", "");
    }
    
    /**
     * 
     * 下钻操作
     * 
     * @param reportId 报表id
     * @param request 请求对象
     * @return 下钻操作 操作结果
     */
    @RequestMapping(value = "/{reportId}/runtime/extend_area/{areaId}/drill", method = { RequestMethod.POST })
    public ResponseResult drillDown(@PathVariable("reportId") String reportId, 
            @PathVariable("areaId") String areaId, HttpServletRequest request) {
    		long begin = System.currentTimeMillis();
    		logger.info("[INFO]------ begin drill down operation");
        String uniqueName = request.getParameter("uniqueName");
        ReportDesignModel model;
        try {
            model = reportModelCacheManager.getReportModel(reportId);
        } catch (CacheOperationException e) {
            logger.info("[INFO]------Can not find such model in cache. Report Id: " + reportId, e);
            return ResourceUtils.getErrorResult("不存在的报表，ID " + reportId, 1);
        }
        ReportRuntimeModel runTimeModel = null;
        try {
            runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        } catch (CacheOperationException e1) {
            logger.info("[INFO]------There are no such model in cache. Report Id: " + reportId, e1);
        }
        // TODO 这里调用需要考虑是否必须，按照正常逻辑，此处runtimeModel已经初始化完毕
        if (runTimeModel == null) {
            runTimeModel = reportModelCacheManager.loadRunTimeModelToCache(reportId);
        }
        ExtendArea targetArea = model.getExtendById(areaId);
        LogicModel targetLogicModel = null;
        String logicModelAreaId = areaId;
        if (targetArea.getType() == ExtendAreaType.CHART || targetArea.getType() == ExtendAreaType.LITEOLAP_CHART) {
            return ResourceUtils.getErrorResult("can not drill down a chart. ", 1); 
        } else if (targetArea.getType() == ExtendAreaType.LITEOLAP_TABLE) {
            LiteOlapExtendArea liteOlapArea = (LiteOlapExtendArea) model.getExtendById(targetArea.getReferenceAreaId());
            targetLogicModel = liteOlapArea.getLogicModel();
            logicModelAreaId = liteOlapArea.getId();
        } else {
            targetLogicModel = targetArea.getLogicModel();
        }
        
        if (targetLogicModel == null) {
            targetLogicModel = new LogicModel();
        }
        /**
         * 找到下载的维度节点
         */
        String[] uniqNames = com.baidu.rigel.biplatform.ac.util.
                DataModelUtils.parseNodeUniqueNameToNodeValueArray(uniqueName);
        if (uniqNames == null || uniqNames.length == 0) {
            String msg = String.format("Fail in drill down. UniqueName param is empty.");
            logger.error(msg);
            return ResourceUtils.getErrorResult(msg, 1);
        }
        QueryAction action = (QueryAction) runTimeModel.getContext().get(uniqueName);
        String drillTargetUniqueName = uniqNames[uniqNames.length - 1];
        logger.info("[INFO] drillTargetUniqueName : {}", drillTargetUniqueName);
        boolean isRoot = drillTargetUniqueName.toLowerCase().contains("all");
        if (action == null) {
            Map<String, String[]> oriQueryParams = Maps.newHashMap();
            
            String dimName = MetaNameUtil.getDimNameFromUniqueName(drillTargetUniqueName);
            Map<String, Item> store = runTimeModel.getUniversalItemStore().get(logicModelAreaId);
            if (CollectionUtils.isEmpty(store)) {
                String msg = "The item map of area (" + logicModelAreaId + ") is Empty!";
                logger.error(msg);
                throw new RuntimeException(msg);
            }
            Item row = store.get(dimName);
            oriQueryParams.putAll(request.getParameterMap());
            String[] drillName = new String[]{drillTargetUniqueName};
            oriQueryParams.put(row.getOlapElementId(), drillName);
            /**
             * update context
             */
            Map<String, Object> queryParams = updateLocalContextAndReturn(runTimeModel, areaId, oriQueryParams);
            action = queryBuildService.generateTableQueryAction(model, areaId, queryParams);
            runTimeModel.getContext().put(uniqueName, action);
            /**
             * 把下钻的值存下来
             * TODO 临时放在这里，需要重新考虑
             */
            if (row != null && queryParams.containsKey(row.getOlapElementId())) {
                action.getDrillDimValues().put(row,
                    queryParams.get(row.getOlapElementId()));
            }
        }
        ResultSet result;
        try {
            result = reportModelQueryService.queryDatas(model, action, true, true, securityKey);
        } catch (DataSourceOperationException e1) {
            logger.info("[INFO]--- ---can't get datasource！", e1);
            return ResourceUtils.getErrorResult("获取数据源失败！", 1);
        } catch (QueryModelBuildException e1) {
            logger.info("[INFO]--- ----can't not build question model！", e1);
            return ResourceUtils.getErrorResult("构建问题模型失败！", 1);
        } catch (MiniCubeQueryException e1) {
            logger.info("[INFO] --- --- can't query data ", e1);
            return ResourceUtils.getErrorResult("查询数据失败！", 1);
        }
        runTimeModel.drillDown(action, result);
        PivotTable table = null;
        Map<String, Object> resultMap = Maps.newHashMap();
        try {
            table = queryBuildService.parseToPivotTable(result.getDataModel());
        } catch (PivotTableParseException e) {
            logger.info(e.getMessage(), e);
            return ResourceUtils.getErrorResult("Fail in parsing result. ", 1);
        }
        ExtendAreaContext areaContext = reportModelCacheManager.getAreaContext(targetArea.getId());
        if (targetArea.getType() == ExtendAreaType.TABLE 
                || targetArea.getType() == ExtendAreaType.LITEOLAP_TABLE) {
            /**
             * TODO 考虑一下这样的逻辑是否应该放到resource中
             */
            List<Map<String, String>> mainDims = Lists.newArrayList();
            do {
                Map<String, String> dims3 = Maps.newHashMap();
                dims3.put("uniqName", drillTargetUniqueName);
                String showName = genShowName(drillTargetUniqueName);
                if (isRoot) {
                    showName = areaContext.getCurBreadCrumPath().get("showName");
                }
                dims3.put("showName", showName);
                mainDims.add(dims3);
                drillTargetUniqueName = MetaNameUtil.getParentUniqueName(drillTargetUniqueName);
            } while (drillTargetUniqueName != null && 
                    !drillTargetUniqueName.toLowerCase().contains("all"));
            if (!isRoot) {
                Map<String, String> root = areaContext.getCurBreadCrumPath();
                mainDims.add(root);
            }
            Collections.reverse(mainDims);
            resultMap.put("mainDimNodes", mainDims);
            runTimeModel.getContext().put("bread_key", mainDims);
        } 
        areaContext.getQueryStatus().add(result);
        reportModelCacheManager.updateAreaContext(targetArea.getId(), areaContext);
        reportModelCacheManager.updateRunTimeModelToCache(reportId, runTimeModel);
        DataModelUtils.decorateTable(targetArea.getFormatModel(), table);
        resultMap.put("pivottable", table);
        resultMap.put("rowCheckMin", 1);
        resultMap.put("rowCheckMax", 5);
        resultMap.put("reportTemplateId", reportId);
        resultMap.put("totalSize", table.getActualSize());
        resultMap.put("currentSize", table.getDataSourceRowBased().size());
        
        ResponseResult rs = ResourceUtils.getResult("Success Getting VM of Report",
                "Fail Getting VM of Report", resultMap);
        logger.info("[INFO]Successfully execute drill operation. cost {} ms", (System.currentTimeMillis() - begin));
        return rs;
    }

    /**
     * 
     * @param drillTargetUniqueName
     * @return
     * 
     */
    private String genShowName(String drillTargetUniqueName) {
        String showName = drillTargetUniqueName.substring(drillTargetUniqueName.lastIndexOf("[") + 1, 
                drillTargetUniqueName.length() - 1);
        if (showName.contains("All_")) {
            showName = showName.replace("All_", "全部");
            showName = showName.substring(0, showName.length() - 1);
        }
        return showName;
    }
    
    /**
     * 展开下钻操作
     * 
     * @param reportId
     *            报表id
     * @param request
     *            请求对象
     * @param rowIndex 当前点击行行号
     * @param colIndex 列索引
     * @return 下钻操作 操作结果
     */
    @RequestMapping(value = "/{reportId}/runtime/extend_area/{areaId}/drill/{type}", method = { RequestMethod.POST })
    public ResponseResult drillDown(@PathVariable("reportId") String reportId,
            @PathVariable("areaId") String areaId,  @PathVariable("type") String type,
            HttpServletRequest request) throws Exception {
        long begin = System.currentTimeMillis();
        logger.info("begin drill down opeartion");
//        // 解析查询条件条件 来自于rowDefine
        String condition = request.getParameter("lineUniqueName");
        
//        String uniqueName = request.getParameter("uniqueName");
        ReportDesignModel model;
        try {
            model = reportModelCacheManager.getReportModel(reportId);
        } catch (CacheOperationException e) {
            logger.info("[INFO] Can not find such model in cache. Report Id: " + reportId, e);
            return ResourceUtils.getErrorResult("不存在的报表，ID " + reportId, 1);
        }
        ReportRuntimeModel runTimeModel = null;
        try {
            runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        } catch (CacheOperationException e1) {
            logger.info("[INFO] There are no such model in cache. Report Id: " + reportId, e1);
        }
        // TODO 这里调用需要考虑是否必须，按照正常逻辑，此处runtimeModel已经初始化完毕
        if (runTimeModel == null) {
            runTimeModel = reportModelCacheManager.loadRunTimeModelToCache(reportId);
        }
        ExtendArea targetArea = model.getExtendById(areaId);
        /**
         * TODO 合并当前的全局上下文，需要重构下，整理上下文处理逻辑
         */
//        QueryAction previousAction = runTimeModel.getPreviousQueryAction(areaId);
        ExtendAreaContext areaContext = reportModelCacheManager.getAreaContext(targetArea.getId());
        
        ResultSet previousResult = areaContext.getQueryStatus().getLast();
        LogicModel targetLogicModel = null;
        String logicModelAreaId = areaId;
        if (targetArea.getType() == ExtendAreaType.CHART || targetArea.getType() == ExtendAreaType.LITEOLAP_CHART) {
            return ResourceUtils.getErrorResult("can not drill down a chart. ", 1); 
        } else if (targetArea.getType() == ExtendAreaType.LITEOLAP_TABLE) {
            LiteOlapExtendArea liteOlapArea = (LiteOlapExtendArea) model.getExtendById(targetArea.getReferenceAreaId());
            targetLogicModel = liteOlapArea.getLogicModel();
            logicModelAreaId = liteOlapArea.getId();
        } else {
            targetLogicModel = targetArea.getLogicModel();
        }
        
        if (targetLogicModel == null) {
            targetLogicModel = new LogicModel();
        }
        /**
         * update context
         */
        Map<String, Object> queryParams = updateLocalContextAndReturn(runTimeModel, areaId, request.getParameterMap());
        /**
         * 找到下载的维度节点
         */
        String[] uniqNames = com.baidu.rigel.biplatform.ac.util.
                DataModelUtils.parseNodeUniqueNameToNodeValueArray(condition);
        if (uniqNames == null || uniqNames.length == 0) {
            String msg = String.format("Fail in drill down. UniqueName param is empty.");
            logger.error(msg);
            return ResourceUtils.getErrorResult(msg, 1);
        }
        int targetIndex = uniqNames.length - 1;
        String drillTargetUniqueName = uniqNames[targetIndex];
        String dimName = MetaNameUtil.getDimNameFromUniqueName(drillTargetUniqueName);
        Map<String, Item> store = runTimeModel.getUniversalItemStore().get(logicModelAreaId);
        if (CollectionUtils.isEmpty(store)) {
            String msg = "The item map of area (" + logicModelAreaId + ") is Empty!";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
        /** 
         * 把本行前面的维度都放到过滤中，作为过滤条件
         */
        for (int i = 0; i < targetIndex; i++) {
            String rowAheadUniqueName = uniqNames[i];
            String rowAheadDimName = MetaNameUtil.getDimNameFromUniqueName(rowAheadUniqueName);
            Item rowAhead = store.get(rowAheadDimName);
            queryParams.put(rowAhead.getOlapElementId(), rowAheadUniqueName);
        }
        Item row = store.get(dimName);
        queryParams.put(row.getOlapElementId(), drillTargetUniqueName);
        QueryAction action = queryBuildService.generateTableQueryActionForDrill(model,
                areaId, queryParams, targetIndex);
        
        ResultSet result;
        try {
            result = reportModelQueryService.queryDatas(model, action, true, false, securityKey);
        } catch (DataSourceOperationException | QueryModelBuildException | MiniCubeQueryException e1) {
            logger.error(e1.getMessage(), e1);
            return ResourceUtils.getErrorResult(e1.getMessage(), 1);
        } 
        PivotTable table = null;
        Map<String, Object> resultMap = Maps.newHashMap();
//        ResultSet previousResult = runTimeModel.getPreviousQueryResult();
        int rowNum = this.getRowNum(previousResult, condition);
        try {
            // 查询下钻的数据
            if (type.equals("expand")) {
//                ResultSet result = reportModelQueryService.queryDatas(model, action, true);
                DataModel newDataModel = DataModelUtils.merageDataModel(previousResult.getDataModel(), 
                        result.getDataModel(), rowNum);
                table = DataModelUtils.transDataModel2PivotTable(newDataModel, false, 0, false);
                result.setDataModel(newDataModel);
                /**
                 * TODO 这里重新生成当前条件对应的action，而不是下钻使用的action，为的是记录下当前表的结果
                 * 
                 */
                QueryContext previousContext = runTimeModel.getLocalContextByAreaId(areaId);
                QueryAction recordAction = queryBuildService.generateTableQueryAction(model,
                        areaId, previousContext.getParams());
                runTimeModel.updateDatas(recordAction, result);
            } else { //上卷或者折叠操作
                DataModel newModel = DataModelUtils.removeDataFromDataModel(previousResult.getDataModel(), rowNum);
                table = DataModelUtils.transDataModel2PivotTable(newModel, false, 0, false);
                result = new ResultSet();
                result.setDataModel(newModel);
                /**
                 * TODO 这里重新生成当前条件对应的action，而不是下钻使用的action，为的是记录下当前表的结果
                 * 
                 */
                QueryContext previousContext = runTimeModel.getLocalContextByAreaId(areaId);
                QueryAction recordAction = queryBuildService.generateTableQueryAction(model,
                        areaId, previousContext.getParams());
                runTimeModel.updateDatas(recordAction, result);
            }
            areaContext.getQueryStatus().add(result);
            reportModelCacheManager.updateAreaContext(targetArea.getId(), areaContext);
            reportModelCacheManager.updateRunTimeModelToCache(reportId, runTimeModel);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        
        if (targetArea.getType() == ExtendAreaType.TABLE || targetArea.getType() == ExtendAreaType.LITEOLAP_TABLE) {
        		// TODO 临时解决方案，此处应将查询条件设置到QuestionModel中
        		DataModelUtils.decorateTable(targetArea.getFormatModel(), table);
            resultMap.put("pivottable", table);
            resultMap.put("rowCheckMin", 1);
            resultMap.put("rowCheckMax", 5);
            resultMap.put("mainDimNodes", runTimeModel.getContext().get("bread_key"));
            resultMap.put("reportTemplateId", reportId);
            resultMap.put("totalSize", table.getActualSize());
            resultMap.put("currentSize", table.getDataSourceRowBased().size());
        } 
        ResponseResult rs = ResourceUtils.getResult("Success Getting VM of Report",
                "Fail Getting VM of Report", resultMap);
        logger.info("[INFO]Successfully execute drill down operation, cost {} ms",
        		(System.currentTimeMillis() - begin));
        return rs;
    }
    
    /**
     * 
     * @param previousResult
     * @param uniqueName
     * @return
     */
    private int getRowNum(ResultSet previousResult, String uniqueName) {
        List<HeadField> headField = previousResult.getDataModel().getRowHeadFields();
        List<HeadField> tmp = com.baidu.rigel.biplatform.ac.util.DataModelUtils.getLeafNodeList(headField);
        for (int rowNum = 0; rowNum < tmp.size(); ++rowNum) {
            /**
             * TODO 需要回复
             */
            String lineUniqueName = tmp.get(rowNum).getNodeUniqueName();
            if (lineUniqueName.equals(uniqueName)) {
                return rowNum;
            }
        }
        throw new IllegalStateException("can not found rowNum and colNum");
    }

    /**
     * 上卷操作
     * 
     * @return 上卷操作 操作结果
     */
    public ResponseResult roolUp() {
        return null;
    }
    
    /**
     * 获取维度成员
     * @return
     */
    @RequestMapping(value = "/runtime/extend_area/{areaId}/dims/{dimId}/members",
            method = { RequestMethod.POST })
    public ResponseResult queryMembers(@PathVariable("areaId") String areaId, 
        @PathVariable("dimId") String dimId, HttpServletRequest request) throws Exception {
        long begin = System.currentTimeMillis();
        logger.info("[INFO] begin query member operation");
        String reportId = request.getParameter("reportId");
        if (StringUtils.isEmpty(reportId)) {
            ResponseResult rs = new ResponseResult();
            rs.setStatus(1);
            rs.setStatusInfo("reportId为空，请检查输入");
            return rs;
        }
        ReportDesignModel model = null;
        try {
            model = reportModelCacheManager.getReportModel(reportId);
        } catch (CacheOperationException e) {
            logger.error(e.getMessage(), e);
            ResponseResult rs = ResourceUtils.getErrorResult("不存在的报表，ID " + reportId, 1);
            return rs;
        }
        ReportRuntimeModel runTimeModel = null;
        try {
            runTimeModel = reportModelCacheManager.getRuntimeModel(reportId);
        } catch (CacheOperationException e) {
            logger.error(e.getMessage(), e);
            ResponseResult rs = ResourceUtils.getErrorResult("不存在的报表，ID " + reportId, 1);
            return rs;
        }
        if (model == null) {
            ResponseResult rs = ResourceUtils.getErrorResult("不存在的报表，ID " + reportId, 1);
            return rs;
        }
        
        ExtendArea area = model.getExtendById(areaId);
        String cubeId = area.getCubeId();
        Cube cube = model.getSchema().getCubes().get(cubeId);
        Dimension dim = cube.getDimensions().get(dimId);
        /**
         * 通过全局的上下文作为members的参数
         */
        QueryContext queryContext = runTimeModel.getContext();
        Map<String, String> params = Maps.newHashMap();
        for (String key : queryContext.getParams().keySet()) {
            Object value = queryContext.get(key);
            if (value != null) {
                params.put(key, value.toString());
            }
        }
        cube = QueryUtils.getCubeWithExtendArea(model, area);
        ((MiniCube) cube).setSchema(model.getSchema());
        final Dimension newDim = QueryUtils.convertDim2Dim(dim);
        if (params.containsKey(Constants.ORG_NAME) || params.containsKey(Constants.APP_NAME)) {
            ResponseResult rs = new ResponseResult();
            rs.setStatus(0);
            rs.setStatusInfo("OK");
            return rs;
        }
        List<List<Member>> members = reportModelQueryService.getMembers(cube, newDim, params);
        QueryContext context = runTimeModel.getLocalContextByAreaId(area.getId());
        List<DimensionMemberViewObject> datas = Lists.newArrayList();
        final AtomicInteger i = new AtomicInteger(1);
        members.forEach(tmpMembers ->{
            DimensionMemberViewObject viewObject = new DimensionMemberViewObject();
            String caption = "第" + i.getAndAdd(1) + "级";
            viewObject.setCaption(caption);
            String name = "[" + newDim.getName() + "]";
            name += ".[All_" + newDim.getName() + "s]";
            viewObject.setName(name);
            viewObject.setNeedLimit(false);
            viewObject.setSelected(i.get() == 2);
            viewObject.setChildren(genChildren(newDim, tmpMembers, context));
            datas.add(viewObject);
        });
        ResponseResult rs = new ResponseResult();
        rs.setStatus(0);
        rs.setStatusInfo("successfully");
        Map<String, List<DimensionMemberViewObject>> dimValue = Maps.newHashMap();
        dimValue.put("dimValue", datas);
        rs.setData(dimValue);
        logger.info("[INFO] query member operation successfull, cost {} ms", (System.currentTimeMillis() - begin));
        return rs;
    }
    
    /**
     * 按照指标指定排序方式显示数据
     * @return ResponseResult
     */
    @RequestMapping(value = "/{reportId}/runtime/extend_area/{areaId}/sort", method = { RequestMethod.POST , RequestMethod.GET})
    public ResponseResult sortByMeasure(@PathVariable("reportId")String reportId,
    		    @PathVariable("areaId")String areaId,
    		    HttpServletRequest request, HttpServletResponse response) throws Exception {
    		long begin = System.currentTimeMillis();
    		logger.info("begin execuet sort by measure");
    		String uniqueName = request.getParameter("uniqueName");
    		String sort = request.getParameter("sortType");
    		if (StringUtils.isEmpty(sort)) {
    			sort = "NONE";
    		}
    		if (sort.equalsIgnoreCase("NONE") || sort.equalsIgnoreCase("ASC")) {
    			sort = "DESC";
    		} else if (sort.equalsIgnoreCase("DESC")) {
    			sort = "ASC";
    		} 
    		ReportDesignModel reportModel = reportModelCacheManager.getReportModel(reportId);
    		SortRecord.SortType sortType = SortRecord.SortType.valueOf(sort.toUpperCase());
    		ExtendAreaContext context = this.reportModelCacheManager.getAreaContext(areaId);
    		DataModel model = DeepcopyUtils.deepCopy(context.getQueryStatus().getLast().getDataModel());
    		SortRecord type = new SortRecord(sortType, uniqueName, 500);
    		com.baidu.rigel.biplatform.ac.util.DataModelUtils.sortDataModelBySort(model, type);
    		ResultSet rs = new ResultSet();
    		model.getColumnHeadFields().forEach(headField -> {
    			if (headField.getValue().equals(uniqueName)) {
    				headField.getExtInfos().put("sortType", sortType.name());
    			} else {
    				headField.getExtInfos().put("sortType", "NONE");
    			}
    		});
    		rs.setDataModel(model);
    		context.getQueryStatus().add(rs);
    		PivotTable table = null;
        Map<String, Object> resultMap = Maps.newHashMap();
        try {
            table = queryBuildService.parseToPivotTable(model);
        } catch (PivotTableParseException e) {
            logger.error(e.getMessage(), e);
            return ResourceUtils.getErrorResult("Fail in parsing result. ", 1);
        }
        DataModelUtils.decorateTable(reportModel.getExtendById(areaId).getFormatModel(), table);
        if (table.getDataSourceColumnBased().size() == 0) {
        		ResponseResult tmp = new ResponseResult();
        		tmp.setStatus(1);
        		tmp.setStatusInfo("未查到任何数据");
        		return tmp;
        } else {
        		resultMap.put("pivottable", table);
        }
        resultMap.put("rowCheckMin", 1);
        resultMap.put("rowCheckMax", 5);
        resultMap.put("reportTemplateId", reportId);
        resultMap.put("totalSize", table.getDataRows());
        resultMap.put("currentSize", table.getDataSourceRowBased().size());
        context.getQueryStatus().add(rs);
        reportModelCacheManager.updateAreaContext(areaId, context);
        logger.info("[INFO]successfully execute sort by measure. cost {} ms", (System.currentTimeMillis() - begin));
        return ResourceUtils.getResult("Success", "Fail", resultMap);
    }
    
    /**
     * 
     * @param tmpMembers
     * @param context 
     * @return List<DimensionMemberViewObject>
     */
    private List<DimensionMemberViewObject> genChildren(Dimension dim, List<Member> tmpMembers, QueryContext context) {
        final List<DimensionMemberViewObject> rs = Lists.newArrayList();
        DimensionMemberViewObject all = new DimensionMemberViewObject();
        Map<String, Object> params = context.getParams();
        Set<String> tmpKey = Sets.newHashSet();
        params.values().forEach(strArray -> {
            if (strArray instanceof String[]) {
                String[] tmpArray = (String[]) strArray;
                for (String tmpStr : tmpArray) {
                    tmpKey.add(tmpStr);
                }
            }
        });
        all.setCaption("全部");
        all.setNeedLimit(false);
        String name = "[" + dim.getName() + "]";
        name += ".[All_" + dim.getName() + "]";
        all.setName(name);
        all.setSelected(tmpKey.contains(name));
        rs.add(all);
        tmpMembers.forEach(m ->{
            DimensionMemberViewObject child = new DimensionMemberViewObject();
            child.setCaption(m.getCaption());
            child.setSelected(tmpKey.contains(m.getUniqueName()));
            child.setNeedLimit(false);
            child.setName(m.getUniqueName());
            rs.add(child);
        });
        return rs;
    }

    /**
     * 更新维度成员
     * @return ResponseResult
     */
    @RequestMapping(value = "/runtime/extend_area/{areaId}/dims/{dimId}/members/1",
            method = { RequestMethod.POST })
    public ResponseResult updateMembers(@PathVariable("areaId") String areaId, 
            @PathVariable("dimId") String dimId, HttpServletRequest request) throws Exception {
        String reportId = request.getParameter("reportId");
        if (StringUtils.isEmpty(reportId)) {
            ResponseResult rs = new ResponseResult();
            rs.setStatus(1);
            rs.setStatusInfo("reportId为空，请检查输入");
            return rs;
        }
        ReportRuntimeModel model = null;
        try {
            model = reportModelCacheManager.getRuntimeModel(reportId);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            ResponseResult rs = ResourceUtils.getErrorResult("不存在的报表，ID " + reportId, 1);
            return rs;
        }
        ReportDesignModel designModel = reportModelCacheManager.getReportModel(reportId);
        ExtendArea area = designModel.getExtendById(areaId);
        
        String[] selectedDims = request.getParameterValues("selectedNodes");
        updateLocalContext(dimId, model, selectedDims, areaId);
        if (area.getType() == ExtendAreaType.SELECTION_AREA) {
            areaId = area.getReferenceAreaId();
            LiteOlapExtendArea liteOlapArea =  (LiteOlapExtendArea) designModel.getExtendById(areaId);
            String chartAreaId = liteOlapArea.getChartAreaId();
            String tableAreaId = liteOlapArea.getTableAreaId();
            updateLocalContext(dimId, model, selectedDims, chartAreaId);
            updateLocalContext(dimId, model, selectedDims, tableAreaId);
        }
        this.reportModelCacheManager.updateRunTimeModelToCache(reportId, model);
        ResponseResult rs = ResourceUtils.getCorrectResult("successfully", null);
        return rs;
    }

    /**
     * updateLocalContext
     * @param dimId
     * @param model
     * @param selectedDims
     * @param chartAreaId
     * 
     */
    private void updateLocalContext(String dimId, ReportRuntimeModel model, String[] selectedDims,
            String areaId) {
        QueryContext localContext = model.getLocalContextByAreaId(areaId);
        localContext.getParams().put(dimId, selectedDims);
        ExtendAreaContext context = this.reportModelCacheManager.getAreaContext(areaId);
        context.getParams().put(dimId, selectedDims);
        reportModelCacheManager.updateAreaContext(areaId, context);
    }
    
    /**
     * 下载请求
     * @return
     */
    @RequestMapping(value = "/{reportId}/download/{areaId}", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseResult download(@PathVariable("reportId") String reportId, @PathVariable("areaId")String areaId,
    		HttpServletRequest request, HttpServletResponse response) throws Exception {
    		long begin = System.currentTimeMillis();
    		ReportDesignModel report  = reportModelCacheManager.getReportModel(reportId);
    		if (report == null) {
    			throw new IllegalStateException("未知报表定义，请确认下载信息");
    		}
    		ExtendArea targetArea = report.getExtendById(areaId);
    		ReportRuntimeModel model = reportModelCacheManager.getRuntimeModel(reportId);
    		
    		ExtendAreaContext areaContext = this.getAreaContext(areaId, request, targetArea, model);
    		QueryAction action = queryBuildService.generateTableQueryAction(report, areaId, areaContext.getParams());
        if (action != null) {
            action.setChartQuery(false);
        }
        ResultSet queryRs = reportModelQueryService.queryDatas(report, action, true, true, areaContext.getParams(), securityKey);
    		DataModel dataModel = queryRs.getDataModel();
    		logger.info("[INFO]query data cost : " + (System.currentTimeMillis() - begin) + " ms");
    		begin = System.currentTimeMillis();
    		String csvString = DataModelUtils.convertDataModel2CsvString(dataModel);
    		logger.info("[INFO]convert data cost : " + (System.currentTimeMillis() - begin) + " ms" );
    		response.setCharacterEncoding("utf-8");
    		response.setContentType("application/vnd.ms-excel;charset=utf-8");
    		response.setContentType("application/x-msdownload;charset=utf-8");
    		response.setHeader("Content-Disposition", "attachment;filename=" + report.getName() + ".csv"); 
    		byte[] content = csvString.getBytes("GBK");
    		response.setContentLength(content.length);
    		OutputStream os = response.getOutputStream();
    		os.write(content);
    		os.flush();
    		ResponseResult rs = new ResponseResult();
    		rs.setStatus(ResponseResult.SUCCESS);
    		rs.setStatusInfo("successfully");
    		return rs;
    }
    
}