package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.AllocateRecord;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import com.qgyun.hltgq.hltgqsite.entity.DecisionBranchDetail;
import com.qgyun.hltgq.hltgqsite.entity.DecisionRecord;
import com.qgyun.hltgq.hltgqsite.entity.DecisionScaleFactor;
import com.qgyun.hltgq.hltgqsite.entity.DemandRecord;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionBranchDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionScaleFactorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 配水调度服务。
 * <p>入参需水方案+配置方案（必传）：source=配置方案 mode、canal_eff=需水方案 canal_eff，
 * 调 /decision(tens=null 全 18 旬, save_excel=true)。
 * <p>缩放系数 → scale_factor 表、支渠明细（按旬字典）展开 → branch_detail 表；
 * total_demand/total_supply 求和、unsatisfied_count 按支渠去重统计 is_satisfied=#2# 回写。
 * <p>事务边界：模型调用（/decision）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
 * <p>防串档：模型侧 save_excel 输出全局单文件，计算完成瞬间立即拉取并按 recordId 存档快照，下载按 recordId 取用。
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final DecisionRecordMapper recordMapper;
    private final DecisionScaleFactorMapper scaleFactorMapper;
    private final DecisionBranchDetailMapper branchDetailMapper;
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
     */
    public String submit(DecisionSubmitRequest req) {
        if (req == null || req.getDemandRecordId() == null || req.getDemandRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("需水预测方案不能为空");
        }
        if (req.getAllocateRecordId() == null || req.getAllocateRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("水资源配置方案不能为空");
        }
        QueryWrapper<DemandRecord> demandWrapper = new QueryWrapper<>();
        demandWrapper.eq("\"id\"", req.getDemandRecordId().trim())
                .eq("\"del_flag\"", BoolTextUtils.FALSE);
        DemandRecord demandRecord = demandRecordMapper.selectOne(demandWrapper);
        if (demandRecord == null) {
            throw new IllegalArgumentException("需水预测方案不存在或已删除：" + req.getDemandRecordId());
        }
        QueryWrapper<AllocateRecord> allocateWrapper = new QueryWrapper<>();
        allocateWrapper.eq("\"id\"", req.getAllocateRecordId().trim())
                .eq("\"del_flag\"", BoolTextUtils.FALSE);
        AllocateRecord allocateRecord = allocateRecordMapper.selectOne(allocateWrapper);
        if (allocateRecord == null) {
            throw new IllegalArgumentException("水资源配置方案不存在或已删除：" + req.getAllocateRecordId());
        }
        String source = allocateRecord.getMode() == null || allocateRecord.getMode().trim().isEmpty()
                ? "auto" : allocateRecord.getMode().trim();
        Double canalEff = demandRecord.getCanalEff();

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
        record.setStatus("calculating");
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setCanalEff(canalEff);
        record.setSource(source);
        record.setAllocateRecordId(allocateRecord.getId());
        record.setDemandRecordId(demandRecord.getId());
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
            record.setTens(req.getTens() == null || req.getTens().isEmpty()
                    ? null : objectMapper.writeValueAsString(req.getTens()));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy);
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

                    // 2. 支渠明细：对象 {旬标签: [支渠行...]}，纵向展开
                    double[] totals = new double[2];
                    Set<String> unsatisfiedBranches = new HashSet<>();
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
                                detail.setArea(doubleOf(item, "灌区面积"));
                                detail.setCropTypes(textOf(item, "种植作物种类"));
                                detail.setDemandVolume(doubleOf(item, "需水量"));
                                detail.setNetDemand(doubleOf(item, "净需水量"));
                                detail.setSuggestedSupply(doubleOf(item, "建议供水量"));
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
                                }
                            }
                        });
                    }

                    // 3. 回写汇总 + completed
                    DecisionRecord record = new DecisionRecord();
                    record.setId(recordId);
                    record.setTotalDemand(round2(totals[0]));
                    record.setTotalSupply(round2(totals[1]));
                    record.setUnsatisfiedCount((double) unsatisfiedBranches.size());
                    record.setStatus("completed");
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                    log.info("配水调度完成：recordId={}, 不满足支渠{}条", recordId, unsatisfiedBranches.size());
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

    private void fillBase(BaseWaterEntity entity, LocalDateTime now) {
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(createdBy);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(createdBy);
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        DecisionRecord record = new DecisionRecord();
        record.setId(recordId);
        record.setStatus("failed");
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("配水调度失败：recordId={}, error={}", recordId, errorMsg);
    }
}
