package org.example.tjp.bean;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ExpVOExpNew {
    @ExcelProperty("供电单位编码")
    private String mgtOrgCode;
    @ExcelProperty("供电单位名称")
    private String mgtOrgCodeName;


    @ExcelProperty("使用人数")
    private Long syrs = 0L;
    @ExcelProperty("使用人次")
    private Long syrc = 0L;
    @ExcelProperty("指令使用人数（去除唤醒）")
    private Long zlsyrs = 0L;
    @ExcelProperty("指令使用人次（去除唤醒）")
    private Long zlsyrc = 0L;


    @ExcelProperty({"其中", "业务办理"})
    private Long ywbl = 0L;
    @ExcelProperty({"其中", "查看指标"})
    private Long ckzb = 0L;
    @ExcelProperty({"其中", "查询作业工单"})
    private Long zygd = 0L;
    @ExcelProperty({"其中", "工作票"})
    private Long gzp = 0L;
    @ExcelProperty({"其中", "设备操控"})
    private Long sbck = 0L;
    //扫码装拆明细数量
    @ExcelProperty({"其中", "装拆作业辅助(实际操作次数)"})
    private Long zccount = 0L;
    @ExcelProperty({"其中", "知识问答数"})
    private Long zswds = 0L;
    @ExcelProperty({"其中", "其他"})
    private Long qt = 0L;
    @ExcelProperty({"其中", "其他(无效)"})
    private Long qtwx = 0L;


    @ExcelProperty("全量装拆工单数据")
    private Long qlzcgd;
    @ExcelProperty("移动作业终端工单处理量")
    private Long ydzyzdgd;
    //装拆智能助手处理量
    @ExcelProperty("移动作业助手工单处理量")
    private Long znzscll = 0L;
    @ExcelProperty("移动作业助手处理量占比")
    private Long ydzszb;
}
