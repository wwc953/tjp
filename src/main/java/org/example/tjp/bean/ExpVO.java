package org.example.tjp.bean;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ExpVO {
    @ExcelProperty("供电单位编码")
    private String mgtOrgCode;
    @ExcelProperty("供电单位名称")
    private String mgtOrgCodeName;

    @ExcelProperty("使用人数")
    private Long syrs = 0L;
    @ExcelProperty("使用人次")
    private Long syrc = 0L;
    @ExcelProperty("装拆作业辅助")
    private Long zczyfz = 0L;
    @ExcelProperty("工作票")
    private Long gzp = 0L;
    @ExcelProperty("知识问答数")
    private Long zswds = 0L;
    @ExcelProperty("查看指标")
    private Long ckzb = 0L;
    @ExcelProperty("作业工单")
    private Long zygd = 0L;
    @ExcelProperty("装拆智能助手处理量")
    private Long znzscll = 0L;
    @ExcelProperty("指令使用人数")
    private Long zlsyrs = 0L;
    @ExcelProperty("指令使用人次")
    private Long zlsyrc = 0L;
    @ExcelProperty("装拆作业辅助+装拆智能助手处理量")
    private Long zcall = 0L;
    @ExcelProperty("扫码装拆明细数量")
    private Long zccount = 0L;
    @ExcelProperty("业务办理")
    private Long ywbl = 0L;
    @ExcelProperty("设备操控")
    private Long sbck = 0L;
    @ExcelProperty("其他")
    private Long qt = 0L;
    @ExcelProperty("其他(无效)")
    private Long qtwx = 0L;

    private Long disRsSum = 0L;
    private Long disZlrsSum = 0L;

}
