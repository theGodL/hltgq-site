package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qgyun.hltgq.hltgqsite.entity.AllocateRecord;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import com.qgyun.hltgq.hltgqsite.entity.DecisionAdminMonth;
import com.qgyun.hltgq.hltgqsite.entity.DecisionAdminYear;
import com.qgyun.hltgq.hltgqsite.entity.DecisionBranchDetail;
import com.qgyun.hltgq.hltgqsite.entity.DecisionExtraTable;
import com.qgyun.hltgq.hltgqsite.entity.DecisionRecord;
import com.qgyun.hltgq.hltgqsite.entity.DecisionScaleFactor;
import com.qgyun.hltgq.hltgqsite.entity.DecisionSupplyAlloc;
import com.qgyun.hltgq.hltgqsite.entity.DemandRecord;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionAdminMonthMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionAdminYearMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionBranchDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionExtraTableMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionScaleFactorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionSupplyAllocMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.AuditUtils;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayMapUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.DecisionSubmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.intOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 配水调度服务。
 * <p>2026-10-26 定稿：入参仅配置方案（必传），需水方案不再传；
 * canal_eff 沿配置方案归档的 demand_record_id 链式追溯需水方案（取不到兜底 0.589），
 * source=配置方案 mode，调 /decision(tens=null 全 18 旬, save_excel=true)。
 * <p>缩放系数 → scale_factor 表、支渠明细（按旬字典）展开 → branch_detail 表（含缺水量/塘坝补水量/补水后总供水量）；
 * 4 张扩展表（总计_全部旬/月汇总_5至10月/分灌区汇总/片区汇总）逐行快照 → extra_table 表；
 * total_demand/total_supply 求和、unsatisfied_count（去重支渠数）与 unsatisfied_row_count（未满足明细行数，旬次口径）
 * 双口径并存回写（前端直接取，不自算）。
 * <p>事务边界：模型调用（/decision）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
 * <p>防串档：模型侧 save_excel 输出全局单文件，计算完成瞬间立即拉取并按 recordId 存档快照，下载按 recordId 取用。
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final DecisionRecordMapper recordMapper;
    private final DecisionScaleFactorMapper scaleFactorMapper;
    private final DecisionBranchDetailMapper branchDetailMapper;
    private final DecisionAdminMonthMapper adminMonthMapper;
    private final DecisionAdminYearMapper adminYearMapper;
    private final DecisionSupplyAllocMapper supplyAllocMapper;
    private final DecisionExtraTableMapper extraTableMapper;
    private final DemandRecordMapper demandRecordMapper;
    private final AllocateRecordMapper allocateRecordMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;
    private final String excelDir;

    public DecisionService(DecisionRecordMapper recordMapper,
                           DecisionScaleFactorMapper scaleFactorMapper,
                           DecisionBranchDetailMapper branchDetailMapper,
                           DecisionAdminMonthMapper adminMonthMapper,
                           DecisionAdminYearMapper adminYearMapper,
                           DecisionSupplyAllocMapper supplyAllocMapper,
                           DecisionExtraTableMapper extraTableMapper,
                           DemandRecordMapper demandRecordMapper,
                           AllocateRecordMapper allocateRecordMapper,
                           ModelClient modelClient,
                           ModelTaskExecutor taskExecutor,
                           TransactionTemplate transactionTemplate,
                           ObjectMapper objectMapper,
                           @Value("${hltgq.corp-code}") String corpCode,
                           @Value("${hltgq.created-by}") String createdBy,
                           @Value("${model.decision-excel-dir:./decision-excel}") String excelDir) {
        this.recordMapper = recordMapper;
        this.scaleFactorMapper = scaleFactorMapper;
        this.branchDetailMapper = branchDetailMapper;
        this.adminMonthMapper = adminMonthMapper;
        this.adminYearMapper = adminYearMapper;
        this.supplyAllocMapper = supplyAllocMapper;
        this.extraTableMapper = extraTableMapper;
        this.demandRecordMapper = demandRecordMapper;
        this.allocateRecordMapper = allocateRecordMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
        this.excelDir = excelDir;
    }

    /**
     * 提交配水调度（秒回 recordId），异步执行模型计算。
     * <p>2026-10-26 定稿：仅传配置方案，需水方案沿配置方案 demand_record_id 链式追溯。
     */
    public String submit(DecisionSubmitRequest req) {
        if (req == null || req.getAllocateRecordId() == null || req.getAllocateRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("水资源配置方案不能为空");
        }
        QueryWrapper<AllocateRecord> allocateWrapper = new QueryWrapper<>();
        allocateWrapper.eq("\"id\"", req.getAllocateRecordId().trim())
                .eq("\"del_flag\"", BoolTextUtils.FALSE);
        AllocateRecord allocateRecord = allocateRecordMapper.selectOne(allocateWrapper);
        if (allocateRecord == null) {
            throw new IllegalArgumentException("水资源配置方案不存在或已删除：" + req.getAllocateRecordId());
        }
        // 配置方案状态校验：failed 无结果文件、calculating 结果未生成，调 /decision 必然失败，
        // 提交时直接 400 拒绝（避免用户提交后才看到失败）
        if (!ModelRecordCommonService.STATUS_COMPLETED.equals(allocateRecord.getStatus())) {
            throw new IllegalArgumentException(
                    "所选水资源配置方案未计算完成（status=" + allocateRecord.getStatus()
                            + "），无法配水调度；请等待配置方案计算完成或重新提交");
        }
        String source = allocateRecord.getMode() == null || allocateRecord.getMode().trim().isEmpty()
                ? "auto" : allocateRecord.getMode().trim();
        // canal_eff 链式追溯：配置方案归档的 demand_record_id → 需水方案 canal_eff；
        // 追溯失败直接 400 拒绝（旧版本配置方案无 demand_record_id，兜底 0.589 会致净需水量失真）
        String demandRecordId = allocateRecord.getDemandRecordId();
        if (demandRecordId == null || demandRecordId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "该水资源配置方案未关联需水方案（旧版本方案），无法配水调度；请重新提交水资源配置方案");
        }
        QueryWrapper<DemandRecord> demandWrapper = new QueryWrapper<>();
        demandWrapper.eq("\"id\"", demandRecordId.trim())
                .eq("\"del_flag\"", BoolTextUtils.FALSE);
        DemandRecord demandRecord = demandRecordMapper.selectOne(demandWrapper);
        if (demandRecord == null) {
            throw new IllegalArgumentException("该水资源配置方案关联的需水方案不存在或已删除，无法配水调度；请重新提交水资源配置方案");
        }
        Double canalEff = demandRecord.getCanalEff();
        if (canalEff == null) {
            canalEff = 0.589;
            log.warn("需水方案 demandRecordId={} 的 canal_eff 为空，兜底 0.589", demandRecordId);
        }

        // 组装模型请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("canal_eff", canalEff);
        body.put("source", source);
        body.put("save_excel", true);
        if (req.getTens() != null && !req.getTens().isEmpty()) {
            body.put("tens", req.getTens());
        }

        // 写主表（参数留存 + 请求归档，calculating）
        DecisionRecord record = new DecisionRecord();
        record.setSchemeName(buildSchemeName(req, allocateRecord.getSchemeName()));
        record.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setCanalEff(canalEff);
        record.setSource(source);
        record.setAllocateRecordId(allocateRecord.getId());
        record.setDemandRecordId(demandRecordId);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
            record.setTens(req.getTens() == null || req.getTens().isEmpty()
                    ? null : objectMapper.writeValueAsString(req.getTens()));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(ModelRecordCommonService.operatorId(createdBy));
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        recordMapper.insert(record);

        String recordId = record.getId();
        taskExecutor.submit(() -> execute(recordId, body));
        log.info("配水调度任务已提交：recordId={}, source={}, allocateRecordId={}",
                recordId, source, allocateRecord.getId());
        return recordId;
    }

    private String buildSchemeName(DecisionSubmitRequest req, String allocateSchemeName) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "配水调度_" + (allocateSchemeName == null || allocateSchemeName.trim().isEmpty()
                ? "配置方案" : allocateSchemeName.trim());
    }

    /**
     * 异步任务：调模型 → 事务内写缩放系数/支渠明细+回写汇总 → 事务提交后存档 Excel 快照 → 更新状态。
     * <p>事务边界：模型调用（/decision）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> body) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_DECISION, body);

            // 事务内：缩放系数 + 支渠明细展开 + 汇总回写 + completed
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();

                    // 1. 缩放系数：对象 {旬标签: 系数}
                    JsonNode scaleFactors = response.path("缩放系数");
                    if (scaleFactors.isObject()) {
                        scaleFactors.fields().forEachRemaining(entry -> {
                            DecisionScaleFactor factor = new DecisionScaleFactor();
                            factor.setRecordId(recordId);
                            factor.setTendayLabel(entry.getKey());
                            Double value = entry.getValue().isNumber() ? entry.getValue().asDouble() : null;
                            factor.setScaleFactor(value);
                            Integer order = TenDayMapUtils.sortOrderOf(entry.getKey());
                            factor.setSortOrder(order == null ? null : order.doubleValue());
                            fillBase(factor, now);
                            scaleFactorMapper.insert(factor);
                        });
                    }

                    // 联调核对：三表实际键名以模型实测为准，缺键时日志留痕
                    log.info("配水调度三表键名核对：行政区月汇总={}, 行政区年汇总={}, 供水分配汇总={}",
                            firstItem(response, "行政区月汇总"),
                            firstItem(response, "行政区年汇总"),
                            response.path("供水分配汇总"));

                    // 2. 行政区月汇总：按 年份×月份×行政区 聚合（面积分配.xlsx 县级面积比例估算，非逐支渠精确分类）
                    for (JsonNode item : response.path("行政区月汇总")) {
                        DecisionAdminMonth row = new DecisionAdminMonth();
                        row.setRecordId(recordId);
                        row.setYear(intOf(item, "年份"));
                        row.setMonth(intOf(item, "月份"));
                        row.setDistrict(textOf(item, "行政区"));
                        row.setGravitySupply(doubleOf(item, "自流建议供水量"));
                        row.setLiftSupply(doubleOf(item, "提灌建议供水量"));
                        fillBase(row, now);
                        adminMonthMapper.insert(row);
                    }

                    // 3. 行政区年汇总：按 年份×行政区 聚合
                    for (JsonNode item : response.path("行政区年汇总")) {
                        DecisionAdminYear row = new DecisionAdminYear();
                        row.setRecordId(recordId);
                        row.setYear(intOf(item, "年份"));
                        row.setDistrict(textOf(item, "行政区"));
                        row.setGravitySupply(doubleOf(item, "自流建议供水量"));
                        row.setLiftSupply(doubleOf(item, "提灌建议供水量"));
                        fillBase(row, now);
                        adminYearMapper.insert(row);
                    }

                    // 4. 供水分配汇总：实测 18 行逐旬表，逐行落库；净需水量合计仅「总计_全部旬」对象有，数组行不取
                    for (JsonNode item : response.path("供水分配汇总")) {
                        DecisionSupplyAlloc supplyAlloc = new DecisionSupplyAlloc();
                        supplyAlloc.setRecordId(recordId);
                        supplyAlloc.setTendayLabel(textOf(item, "旬"));
                        Integer order = TenDayMapUtils.sortOrderOf(supplyAlloc.getTendayLabel());
                        supplyAlloc.setSortOrder(order == null ? null : order.doubleValue());
                        supplyAlloc.setDemandVolume(doubleOf(item, "需水量"));
                        supplyAlloc.setSuggestedSupply(doubleOf(item, "建议供水量"));
                        supplyAlloc.setPondSupplement(doubleOf(item, "塘坝补水量"));
                        supplyAlloc.setSupplyAfterPond(doubleOf(item, "补水后总供水量"));
                        supplyAlloc.setIsSatisfied(BoolTextUtils.normalize(textOf(item, "是否满足需水")));
                        fillBase(supplyAlloc, now);
                        supplyAllocMapper.insert(supplyAlloc);
                    }

                    // 5. 4 张扩展表逐行快照落库（键名以模型实测为准，缺键日志留痕）
                    log.info("配水调度扩展表键名核对：总计={}, 月汇总={}, 分灌区={}, 片区={}",
                            response.path("总计_全部旬"),
                            firstItem(response, "月汇总_5至10月"),
                            firstItem(response, "分灌区汇总"),
                            firstItem(response, "片区汇总"));
                    writeExtraTable(recordId, response.path("总计_全部旬"), "总计_全部旬", null, now);
                    writeExtraTable(recordId, response.path("月汇总_5至10月"), "月汇总_5至10月", "月份", now);
                    writeExtraTable(recordId, response.path("分灌区汇总"), "分灌区汇总", "分灌区", now);
                    writeExtraTable(recordId, response.path("片区汇总"), "片区汇总", "片区", now);

                    // 6. 支渠明细：对象 {旬标签: [支渠行...]}，纵向展开
                    double[] totals = new double[2];
                    Set<String> unsatisfiedBranches = new HashSet<>();
                    double[] unsatisfiedRows = new double[1];
                    JsonNode branchDetails = response.path("支渠明细");
                    if (branchDetails.isObject()) {
                        branchDetails.fields().forEachRemaining(entry -> {
                            String label = entry.getKey();
                            Integer order = TenDayMapUtils.sortOrderOf(label);
                            for (JsonNode item : entry.getValue()) {
                                DecisionBranchDetail detail = new DecisionBranchDetail();
                                detail.setRecordId(recordId);
                                detail.setTendayLabel(label);
                                detail.setDistrict(textOf(item, "片区"));
                                detail.setSubDistrict(textOf(item, "分灌区"));
                                detail.setBranchName(textOf(item, "支渠"));
                                detail.setCounty(textOf(item, "行政区"));
                                detail.setArea(doubleOf(item, "灌区面积"));
                                detail.setCropTypes(textOf(item, "种植作物种类"));
                                detail.setDemandVolume(doubleOf(item, "需水量"));
                                detail.setNetDemand(doubleOf(item, "净需水量"));
                                detail.setSuggestedSupply(doubleOf(item, "建议供水量"));
                                detail.setDeficit(doubleOf(item, "缺水量"));
                                detail.setPondSupplement(doubleOf(item, "塘坝补水量"));
                                detail.setSupplyAfterPond(doubleOf(item, "补水后总供水量"));
                                detail.setIsSatisfied(BoolTextUtils.normalize(textOf(item, "是否满足")));
                                detail.setSortOrder(order == null ? null : order.doubleValue());
                                fillBase(detail, now);
                                branchDetailMapper.insert(detail);
                                if (detail.getDemandVolume() != null) {
                                    totals[0] += detail.getDemandVolume();
                                }
                                if (detail.getSuggestedSupply() != null) {
                                    totals[1] += detail.getSuggestedSupply();
                                }
                                if (detail.getBranchName() != null
                                        && BoolTextUtils.FALSE.equals(detail.getIsSatisfied())) {
                                    unsatisfiedBranches.add(detail.getBranchName());
                                    unsatisfiedRows[0] += 1;
                                }
                            }
                        });
                    }

                    // 7. 回写汇总 + completed
                    DecisionRecord record = new DecisionRecord();
                    record.setId(recordId);
                    record.setTotalDemand(round2(totals[0]));
                    record.setTotalSupply(round2(totals[1]));
                    record.setUnsatisfiedCount((double) unsatisfiedBranches.size());
                    record.setUnsatisfiedRowCount(unsatisfiedRows[0]);
                    record.setStatus(ModelRecordCommonService.STATUS_COMPLETED);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                    log.info("配水调度完成：recordId={}, 不满足支渠{}条、未满足明细{}行",
                            recordId, unsatisfiedBranches.size(), (long) unsatisfiedRows[0]);
                }
            });

            // 4. 事务提交后：拉取模型侧 Excel 存为方案快照，供下载按 recordId 取用（防串档）
            saveExcelSnapshot(recordId);
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /**
     * 计算完成快照：模型侧 save_excel 输出全局单文件，完成瞬间文件必为本方案输出，
     * 立即拉取并按 recordId 存档。失败仅告警，下载时降级实时代理。
     */
    private void saveExcelSnapshot(String recordId) {
        try {
            Path dir = Paths.get(excelDir);
            Files.createDirectories(dir);
            byte[] bytes = modelClient.downloadBytes(ModelClient.PATH_DECISION + "/download");
            Files.write(dir.resolve(recordId + ".xlsx"), bytes);
            log.info("决策 Excel 快照已保存：recordId={}", recordId);
        } catch (Exception e) {
            log.warn("决策 Excel 快照保存失败（下载时降级实时代理）：recordId={}, error={}",
                    recordId, e.getMessage());
        }
    }

    /**
     * 读取方案 Excel 快照；不存在（历史方案/快照失败）返回 null，由调用方降级实时代理。
     */
    public byte[] readExcelSnapshot(String recordId) {
        Path file = Paths.get(excelDir, recordId + ".xlsx");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("读取决策 Excel 快照失败：recordId={}, error={}", recordId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除方案 Excel 快照（方案删除时清理磁盘，避免无用文件占用），失败仅告警。
     */
    public void deleteExcelSnapshot(String recordId) {
        try {
            Files.deleteIfExists(Paths.get(excelDir, recordId + ".xlsx"));
        } catch (IOException e) {
            log.warn("删除决策 Excel 快照失败：recordId={}, error={}", recordId, e.getMessage());
        }
    }

    /** 数组首元素（键名核对日志用）；非数组返回节点本身 */
    private static JsonNode firstItem(JsonNode response, String field) {
        JsonNode node = response.path(field);
        if (node.isArray() && node.size() > 0) {
            return node.get(0);
        }
        return node;
    }

    /** 扩展表落库：数组逐元素一行（rowNameField 取行名，取不到用序号）；对象整体单行（row_key=总计） */
    private void writeExtraTable(String recordId, JsonNode node, String tableKey, String rowNameField,
                                 LocalDateTime now) {
        if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                index++;
                String rowKey = rowNameField != null ? textOf(item, rowNameField) : null;
                if (rowKey == null || rowKey.trim().isEmpty()) {
                    rowKey = String.valueOf(index);
                }
                insertExtraRow(recordId, tableKey, rowKey, item, index, now);
            }
        } else if (node.isObject() && node.size() > 0) {
            insertExtraRow(recordId, tableKey, "总计", node, 1, now);
        }
    }

    /** 单行快照写入：row_json 时间列（旬/月）按时间顺序重排后归档（模型返回键序乱序：10月在5月前） */
    private void insertExtraRow(String recordId, String tableKey, String rowKey, JsonNode rowJson,
                                int sortOrder, LocalDateTime now) {
        DecisionExtraTable row = new DecisionExtraTable();
        row.setRecordId(recordId);
        row.setTableKey(tableKey);
        row.setRowKey(rowKey);
        try {
            row.setRowJson(objectMapper.writeValueAsString(reorderTimeColumns(rowJson)));
        } catch (Exception e) {
            log.warn("扩展表行序列化失败，跳过：recordId={}, tableKey={}, rowKey={}, error={}",
                    recordId, tableKey, rowKey, e.getMessage());
            return;
        }
        row.setSortOrder((double) sortOrder);
        fillBase(row, now);
        extraTableMapper.insert(row);
    }

    /**
     * 时间列重排：字段名可映射为旬序（TenDayMapUtils）或月序（N月）的列按时间升序排到末尾，
     * 其余字段保持模型原序在前（行标识/合计列等）；无时间列的节点原样返回。
     */
    private JsonNode reorderTimeColumns(JsonNode rowJson) {
        if (!(rowJson instanceof ObjectNode) || rowJson.size() <= 1) {
            return rowJson;
        }
        ObjectNode source = (ObjectNode) rowJson;
        ObjectNode result = objectMapper.createObjectNode();
        List<Map.Entry<String, JsonNode>> timeFields = new ArrayList<>();
        source.fields().forEachRemaining(entry -> {
            if (timeOrderOf(entry.getKey()) != null) {
                timeFields.add(entry);
            } else {
                result.set(entry.getKey(), entry.getValue());
            }
        });
        timeFields.sort(Comparator.comparingInt(entry -> timeOrderOf(entry.getKey())));
        for (Map.Entry<String, JsonNode> entry : timeFields) {
            result.set(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** 字段名时间序：旬标签→旬序（1~18）；「N月」→月份序（1~12）；其他返回 null */
    private Integer timeOrderOf(String field) {
        Integer order = TenDayMapUtils.sortOrderOf(field);
        if (order != null) {
            return order;
        }
        if (field != null && field.endsWith("月")) {
            try {
                return Integer.parseInt(field.substring(0, field.length() - 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 审计五连写（委托 AuditUtils） */
    private void fillBase(BaseWaterEntity entity, LocalDateTime now) {
        AuditUtils.fillCreate(entity, now, createdBy, corpCode);
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        DecisionRecord record = new DecisionRecord();
        record.setId(recordId);
        record.setStatus(ModelRecordCommonService.STATUS_FAILED);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("配水调度失败：recordId={}, error={}", recordId, errorMsg);
    }
}
