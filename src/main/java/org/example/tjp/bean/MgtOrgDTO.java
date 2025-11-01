package org.example.tjp.bean;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Data
public class MgtOrgDTO implements Serializable {
    @ExcelProperty("供电单位编码")
    String mgtOrgCode;
    @ExcelProperty("供电单位名称")
    String mgtOrgCodeName;
    @ExcelProperty("上级供电单位编码")
    String prMgtOrgCode;
    @ExcelProperty("上级供电单位名称")
    String prMgtOrgCodeName;

}
