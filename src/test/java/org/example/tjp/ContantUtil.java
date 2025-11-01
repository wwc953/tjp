package org.example.tjp;

import java.util.Arrays;
import java.util.List;

public class ContantUtil {
    public static String CITY = "cityCode";
    public static String MGT = "mgtOrgCode";
    public static String COUNTRY = "countryCode";

    public static String qtwxType = "其他(无效)";
    public static String zsOperType = "知识问答";

    public static String fileName = "2025-09-11.xlsx";

    //知识问答
    public static List<String> zsList = Arrays.asList("查询知识库", "大模型数据", "查询知识详情", "知识考试", "练习题库",
            "大模型知识", "文本知识");
    
    //其他(无效)
    public static List<String> qtwxList = Arrays.asList("云问文本展示", "无效指令");

}
