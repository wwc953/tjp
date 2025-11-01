package org.example.tjp;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.util.NamedValue;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.example.tjp.bean.ExpVO;
import org.example.tjp.bean.ExpVOExpNew;
import org.example.tjp.bean.IndexOrNameData;
import org.example.tjp.bean.MgtOrgDTO;
import org.example.tjp.dao.IndexOrNameDataEsDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


@Slf4j
@SpringBootTest
class TjpApplicationTests {
    String fileName = "2025-10-30.xlsx";
    String readFilePath = "src/main/resources/xlsx/" + fileName;
    String day = fileName.split("\\.")[0].replaceAll("-", "");
    String index = "loginfo";
    //    String index = index_all + "_" + day;
    String type = "_doc";
    String path = "src/main/resources/xlsx/";

    //求和
    List<String> proList = Lists.newArrayList();

    Map<String, MgtOrgDTO> codeMap;
    Map<String, List<MgtOrgDTO>> rootChildList;

    @Autowired
    ElasticsearchClient esClient;

    @Autowired
    IndexOrNameDataEsDao indexOrNameDataEsDao;

    @Autowired
    ResourceLoader resourceLoader;

    @SneakyThrows
    @Test
    public void doDay() {
        importData(new File(readFilePath));
        refresh(index);
        doExpAll();
    }

    @Test
    public void doExpAll() {
//        index = index_all;

        Arrays.asList("32101", "32401", "32402").forEach(v -> {
            expDay(ContantUtil.CITY, List.of(v));
        });

        //睢宁县供电公司、高邮供电公司省网
        expDay(ContantUtil.COUNTRY, Arrays.asList("3240307", "3241008"), false);
        //导出明细
        expDetail();

        //溧水,睢宁
        Arrays.asList("3240106", "3240307").forEach(v -> {
            expDay(ContantUtil.COUNTRY, List.of(v),
//                    Arrays.asList("ywbl", "sbck"),//业务办理、设备操控
                    true);
        });

//        expDay(ContantUtil.MGT, null, false);
    }

    @Test
    public void batchImport() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:xlsx2/");
        File[] files = resource.getFile().listFiles();
        assert files != null : "no files";
        System.out.println("文件:" + files.length);
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CountDownLatch cd = new CountDownLatch(files.length);
        for (File file : files) {
            executorService.execute(() -> {
                String fileName = file.getName();
                try {
                    String fx = fileName.split("\\.")[1];
                    if ("xlsx".equals(fx)) {
                        importData(file);
                    } else {
                        System.out.println("other file name: " + fileName);
                    }
                } catch (Exception e) {
                    log.error("fileName--{}", fileName, e);
                } finally {
                    cd.countDown();
                }
            });
        }
        cd.await();
        executorService.shutdown();
        refresh(index);
        doExpAll();
    }

    public void expDay(String type, List<String> mgtOrgCode) {
        expDay(type, mgtOrgCode, null, true);
    }

    public void expDay(String type, List<String> mgtOrgCode, boolean next) {
        expDay(type, mgtOrgCode, null, next);
    }

    @SneakyThrows
    public void expDay(String type, List<String> mgtOrgCode, List<String> excludeColumnFieldNameList, boolean next) {
        BoolQuery.Builder rootQuery = QueryBuilders.bool()
                .must(
                        QueryBuilders.bool()
                                .mustNot(buildTermsQuery("operView.keyword", Arrays.asList("退出机器人", "连接成功", "连接失败", "关闭助理", "通知唤醒", "初始化机器人")))
                                .mustNot(QueryBuilders.bool()
                                        .mustNot(QueryBuilders.exists(e -> e.field("linkName")))
                                        .mustNot(buildTermsQuery("operView.keyword", Arrays.asList("语音唤醒", "点击唤醒")))
                                        .build())
                                .build()
                );

        if (CollectionUtils.isNotEmpty(mgtOrgCode)) {
            if (ContantUtil.CITY.equals(type) && !"32101".equals(mgtOrgCode.get(0))) {
                rootQuery.filter(buildTermsQuery("cityCode.keyword", mgtOrgCode));
            }
            if (ContantUtil.COUNTRY.equals(type)) {
                rootQuery.filter(buildTermsQuery("countryCode.keyword", mgtOrgCode));
            }
            if (ContantUtil.MGT.equals(type)) {
                rootQuery.filter(buildTermsQuery("mgtOrgCode.keyword", mgtOrgCode));
            }
        }

        BoolQuery build = rootQuery.build();

        String field = type;
        if (next) {
            if (ContantUtil.CITY.equals(type)) {
                field = "32101".equals(mgtOrgCode.get(0)) ? ContantUtil.CITY : ContantUtil.COUNTRY;
            }
            if (ContantUtil.COUNTRY.equals(type) || ContantUtil.MGT.equals(type)) {
                field = ContantUtil.MGT;
            }
        }

        Map<String, Aggregation> map = new HashMap<>();

        Query filterzlrs = QueryBuilders.bool()
                .mustNot(buildTermsQuery("operView.keyword", Arrays.asList("点击唤醒", "语音唤醒")))
                .build()._toQuery();

        Query filterzc = QueryBuilders.bool()
                .must(TermQuery.of(a -> a.field("operType.keyword").value("装拆作业")))
                .must(TermQuery.of(a -> a.field("appNo.keyword").value("_")))
                .build()._toQuery();

        Query filterznzscll = QueryBuilders.bool()
                .must(TermQuery.of(a -> a.field("operType.keyword").value("装拆作业")))
                .mustNot(TermQuery.of(a -> a.field("appNo.keyword").value("_")))
                .build()._toQuery();

        map.put("oper_type", TermsAggregation.of(a -> a.field("operType.keyword").size(1000))._toAggregation());
        map.put("dis_rs", CardinalityAggregation.of(a -> a.field("handleId.keyword"))._toAggregation());
        map.put("filter_zlrs", new Aggregation.Builder().filter(filterzlrs)
                .aggregations("dis_zlrs", CardinalityAggregation.of(a -> a.field("handleId.keyword"))).build());
        map.put("filter_zc", new Aggregation.Builder().filter(filterzc).build());
        map.put("filter_znzscll", new Aggregation.Builder().filter(filterznzscll)
                .aggregations("znzscll_dis", CardinalityAggregation.of(a -> a.field("appNo.keyword"))).build());

        Aggregation aggregation = new Aggregation.Builder()
                .terms(new TermsAggregation.Builder().field(field + ".keyword").size(2000)
                        .order(NamedValue.of("_key", SortOrder.Asc)).build())
                .aggregations(map)
                .build();

        SearchRequest searchRequest = SearchRequest.of(s -> s.index(index).query(build).size(0)
                .aggregations("mgtorg_agg", aggregation));

        System.out.println(searchRequest.toString());
        SearchResponse<Void> searchResponse = esClient.search(searchRequest);
        System.out.println(searchResponse.toString());

        List<ExpVO> result = new ArrayList<>();

        Aggregate mgtorgAgg = searchResponse.aggregations().get("mgtorg_agg");
        List<StringTermsBucket> termsBuckets = mgtorgAgg.sterms().buckets().array();
        for (StringTermsBucket bucket : termsBuckets) {
            String key = bucket.key().stringValue();
            if ("32101".equals(key)) {
                continue;
            }
            ExpVO expVO = new ExpVO();
            expVO.setMgtOrgCode(key);
            expVO.setSyrc(bucket.docCount());

            Map<String, Aggregate> stringAggregateMap = bucket.aggregations();
            expVO.setSyrs(stringAggregateMap.get("dis_rs").cardinality().value());

            FilterAggregate filter_zlrs = stringAggregateMap.get("filter_zlrs").filter();
            expVO.setZlsyrc(filter_zlrs.docCount());
            expVO.setZlsyrs(filter_zlrs.aggregations().get("dis_zlrs").cardinality().value());

            expVO.setZczyfz(stringAggregateMap.get("filter_zc").filter().docCount());

            FilterAggregate filter_znzscll = stringAggregateMap.get("filter_znzscll").filter();
            expVO.setZnzscll(filter_znzscll.aggregations().get("znzscll_dis").cardinality().value());
            expVO.setZcall(expVO.getZczyfz() + expVO.getZnzscll());

            List<StringTermsBucket> operTypeList = stringAggregateMap.get("oper_type").sterms().buckets().array();

            for (StringTermsBucket operType : operTypeList) {
                String operTypeKeyAsString = operType.key().stringValue();
                long docCount = operType.docCount();
                switch (operTypeKeyAsString) {
                    case "业务办理":
                        expVO.setYwbl(docCount);
                        break;
                    case "查看指标":
                        expVO.setCkzb(docCount);
                        break;
                    case "作业工单":
                        expVO.setZygd(docCount);
                        break;
                    case "工作票":
                        expVO.setGzp(docCount);
                        break;
                    case "设备操控":
                        expVO.setSbck(docCount);
                        break;
                    case "装拆作业":
                        expVO.setZccount(docCount);
                        break;
                    case "知识问答":
                        expVO.setZswds(docCount);
                        break;
                    case "其他":
                        expVO.setQt(docCount);
                        break;
                    case "其他(无效)":
                        expVO.setQtwx(docCount);
                        break;
                    default:
                        break;
                }
            }
            result.add(expVO);
        }
        System.out.println(JSON.toJSONString(result));

        List<ExpVO> dataList = Lists.newArrayList();
        if (next) {
            dataList = getChildList(mgtOrgCode.get(0));
        } else if (CollectionUtils.isNotEmpty(mgtOrgCode)) {
            for (String code : mgtOrgCode) {
                MgtOrgDTO mgtOrgDTO = codeMap.get(code);
                ExpVO expVO = new ExpVO();
                BeanUtils.copyProperties(mgtOrgDTO, expVO);
                dataList.add(expVO);
            }
        } else {
            for (MgtOrgDTO mgtOrgDTO : codeMap.values()) {
                ExpVO expVO = new ExpVO();
                BeanUtils.copyProperties(mgtOrgDTO, expVO);
                dataList.add(expVO);
            }
        }

        Map<String, ExpVO> collect = result.stream().collect(Collectors.toMap(ExpVO::getMgtOrgCode, v -> v));
        dataList.forEach(v -> {
            ExpVO expVO = collect.get(v.getMgtOrgCode());
            if (expVO != null) {
                String mgtOrgCodeName = v.getMgtOrgCodeName();
                BeanUtils.copyProperties(expVO, v);
                v.setMgtOrgCodeName(mgtOrgCodeName);
            } else {
                System.out.println(v.getMgtOrgCode() + "--无数据");
            }
        });

        dataList.add(getAllVO(dataList));

        String fileName = path;

        if (next) {
            fileName += codeMap.get(mgtOrgCode.get(0)).getMgtOrgCodeName() + ".xlsx";
        } else if (CollectionUtils.isNotEmpty(mgtOrgCode)) {
            fileName += "其他.xlsx";
        } else {
            fileName += "all.xlsx";
        }

        List<String> excludeColumnFieldNames = Lists.newArrayList("mgtOrgCode");
        if (CollectionUtils.isNotEmpty(excludeColumnFieldNameList)) {
            excludeColumnFieldNames.addAll(excludeColumnFieldNameList);
        }
        if (CollectionUtils.isEmpty(mgtOrgCode)) {
            excludeColumnFieldNames = Lists.newArrayList();
        }

        List<ExpVOExpNew> res = new ArrayList<>();
        dataList.forEach(v -> {
            ExpVOExpNew expVOExpNew = new ExpVOExpNew();
            BeanUtils.copyProperties(v, expVOExpNew);
            res.add(expVOExpNew);
        });
        EasyExcel.write(fileName, ExpVOExpNew.class).excludeColumnFieldNames(excludeColumnFieldNames)
                .useDefaultStyle(false).registerWriteHandler(new LongestMatchColumnWidthStyleStrategy()).sheet("").doWrite(res);


    }

    @SneakyThrows
    public void refresh(String index) {
        RefreshResponse refresh = esClient.indices().refresh(new RefreshRequest.Builder().index(index).build());
        System.out.println("refresh res===" + refresh.shards());
    }

    @Test
    public void expDetail() {
        List<QueryAO> initList = new ArrayList<>();
        initList.add(new QueryAO("南京", List.of("32401"), ContantUtil.CITY));
        initList.add(new QueryAO("无锡", List.of("32402"), ContantUtil.CITY));
//        initList.add(new QueryAO("苏州", List.of("32405"), ContantUtil.CITY));
        initList.add(new QueryAO("徐州睢宁公司", List.of("3240307")));
        initList.add(new QueryAO("扬州高邮公司", Arrays.asList("3241004", "3241008")));
        List<List<IndexOrNameDataVO>> resultDataList = new ArrayList<>();
        initList.forEach(ao -> {
            resultDataList.add(queryLogList(ao.getCodeList(), ao.getType()));
        });

        String fileName = path;
//        if (!StringUtils.equals(index, index_all)) {
//            fileName += day;
//        }
        try (ExcelWriter excelWriter = EasyExcel.write(fileName + "操作明细.xlsx").useDefaultStyle(false).registerWriteHandler(new LongestMatchColumnWidthStyleStrategy()).build()) {
            for (int i = 0; i < initList.size(); i++) {
                QueryAO ao = initList.get(i);
                WriteSheet writeSheet = EasyExcel.writerSheet(i, ao.getSheetName()).head(IndexOrNameDataVO.class).build();
                excelWriter.write(resultDataList.get(i), writeSheet);
            }
        }
    }

    /**
     * 查询日志明细
     */
    @SneakyThrows
    public List<IndexOrNameDataVO> queryLogList(List<String> codeList, String queryType) {
        if (StringUtils.isBlank(queryType)) {
            queryType = ContantUtil.COUNTRY;
        }
        System.out.println("queryLogList==>" + index + "--" + queryType + "--" + codeList);

        BoolQuery.Builder rootQuery = QueryBuilders.bool();

        BoolQuery bp1 = QueryBuilders.bool()
                .mustNot(buildTermsQuery("operView.keyword", Arrays.asList("退出机器人", "连接成功", "连接失败", "关闭助理", "通知唤醒", "初始化机器人")))
                .mustNot(QueryBuilders.bool()
                        .mustNot(QueryBuilders.exists(e -> e.field("linkName")))
                        .mustNot(buildTermsQuery("operView.keyword", Arrays.asList("语音唤醒", "点击唤醒")))
                        .build())
                .mustNot(QueryBuilders.term(t -> t.field("operType.keyword").value("唤醒")))
                .build();
        rootQuery.must(bp1);

        if (StringUtils.equals(type, ContantUtil.CITY)) {
            queryType = ContantUtil.CITY;
        } else if (StringUtils.equals(type, ContantUtil.MGT)) {
            queryType = ContantUtil.MGT;
        }
        rootQuery.filter(buildTermsQuery(queryType + ".keyword", codeList));

        SearchRequest searchRequest = SearchRequest.of(s -> s.index(index).query(rootQuery.build()).size(100000));
        System.out.println(searchRequest.toString());
        SearchResponse<Object> search = esClient.search(searchRequest, Object.class);

        List<IndexOrNameDataVO> result = new ArrayList<>(500);
        List<Hit<Object>> hits = search.hits().hits();
        System.out.println(hits.size());
        for (Hit<Object> hit : hits) {
            result.add(JSON.parseObject(JSON.toJSONString(hit.source()), IndexOrNameDataVO.class));
        }
        return result;
    }

    public TermsQuery buildTermsQuery(String field, List<String> value) {
        return TermsQuery.of(t -> t.field(field).terms(
                new TermsQueryField.Builder().value(
                        value.stream().map(FieldValue::of).collect(Collectors.toList())
                ).build())
        );
    }

    public void initProList() {
        PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(ExpVO.class);
        Arrays.stream(propertyDescriptors).forEach(v -> {
            String name = v.getName();
            if (!name.startsWith("mgtOrg") && !"class".equals(name)) {
                proList.add(name);
            }
        });
        System.out.println(JSON.toJSONString(proList));
    }

    public List<ExpVO> getChildList(String mgtOrgCode) {
        List<ExpVO> childList = getChildList(mgtOrgCode, true);
        log.info("mgtOrgCode child:{}", JSON.toJSONString(childList));
        return childList;
    }

    public List<ExpVO> getChildList(String mgtOrgCode, boolean root) {
        List<MgtOrgDTO> list = rootChildList.get(mgtOrgCode);
        if (root && !"32101".equals(mgtOrgCode)) {
            list.add(codeMap.get(mgtOrgCode));
        }
        return list.stream().map(v -> {
            ExpVO expVO = new ExpVO();
            BeanUtils.copyProperties(v, expVO);
            return expVO;
        }).sorted(Comparator.comparing(ExpVO::getMgtOrgCode)).collect(Collectors.toList());
    }

    @SneakyThrows
    private ExpVO getAllVO(List<ExpVO> dataList) {
        ExpVO all = new ExpVO();
        all.setMgtOrgCode("合计");
        all.setMgtOrgCodeName("合计");
        for (ExpVO v : dataList) {
            for (String key : proList) {
                long value = (Long) PropertyUtils.getProperty(all, key) + (Long) PropertyUtils.getProperty(v, key);
                PropertyUtils.setProperty(all, key, value);
            }
        }
        return all;
    }

    @SneakyThrows
    public void importData(File file) {
        EasyExcel.read(file, IndexOrNameData.class, new ReadListener<IndexOrNameData>() {
            int blank = 0;
            int total = 0;
            final int BATCH_COUNT = 500;
            List<IndexOrNameData> dataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
            int insert = 0;

            @SneakyThrows
            @Override
            public void invoke(IndexOrNameData dto, AnalysisContext analysisContext) {
                dto.setCityCode(StringUtils.substring(dto.getMgtOrgCode(), 0, 5));
                dto.setCityCodeName(codeMap.get(dto.getCityCode()).getMgtOrgCodeName());
                dto.setCountryCode(StringUtils.substring(dto.getMgtOrgCode(), 0, 7));
                dto.setCountryCodeName(codeMap.get(dto.getCountryCode()).getMgtOrgCodeName());
                String operView = dto.getOperView();
                String operType = dto.getOperType();
                //其他(无效)
                if ("其他".equals(operType) && ContantUtil.qtwxList.contains(operView)) {
                    dto.setOperType(ContantUtil.qtwxType);
                }
                if (ContantUtil.zsList.contains(operView)) {
                    dto.setOperType(ContantUtil.zsOperType);
                }
                if ("处理类".equals(operType)) {
                    dto.setOperType("业务办理");
                }

                if (StringUtils.isBlank(operType)) {
                    blank++;
                } else {
                    dataList.add(dto);
                }
                if (dataList.size() >= BATCH_COUNT) {
                    batchCreateUserDocument(dataList);
                    insert += BATCH_COUNT;
                    dataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
                }
                total++;
            }

            @SneakyThrows
            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                if (CollectionUtils.isNotEmpty(dataList)) {
                    batchCreateUserDocument(dataList);
                    insert += dataList.size();
                }
                System.out.println(file.getName() + ", 总数: " + total + ", 插入: " + insert + ", operType为空: " + blank);
            }
        }).sheet().doRead();
    }

    @SneakyThrows
    public void batchCreateUserDocument(List<IndexOrNameData> list) {
//        BulkRequest.Builder br = new BulkRequest.Builder();
//        for (IndexOrNameData product : list) {
//            br.operations(op -> op
//                    .index(idx -> idx
//                            .index(index)
//                            .id(product.getId())
//                            .document(product)
//                    )
//            );
//        }
//        BulkResponse result = esClient.bulk(br.build());
//        return result.errors();

        indexOrNameDataEsDao.saveAll(list);
    }


    @SneakyThrows
    @PostConstruct
    public void readMgtOrgCodeList() {
        initProList();

        List<MgtOrgDTO> result = new ArrayList<>(2000);
        File file = resourceLoader.getResource("classpath:templates/组织树数据.xls").getFile();
        EasyExcel.read(file, MgtOrgDTO.class, new ReadListener<MgtOrgDTO>() {
            @Override
            public void invoke(MgtOrgDTO dto, AnalysisContext analysisContext) {
                result.add(dto);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {
            }
        }).sheet().doRead();

        codeMap = result.stream().collect(Collectors.toMap(MgtOrgDTO::getMgtOrgCode, v -> v));
        rootChildList = result.stream().filter(v -> StringUtils.isNotBlank(v.getPrMgtOrgCode())).collect(Collectors.groupingBy(MgtOrgDTO::getPrMgtOrgCode));
    }

    @Data
    @AllArgsConstructor
    @RequiredArgsConstructor
    @NoArgsConstructor
    public static class QueryAO {
        @NonNull
        private String sheetName;
        @NonNull
        private List<String> codeList;
        private String type;
    }

    @Data
    public static class IndexOrNameDataVO {
        @ExcelProperty("产品名称")
        private String procName;
        @ExcelProperty("市单位")
        private String cityCodeName;
//        @ExcelProperty("市单位code")
//        private String cityCode;

        @ExcelProperty("区县单位")
        private String countryCodeName;
//        @ExcelProperty("区县单位code")
//        private String countryCode;

        @ExcelProperty("供电单位")
        private String mgtOrgName;
        //        @ExcelProperty("供电单位编码")
//        private String mgtOrgCode;
        @ExcelProperty("操作账号")
        private String handleId;
        @ExcelProperty("操作时间")
        private String systemTime;
        @ExcelProperty("人员姓名")
        private String handleName;
        @ExcelProperty("操作内容")
        private String operView;
        @ExcelProperty("指令分类")//操作行为
        private String operType;
//        @ExcelProperty("接口名称")
//        private String linkName;
    }
}
