define(['template'], function (template) {
    function anonymous($data,$filename) {
        'use strict';
        $data=$data||{};
        var $utils=template.utils,$helpers=$utils.$helpers,$escape=$utils.$escape,compId=$data.compId,$each=$utils.$each,xAxis=$data.xAxis,item=$data.item,$index=$data.$index,$out='';$out+='<div class="con-comp-setting-type1 j-comp-setting j-comp-select"  data-comp-id="';
        $out+=$escape(compId);
        $out+='" data-comp-type="SELECT">\r\n    <div class="data-axis-line data-axis-line-48 j-comp-setting-line j-line-x" data-axis-type="x">\r\n        <span class="letter">维度:</span>\r\n        ';
        $each(xAxis,function(item,$index){
        $out+='\r\n        <div class="item hover-bg j-root-line" data-id="';
        $out+=$escape(item.id);
        $out+='" data-name="';
        $out+=$escape(item.name);
        $out+='">\r\n            <span class="item-text j-item-text icon-font" title="';
        $out+=$escape(item.caption);
        $out+='（';
        $out+=$escape(item.name);
        $out+='）">\r\n            ';
        $out+=$escape(item.caption);
        $out+='（';
        $out+=$escape(item.name);
        $out+='）\r\n            </span>\r\n            <span class="icon hide j-delete" title="删除">×</span>\r\n        </div>\r\n        ';
        });
        $out+='\r\n    </div>\r\n    <div class="data-axis-line data-axis-line-48 data-btn-line">\r\n        <span class="letter">设置:</span>\r\n        <span>下拉框类型：</span>\r\n        <select class="select-type" data-comp-id="';
        $out+=$escape(compId);
        $out+='">\r\n            <option value="ECUI_SELECT" ';
        if($data.compMold && $data.compMold==="ECUI_SELECT"){
        $out+=' selected="selected"';
        }
        $out+='>\r\n                单选\r\n            </option>\r\n            <option value="ECUI_MULTI_SELECT" ';
        if($data.compMold && $data.compMold==="ECUI_MULTI_SELECT"){
        $out+=' selected="selected"';
        }
        $out+='>\r\n                多选\r\n            </option>\r\n        </select>\r\n    </div>\r\n</div>';
        return $out;
    }
    return { render: anonymous };
});