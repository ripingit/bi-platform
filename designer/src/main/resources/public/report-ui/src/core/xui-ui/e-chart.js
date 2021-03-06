/**
 * xui.ui.HChart
 * Copyright 2012 Baidu Inc. All rights reserved.
 *
 * @file:    基于highcharts的js图
 *           (最早源自pl-charts.js by cxl(chenxinle))
 * @author:  sushuang(sushuang@baidu.com)
 * @depend:  xui, xutil, echarts
 */

(function () {
    var addClass = xutil.dom.addClass;
    var removeClass = xutil.dom.removeClass;
    var q = xutil.dom.q;
    var domChildren = xutil.dom.children;
    var getPreviousSibling = xutil.dom.getPreviousSibling;
    var inheritsObject = xutil.object.inheritsObject;
    var formatNumber = xutil.number.formatNumber;
//    var extend = xutil.object.extend;
    var XOBJECT = xui.XObject;
//    var DI_ATTR_PREFIX = '\x06diA^_^';
    /**
     * 基于e-chart的JS图
     *
     * @class
     * @extends {xui.ui.Control}
     */
    var UI_E_CHART = xui.ui.EChart =
        inheritsObject(
            XOBJECT,
            function (options) {
                var el = this.el = options.el;
                this._sType = 'xui-e-chart';
                addClass(el, this._sType);
                var type = this._sType;
                // FIXME:优化，header估计得干掉
                el.innerHTML = [
                    '<div class="' + type + '-header">',
                    '</div>',
                    '<div class="' + type + '-content"></div>'
                ].join('');
                this._eHeader = el.childNodes[0];
                this._eContent = el.childNodes[1];
            }
        );
    var UI_E_CHART_CLASS = UI_E_CHART.prototype;

    /**
     * 初始化
     */
    UI_E_CHART_CLASS.init = function () {
    };

    /**
     * 设置数据
     *
     * @public
     * @param {Object} dataWrap 数据
     * @param {boolean=} isSilent 是否静默（不渲染），缺省则为false
     */
    UI_E_CHART_CLASS.setData = function (dataWrap, isSilent) {
        this._zoomSelectedButton = 0;
        dataWrap = dataWrap || {};
        this._bSeriesHasValue = null;
        this._nWidth = dataWrap.width;
        this._nHeight = dataWrap.height;
        /**
         * x轴定义
         * 例如：
         *  xAxis: [
         *      {
         *          type: 'quarter', // 或'category', 'date', 'month'等，参见EXT_AXIS_FORMAT
         *          data: ['2012-Q1', '2012-Q2']
         *      }
         *  ];
         */
        this._aXAxis = dataWrap.xAxis || [];
        this._zoomStart = 0;
        this._zoomEnd = this._aXAxis.data
            ? this._aXAxis.data.length - 1
            : 0;
        /**
         * y轴定义
         * 例如：
         *  xAxis: [
         *      {
         *          format: 'I,III.DD%', // 显示格式
         *          title: '我是y轴上的描述文字'
         *      }
         *  ];
         */
        this._aYAxis = dataWrap.yAxis || [];
        /**
         * 系列数据
         * 例如：
         *  series: [
         *      {
         *          name: '我是系列1',
         *          data: [1234.1234, 12344.333, 57655]
         *      },
         *      {
         *          name: '我是系列2',
         *          data: [566.1234, 565, 9987]
         *      }
         *  ];
         */
        this._aSeries = dataWrap.series || [];
        /**
         * 用户自定义rangeselector的按钮
         * 例如：
         *  rangeSelector: {
         *      byAxisType: {
         *          date: {
         *              buttons: [
         *                  { type: 'thisMonth', text: '本月', by: 'max' },
         *                  { type: 'all', text: '全部' }
         *              ],
         *              selected: 0
         *          }
         *      }
         *  }
         */
        this._oRangeSelector = dataWrap.rangeSelector;
        /**
         * 用户自定义legend的模式（外观+行为）
         * 例如：
         *  legend: {
         *      xMode: 'pl' // PL模式的legend。缺省则使用默认模式。
         *  }
         */
        this._oLegend = dataWrap.legend || {};
        /**
         * 数据为空时的html
         */
        this._sEmptyHTML = dataWrap.emptyHTML || '数据为空';

        this._allMeasures = dataWrap.allMeasures;
        this._defaultMeasures = dataWrap.defaultMeasures;
        this._allDims = dataWrap.allDims;
        this._defaultDims = dataWrap.defaultDims;
        this._mapMinValue = dataWrap.mapMinValue;
        this._mapMaxValue = dataWrap.mapMaxValue;
        !isSilent && this.render();
    };

    //------------------------------------------
    // 图形备选区域模块
    //------------------------------------------

    /**
     * 生成指标切换按钮
     *
     * @protected
     */
    UI_E_CHART_CLASS.$renderCheckBoxs = function () {
        var me = this;
        var allMeasures = me._allMeasures;
        var defaultMeasures = me._defaultMeasures;
        var measureHtml = [];

        // 渲染图形中备选区模块
        if (allMeasures.length > 0) {
            if (this._chartType === 'pie') {
                // 多选
                for (var i = 0; i < allMeasures.length; i ++) {
                    measureHtml.push(
                        '<label>',
                        allMeasures[i],
                        '</label>',
                        '<input type="radio" name="echarts-candidate" ',
                        isInArray(allMeasures[i], defaultMeasures) ? 'checked="checked" ' : '',
                        '/>'
                    );
                }
                this._eHeader.innerHTML = '<div class="echarts-candidate" id="echarts-candidate">'
                    + measureHtml.join('')
                    + '</div>';
                this._eCandidateBox = domChildren(this._eHeader)[0];
                this._eCandidateBox.onclick = function (ev) {
                    candidateClick.call(me, ev || window.event);
                };
            }
            else {
                // 多选
                for (var i = 0; i < allMeasures.length; i ++) {
                    measureHtml.push(
                        '<label>',
                        allMeasures[i],
                        '</label>',
                        '<input type="checkbox" name="echarts-candidate" ',
                        isInArray(allMeasures[i], defaultMeasures) ? 'checked="checked" ' : '',
                        '/>'
                    );
                }
                this._eHeader.innerHTML = '<div class="echarts-candidate" id="echarts-candidate">'
                    + measureHtml.join('')
                    + '</div>';
                // 绑定备选区按钮事件
                this._eCandidateBox = domChildren(this._eHeader)[0];
                this._eCandidateBox.onclick = function (ev) {
                    candidateClick.call(me, ev || window.event);
                };
            }




        }
    };
    // 备选区按钮点击事件
    function candidateClick(ev) {
        var resultName = '';
        var oTarget = ev.target;

        if (oTarget.tagName.toLowerCase() === 'input') {
            resultName = getPreviousSibling(oTarget).innerHTML;
            if (oTarget.type === 'radio') {
                this._defaultMeasures = [resultName];
            }
            else {
                // oTarget.checked = oTarget.checked ? false : true;
                this._defaultMeasures = getCurrentCandidate(resultName, this._defaultMeasures);
            }

            //this.$disposeHeader();
            this.$disposeChart();
            this.$createChart(this.$initOptions());
        }
    }
    // 在数组中是否存在
    function isInArray(item, array) {
        var flag = false;
        for (var i = 0; i < array.length; i ++) {
            if (item === array[i]) {
                flag = true;
            }
        }
        return flag;
    }
    // 获取备选区中当前显示的内容
    function getCurrentCandidate(name, currentSelects) {
        var isHave = false;
        var result = [];

        for (var i = 0; i < currentSelects.length; i ++) {
            if (currentSelects[i] === name) {
                isHave = true;
            }
            else {
                result.push(currentSelects[i]);
            }
        }
        // 如果本身就没有name元素，就添加进去
        if (!isHave) {
            result.push(name);
        }
        return result;
    }

    /**
     * 设置数据
     *
     * @protected
     */
    UI_E_CHART_CLASS.$setupSeries = function (options) {
        var series = [];
        var seryKind = {};
        var tempData = [];
        var xAxis = this._aXAxis;
        var defaultMeasures = this._defaultMeasures;

        for (var i = 0, ser, serDef; serDef = this._aSeries[i]; i ++) {
            seryKind[serDef.type] = seryKind[serDef.type]
                ? seryKind[serDef.type] + 1
                : 1;
            ser = { data: [] };
            ser.name = serDef.name || '';
            ser.yAxisIndex = serDef.yAxisIndex || 0;
            ser.color = serDef.color || void 0;
            ser.format = serDef.format || void 0;
            ser.type = (serDef.type === 'column' ? 'bar' : serDef.type);
            (serDef.id !== null) && (ser.id = serDef.id);
            // TODO:这个data需要后端注意一下数据格式
            ser.data = serDef.data;
            if (defaultMeasures) {
                if (ser.type === 'bar') {
                    if (isInArray(ser.name, defaultMeasures)) {
                        ser.yAxisIndex = 0;
                        series.push(ser);
                    }
                }
                else if (ser.type === 'column') {
                    if (isInArray(ser.name, defaultMeasures)) {
                        ser.type = 'bar';
                        series.push(ser);
                    }
                }
                else if (ser.type === 'pie') {
                    if (isInArray(ser.name, defaultMeasures)) {
                        series.push(ser);
                    }
                }
                else if (ser.type === 'line') {
                    ser.symbol = 'none'; // 线图上的点的形状
                    if (isInArray(ser.name, defaultMeasures)) {
                        tempData.push(ser);
                    }
                }
                else if (ser.type === 'map') {
                    ser.mapType = 'china';
                    ser.roam = false;
                    ser.itemStyle = {
                        normal:{ label:{ show:true } },
                        emphasis:{ label:{ show:true } }
                    };
                    var serData = [];
                    for (var x = 0; x < ser.data.length; x ++) {
                        serData.push({
                            name: xAxis.data[x],
                            value: ser.data[x]
                        });
                    }
                    ser.data = serData;
                    if (isInArray(ser.name, defaultMeasures)) {
                        series.push(ser);
                    }
                }
            }
            else {
                if (ser.type === 'bar') {
                    ser.yAxisIndex = 0;
                    series.push(ser);
                }
                else if (ser.type === 'column') {
                    ser.type = 'bar';
                    series.push(ser);
                }
                else if (ser.type === 'pie') {
                    series.push(ser);
                }
                else if (ser.type === 'line') {
                    tempData.push(ser);
                }
                else if (ser.type === 'map') {
                    ser.mapType = 'china';
                    ser.roam = false;
                    ser.itemStyle = {
                        normal:{ label:{ show:true } },
                        emphasis:{ label:{ show:true } }
                    };
                    var serData = [];
                    for (var x = 0; x < ser.data.length; x ++) {
                        serData.push({
                            name: xAxis.data[x],
                            value: ser.data[x]
                        });
                    }
                    ser.data = serData;
                    series.push(ser);
                }
            }

        }
        series = series.concat(tempData);
        if (seryKind.line >= 1 && seryKind.bar >= 1) {
            this._isAddYxis = true;
        }
        // series中只允许有一个饼图。
        if (this._chartType === 'pie') {
            var targetSeries = [{}];
            for(var key in series[0]) {
                series[0].hasOwnProperty(key) && (targetSeries[0][key] = series[0][key]);
            }
            targetSeries[0].data = [];
            for (var k = 0; k < series[0].data.length; k ++) {
                var  kser = series[0].data[k];
                var tarData = {
                    value: kser,
                    name: xAxis.data[k]
                };
                targetSeries[0].data.push(tarData);
            }
            series = targetSeries;
        }
        options.series = series;
    };
    /**
     * 设置x轴
     *
     * @private
     */
    UI_E_CHART_CLASS.$setupXAxis = function (options) {
        var xAxis =  {
            type: 'category',
            boundaryGap: true,
            axisLine: {
                onZero: false
            },
            data: this._aXAxis.data
        };
        // 如果是正常图形（柱形图与线图），那么x轴在下面显示
        if (this._chartType === 'column' || this._chartType === 'line') {
            options.xAxis = xAxis;
        }
        else if (this._chartType === 'pie') {

        }
        else {
            options.yAxis = xAxis;
        }
        return options;
    };
    /**
     * 设置y轴
     * 支持多轴
     *
     * @private
     */
    UI_E_CHART_CLASS.$setupYAxis = function (options) {
        if (this._chartType !== 'pie') {
            var yAxis = [];
            if (this._aYAxis && this._aYAxis.length > 0) {
                var yAxisOption;
                for (var i = 0, option; option = this._aYAxis[i]; i ++) {
                    yAxisOption = {};
                    yAxisOption.name = option.title.text;
                    yAxisOption.type = 'value';
                    yAxisOption.splitArea = { show : true };
                    yAxisOption.boundaryGap = [0.1, 0.1];
                    yAxisOption.splitNumber = 5;
//                    if (option.title.text) {
//                        yAxisOption.axisLabel = {
//                            formatter: '{value} '+ option.title.text
//                        }
//                    }
                    yAxis.push(yAxisOption);
                }
            }
            else {
                yAxisOption = {};
                yAxisOption.type = 'value';
                yAxisOption.splitArea = { show : true };
                yAxisOption.boundaryGap = [0.1, 0.1];
                yAxisOption.splitNumber = 5;
                yAxis.push(yAxisOption);
            }
            if (this._isAddYxis && yAxis.length <= 1) {
                yAxis.push(yAxisOption);
                for (var i = 0, iLen = options.series.length; i < iLen; i ++) {
                    var o = options.series[i];
                    if (o.type === 'bar') {
                        delete o.yAxisIndex;
                    }
                    if (o.type === 'line') {
                        o.yAxisIndex = 1;
                    }
                    else {
                        o.yAxisIndex = 0;
                    }
                }
            }
        }
        if (this._chartType === 'bar') {
            options.xAxis = yAxis;
        }
        if (this._chartType === 'column' || this._chartType === 'line') {
            options.yAxis = yAxis;
        }
    };
    /**
     * 设置图例
     *
     * @protected
     */
    UI_E_CHART_CLASS.$setupLegend = function (options) {
        var legend = {
            // orient: 'vertical',
            x: 'center',
            y: 'bottom'
//            borderColor: '#ccc',
//            borderWidth: 0.5
        };
        var data = [];
        var defaultMeasures = this._defaultMeasures;

        if (this._chartType === 'pie') {
            for (var i = 0; i < this._aXAxis.data.length; i++) {
                data[i] = this._aXAxis.data[i];
            }
        }
        else {
            if (this._aSeries && this._aSeries.length > 0) {
                for (var i = 0; i < this._aSeries.length; i++) {
                    if (defaultMeasures) {
                        if (isInArray(this._aSeries[i].name, defaultMeasures)) {
                            data.push(this._aSeries[i].name);
                        }
                    }
                    else {
                        data.push(this._aSeries[i].name);
                    }
                }
            }
        }
        legend.data = data;
        options.legend = legend;
    };
    /**
     * 设置工具箱
     *
     * @protected
     */
    UI_E_CHART_CLASS.$setupToolBox = function (options) {
        var toolbox;
        var series;
        var itemChartType = {};
        var chartTypeLen = 0;
        // 如果是柱状图或者条形图，series中数据大与一个，且每一个的图形一致；才显示图形种类
        if (this._chartType === 'bar' || this._chartType === 'column') {
            series = this._aSeries;
            for (var i = 0; i < series.length; i++ ) {
                itemChartType[series[i].type] = 1;
            }
            for (var key in itemChartType) {
                if (itemChartType.hasOwnProperty(key)) {
                    chartTypeLen ++;
                }
            }
            if (series.length === 1 || chartTypeLen >= 2) {
                return;
            }
            toolbox = {
                show: true,
                orient : 'horizontal',
                x: 'right',
                y : 'top',
                feature : {
                    magicType : {show: true, type: ['stack', 'tiled']}
                }
            };
            options.toolbox = toolbox;
        }


    };
    /**
     * 设置dataRoom
     *
     * @private
     */
    UI_E_CHART_CLASS.$setupDataRoom = function (options) {
        // 此方法内只接受data中的start与end
        var dataZoom = {};
        var categories = {};

        if (this._aXAxis) {
            categories = this._aXAxis;
        }

        if (
            this._chartType === 'column'
            || this._chartType === 'bar'
            || this._chartType === 'line'
            ) {
            dataZoom.show = false;
            var xNums = categories.data ? categories.data.length : 0;
            var enableSelectRange = false;

            enableSelectRange = (xNums > 10 && this._aXAxis.type !== 'category')
                ? true
                : enableSelectRange;
            dataZoom.show = enableSelectRange;
            setupRangSelector.call(this, options, enableSelectRange);

            dataZoom.realtime = true;
            if (this._zoomStart === 0) {
                dataZoom.start = this._zoomStart;
            }
            else {
                dataZoom.start = Math.round(101 / xNums * this._zoomStart);
            }

            if (this._zoomEnd === (xNums - 1 )) {
                dataZoom.end = 100;
            }
            else {
                dataZoom.end = Math.round(101 / xNums * this._zoomEnd);
            }
            options.dataZoom = dataZoom;
        }

    };
    function setupRangSelector(options, enabled) {
        var me = this;
        var xDatas;
        // 禁用rangeselector的情况
        if (!enabled) {
            return;
        }

        xDatas = me._aXAxis.data;
        createRangeHtml.call(me);

        me._zoomButtons.onclick = function (ev) {
            var target = ev.target;
            if (ev.target.tagName.toLowerCase() === 'span') {
                me._zoomSelectedButton = Number(target.getAttribute('selRangeIndex'));
                me._oldZoomSelectButton && removeClass(me._oldZoomSelectButton, 'zoom-button-focus');
                addClass(ev.target, 'zoom-button-focus');
                me._oldZoomSelectButton = target;
                me._zoomStart = (me._zoomSelectedButton == 0)
                    ? 0
                    : (xDatas.length - (me._zoomSelectedButton * 30));
                me._zoomStart = (me._zoomStart <= 0)
                    ? 0
                    : me._zoomStart;
                me._zoomEnd = xDatas.length - 1;
            }
            // TODO:校验，如果所选时间的长度大于当前时间存在的时间，就不重绘，没必要，因为展现的东西还是一样的
            me.render();
        };
        var oMinDate = q('zoomMin', this._zoomDateRange)[0];
        var oMaxDate = q('zoomMax', this._zoomDateRange)[0];
        // 当from to改变后，render图形
        document.onkeydown = function() {
            if (event.keyCode === 13) {
                dateRangeChange.call(me, oMinDate, oMaxDate);
            }
        };
        oMinDate.onblur = function () {
            dateRangeChange.call(me, oMinDate, oMaxDate);
        };
        oMaxDate.onblur = function () {
            dateRangeChange.call(me, oMinDate, oMaxDate);
        };

        var min = xDatas[me._zoomStart];
        var max = xDatas[me._zoomEnd];
        oMinDate.value = min;
        oMaxDate.value = max;
        me._oldMinDate = min;
        me._oldMaxDate = max;
    }
    // 创建html元素
    function createRangeHtml() {
        var buttons;
        var axisType = this._aXAxis.type;
        this._zoomSelectedButton = (this._zoomSelectedButton === undefined)
            ? 0
            : this._zoomSelectedButton;
        if (axisType === 'date') {
            buttons = [
                { type: 'month', count: 1, text: '1月' },
                { type: 'month', count: 2, text: '2月' },
                { type: 'all', count: 0, text: '全部' }
            ];
        }
        else if (axisType === 'month') {
            buttons = [
                { type: 'month', count: 6, text: '6月' },
                { type: 'year', count: 12, text: '1年' },
                { type: 'all', count: 0, text: '全部' }
            ];
        }
        else {
            buttons = [
                { type: 'all', count: 0, text: '全部' }
            ];
        }

        // zoom按钮html模板
        var buttonsHtml = [
            '<ul class="zoom-buttons">'
        ];
        for (var i = 0, len = buttons.length; i < len; i++) {
            // li模版：<li><span selRangeIndex="1" class="zoom-button-focus">1月</span></li>
            buttonsHtml.push(
                '<li>',
                '<span selRangeIndex ="', buttons[i].count, '"',
                    this._zoomSelectedButton == buttons[i].count
                    ? ' class="zoom-button-focus"'
                    : '',
                '>', buttons[i].text, '</span>',
                '</li>'
            );
        }
        buttonsHtml.push('</ul>');
        // 时间范围html模板
        var selectRangeHtml = [
            '<div class="zoom-dateRange">',
            '<span>From:</span>',
            '<input class="zoomMin" type="text">',
            '<span>To:</span>',
            '<input class="zoomMax" type="text">',
            '</div>'
        ].join('');
        this._eHeader.innerHTML = buttonsHtml.join('') + selectRangeHtml;

        this._zoomButtons = domChildren(this._eHeader)[0];
        this._oldZoomSelectButton = q('zoom-button-focus', this._zoomButtons)[0];
        this._zoomDateRange = domChildren(this._eHeader)[1];
    }
    // 当时间range改变后
    function dateRangeChange(oMinDate, oMaxDate) {
        var xDatas = this._aXAxis.data;
        var start;
        var end;
        var minDate = oMinDate.value;
        var maxDate = oMaxDate.value;
        for (var i = 0, iLen = xDatas.length; i < iLen; i++) {
            if (minDate === xDatas[i]) {
                start = i;
            }
            if (maxDate === xDatas[i]) {
                end = i;
            }
        }
        if ((start === 0 || start) && end) {
            if ((xDatas[start] === this._oldMinDate)
                && (xDatas[end] === this._oldMaxDate)
                ) {
                return;
            }
            this._zoomStart = start;
            this._zoomEnd = end;
            var oZoomSelBtn = q('zoom-button-focus', this._zoomButtons)[0];
            oZoomSelBtn && removeClass(oZoomSelBtn, 'zoom-button-focus');
            this._zoomSelectedButton = -1;
            this.render();
        }
        else {
            oMinDate.value = this._oldMinDate;
            oMaxDate.value = this._oldMaxDate;
        }
    }
    /**
     * 设置提示浮层
     *
     * @protected
     */
    UI_E_CHART_CLASS.$setupTooptip = function (options) {
        var toolTip = {};

        if (this._chartType === 'pie') {
            toolTip.formatter = "{a} <br/>{b} : {c} ({d}%)";
            toolTip.trigger = 'item';
        }
        else {
            toolTip.trigger = 'axis';
            // 在此将提示信息的format属性加上以便方便显示
            toolTip.formatter =  function(data, ticket, callback) {
                var res = data[0][1];
                for (var i = 0, l = data.length; i < l; i++) {
                    var valueFormat = options.series[i].format;
                    var valueLable = data[i][2];
                    // 当发现图数据有配置format属性时，按format所示进行展示
                    // 当没有format的时候，展示原值
                    if (valueFormat) {
                        valueLable = formatNumber(
                            data[i][2],
                            valueFormat,
                            null,
                            null,
                            true
                        );
                    }
                    res += '<br/>' + data[i][0] + ' : ' + valueLable;
                }
                return res;
            }
        }
        options.tooltip = toolTip;
    };

    /**
     * 重新渲染图表
     *
     * @public
     */
    UI_E_CHART_CLASS.render = function () {
        this.$disposeChart();
        // 如果没有数据，图形显示空
        if (!this._aSeries || this._aSeries.length == 0) {
            this._eContent.innerHTML = ''
                + '<div class="' + this._sType + '-empty">'
                +     this._sEmptyHTML
                + '</div>';
            return;
        }
        this.$preload();
        this.$createChart(this.$initOptions());
    };

    /**
     * 创建图表
     *
     * @public
     */
    UI_E_CHART_CLASS.$createChart = function (options) {
        var start;
        var end;
        var xDatas = this._aXAxis.data;
        this._oChart = echarts.init(this._eContent);
        this._oChart.setOption(options);
        if (!this._chartType === 'pie') {
            this._oChart.on(echarts.config.EVENT.DATA_ZOOM, zoomChage);
        }
        function zoomChage(param) {
            start = param.zoom.xStart;
            end = param.zoom.xEnd;
            changeDateRange();
        }
        function changeDateRange() {
            var oMinDate = q('zoomMin', this._zoomDateRange)[0];
            var oMaxDate = q('zoomMax', this._zoomDateRange)[0];
            oMinDate.value = xDatas[start];
            oMaxDate.value = xDatas[end - 1];
        }
    };
    /**
     * 构建图表参数
     *
     * @private
     */
    UI_E_CHART_CLASS.$initOptions = function () {
        var options = {
            title: { text: '' }
        };

        this.$setupSeries(options);
        this.$setupTooptip(options);
        if (
            this._chartType === 'column'
            || this._chartType === 'bar'
            || this._chartType === 'line'
            || this._chartType === 'pie'
            ) {
            this.$setupDataRoom(options);
            this.$setupToolBox(options);
            this.$setupYAxis(options);
            this.$setupLegend(options);
            this.$setupXAxis(options);
        }
        else if (this._chartType === 'map') {
            options.roamController = {
                show: true,
                x: 'right',
                mapTypeControl: {
                    'china': true
                }
            };
            // TODO:需要后端返回最大最小值
            options.dataRange = {
                min: this._mapMinValue,
                max: this._mapMaxValue,
                x: 'left',
                y: 'bottom',
                text:['高','低'],           // 文本，默认为数值文本
                calculable : true
            };
        }
        if (this._chartType === 'pie') {
            options.calculable = true;
        }
        return options;
    };
    UI_E_CHART_CLASS.$preload = function () {
        for (var i = 0, ser; ser = this._aSeries[i]; i ++) {
            this._chartType = ser.type;
        }
        if (this._allMeasures) {
            this.$renderCheckBoxs();
        }
    };
    /**
     * 销毁图表
     *
     * @private
     */
    UI_E_CHART_CLASS.$disposeChart = function () {
        document.onkeydown = null;
        if (this._oChart) {
            this._oChart.clear();
            this._oChart.dispose();
            this._oChart = null;
        }
        this._eContent && (this._eContent.innerHTML = '');
    };
    /**
     * 销毁图表
     *
     * @private
     */
    UI_E_CHART_CLASS.$disposeHeader = function () {
        this._eHeader && (this._eHeader.innerHTML = '');
    };
    /**
     * @override
     */
    UI_E_CHART_CLASS.dispose = function () {
        this.$disposeChart();
        UI_E_CHART.superClass.dispose.call(this);
    };

})();
