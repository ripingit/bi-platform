define(['template'], function (template) {
    function anonymous($data,$filename) {
        'use strict';
        $data=$data||{};
        var $utils=template.utils,$helpers=$utils.$helpers,$out='';$out+='<div class="dim-setting">\r\n    <ul class="dim-setting-head">\r\n        <li class="classification classification-focus" id="j-tab-normal"><span>普通维度</span></li>\r\n        <li class="classification" id="j-tab-date"><span>时间维度</span></li>\r\n        <li class="classification" id="j-tab-callback"><span>回调维度</span></li>\r\n        <li class="classification" id="j-tab-custom"><span>自定义维度</span></li>\r\n    </ul>\r\n    <div class="dim-setting-body j-dim-setting-body">\r\n    </div>\r\n    <div class="ta-c mt-100">\r\n        <span class="button button-flat-primary m-10 j-dim-set-prev">返回重新选择数据表</span>\r\n        <span class="button button-flat-primary m-10 j-dim-set-ok">完成</span>\r\n        <span class="button button-flat-primary m-10 j-dim-set-cancel">取消</span>\r\n    </div>\r\n</div>\r\n\r\n';
        return $out;
    }
    return { render: anonymous };
});