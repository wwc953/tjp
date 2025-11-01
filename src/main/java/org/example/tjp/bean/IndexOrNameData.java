package org.example.tjp.bean;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.stereotype.Indexed;

import java.io.Serializable;
import java.util.Date;

@Data
//@Document(indexName = "loginfo_#{T(java.time.LocalDate).now().toString()}")
@Document(indexName = "loginfo")
@Setting(replicas = 0)
public class IndexOrNameData implements Serializable {
    private String _class;

    @Id
    private String id;

    @ExcelProperty("产品名称")
    private String procName;

    @ExcelProperty("产品编码")
    private String procCode;

    @ExcelProperty("工单编号")
    private String appNo;

    @ExcelProperty("供电单位")
    private String mgtOrgName;

    @ExcelProperty("供电单位编码")
    private String mgtOrgCode;

    @ExcelProperty("操作账号")
    private String handleId;

    @ExcelProperty("进入方式")
    private String viewType;

    @ExcelProperty("操作时间")
//    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date systemTime;

    @ExcelProperty("人员姓名")
    private String handleName;

    @ExcelProperty("环节名称")
    private String stepName;

    @ExcelProperty("接口名称")
    private String linkName;

    @ExcelProperty("环节编码")
    private String stepCode;

    @ExcelProperty("当前操作界面")
    private String operView;

    @ExcelProperty("操作行为")
    private String operType;

    @ExcelProperty("操作结果")
    private String operResult;

    @ExcelProperty("访问IP")
    private String operIp;

    private String cityCode;

    private String cityCodeName;

    private String countryCode;

    private String countryCodeName;

}
