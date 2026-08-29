package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.AllocateRecord;
import com.qgyun.hltgq.hltgqsite.entity.AllocateTenday;
import com.qgyun.hltgq.hltgqsite.entity.DemandRecord;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictRecord;
import com.qgyun.hltgq.hltgqsite.entity.LossRecord;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateTendayMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayMapUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.AllocateSubmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 水资源配置服务。
 * <p>auto 模式：参数从所选方案提取（scenario←中长期、保证率/渠系系数←需水），
 * 调 /allocate(mode=auto, save_excel=true)；manual 模式：rows 直传。
 * <p>结果旬尺度 → allocate_tenday（20 字段 + sort_order=FULL_TEN_DAY_MAP），
 * total_inflow/total_demand/total_supply/spill 对 tenday 求和、deficit=MAX(需-供,0) 回写。
 * <p>事务边界：模型调用（/allocate）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
 */
@Service
public class AllocateService {

    private static final Logger log = LoggerFactory.getLogger(AllocateService.class);

    private final AllocateRecordMapper recordMapper;
    private final AllocateTendayMapper tendayMapper;
    private final LongPredictRecordMapper longRecordMapper;
    private final DemandRecordMapper demandRecordMapper;
    private final LossRecordMapper lossRecordMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public AllocateService(AllocateRecordMapper recordMapper,
                           AllocateTendayMapper tendayMapper,
                           LongPredictRecordMapper longRecordMapper,
                           DemandRecordMapper demandRecordMapper,
                           LossRecordMapper lossRecordMapper,
                           ModelClient modelClient,
                           ModelTaskExecutor taskExecutor,
                           TransactionTemplate transactionTemplate,
                           ObjectMapper objectMapper,
                           @Value("${hltgq.corp-code}") String corpCode,
                           @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.tendayMapper = tendayMapper;
        this.longRecordMapper = longRecordMapper;
        this.demandRecordMapper = demandRecordMapper;
        this.lossRecordMapper = lossRecordMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /**
     * 提交水资源配置（秒回 recordId），异步执行模型计算。
     */
    public String submit(AllocateSubmitRequest req) {
        if (req == null || req.getMode() == null || req.getMode().trim().isEmpty()) {
            throw new IllegalArgumentException("模式不能为空（auto / manual）");
        }
        String mode = req.getMode().trim();
        if (!"auto".equals(mode) && !"manual".equals(mode)) {
            throw new IllegalArgumentException("模式必须是 auto / manual 之一");
        }
        double startLevel = req.getStartLevel() == null ? 75.0 : req.getStartLevel();
        double floodLimitLevel = req.getFloodLimitLevel() == null ? 80.0 : req.getFloodLimitLevel();
        double maxLevel = req.getMaxLevel() == null ? 82.8 : req.getMaxLevel();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("save_excel", true);
        body.put("start_level", startLevel);
        body.put("flood_limit_level", floodLimitLevel);
        body.put("max_level", maxLevel);

        // 参数留存字段 + auto 模式参数提取
        AllocateRecord record = new AllocateRecord();
        record.setMode(mode);
        record.setStartLevel(startLevel);
        record.setFloodLimitLevel(floodLimitLevel);
        record.setMaxLevel(maxLevel);
        String demandRecordId = null;
        if ("auto".equals(mode)) {
            demandRecordId = loadAutoParams(req, body, record);
        } else {
            body.put("use_manual_spill", Boolean.TRUE.equals(req.getUseManualSpill()));
            if (req.getRows() == null || req.getRows().isEmpty()) {
                throw new IllegalArgumentException("manual 模式下配水数据行不能为空（或使用 /water-allocation/upload 上传模板）");
            }
            // 空串数值 → null，避免模型端 "could not convert string to float: ''"（upload 解析已置 null，JSON 直传兜底）
            List<Map<String, Object>> cleanedRows = new ArrayList<>();
            for (Map<String, Object> row : req.getRows()) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    Object v = entry.getValue();
                    item.put(entry.getKey(), (v instanceof String && ((String) v).trim().isEmpty()) ? null : v);
                }
                cleanedRows.add(item);
            }
            body.put("rows", cleanedRows);
        }

        // 写主表（参数留存 + 请求归档，calculating）
        record.setSchemeName(buildSchemeName(req, mode));
        record.setStatus("calculating");
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setDemandRecordId(demandRecordId);
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy);
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        recordMapper.insert(record);

        String recordId = record.getId();
        taskExecutor.submit(() -> execute(recordId, body));
        log.info("水资源配置任务已提交：recordId={}, mode={}", recordId, mode);
        return recordId;
    }

    /** auto 模式：提取 scenario（中长期方案）、guarantee_rate/canal_eff（需水方案），损失方案归档追溯 */
    private String loadAutoParams(AllocateSubmitRequest req, Map<String, Object> body, AllocateRecord record) {
        if (req.getLongPredictRecordId() == null || req.getLongPredictRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("auto 模式下中长期来水方案不能为空");
        }
        if (req.getDemandRecordId() == null || req.getDemandRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("auto 模式下需水预测方案不能为空");
        }
        QueryWrapper<LongPredictRecord> midWrapper = new QueryWrapper<>();
        midWrapper.eq("\"id\"", req.getLongPredictRecordId().trim())
                .eq("\"del_flag\"", BoolTextUtils.FALSE);
        LongPredictRecord midRecord = longRecordMapper.selectOne(midWrapper);
        if (midRecord == null) {
            throw new IllegalArgumentException("中长期来水方案不存在或已删除：" + req.getLongPredictRecordId());
        }
        QueryWrapper<DemandRecord> demandWrapper = new QueryWrapper<>();
        demandWrapper.eq("\"id\"", req.getDemandRecordId().trim())
                .eq("\"del_flag\"", BoolTextUtils.FALSE);
        DemandRecord demandRecord = demandRecordMapper.selectOne(demandWrapper);
        if (demandRecord == null) {
            throw new IllegalArgumentException("需水预测方案不存在或已删除：" + req.getDemandRecordId());
        }
        if (req.getLossRecordId() != null && !req.getLossRecordId().trim().isEmpty()) {
            // 仅校验所选损失方案存在性；/allocate 契约不含该参数（auto 模式内部已含蒸发预测），不传模型
            QueryWrapper<LossRecord> lossWrapper = new QueryWrapper<>();
            lossWrapper.eq("\"id\"", req.getLossRecordId().trim())
                    .eq("\"del_flag\"", BoolTextUtils.FALSE);
            LossRecord lossRecord = lossRecordMapper.selectOne(lossWrapper);
            if (lossRecord == null) {
                throw new IllegalArgumentException("水量损失方案不存在或已删除：" + req.getLossRecordId());
            }
        }
        String pondOption = req.getPondOption() == null || req.getPondOption().trim().isEmpty()
                ? "多年平均" : req.getPondOption().trim();
        // /allocate 契约：塘坝可供水量档位只能是 50% / 75% / 90% / 多年平均
        if (!"50%".equals(pondOption) && !"75%".equals(pondOption) && !"90%".equals(pondOption)
                && !"多年平均".equals(pondOption)) {
            throw new IllegalArgumentException("塘坝可供水量档位必须是 50% / 75% / 90% / 多年平均 之一");
        }
        // 中长期方案 scenario 入库可能为空白串（"原始"方案提交时写 null，被库默认值落成 ''）；
        // 且 /predict 对"原始"方案会回显 "历史<年份>年" 并回写记录（如"历史1957年"），
        // 语义等价于"原始"。两者均规范化为 null 再传模型（/allocate 契约：scenario 必须是 丰/平/枯 或 null）
        String scenario = midRecord.getScenario() == null || midRecord.getScenario().trim().isEmpty()
                ? null : midRecord.getScenario().trim();
        if (scenario != null && !"丰".equals(scenario) && !"平".equals(scenario) && !"枯".equals(scenario)) {
            log.warn("中长期方案 {} 的 scenario=[{}] 非丰/平/枯，按原始(null)处理",
                    req.getLongPredictRecordId(), scenario);
            scenario = null;
        }
        body.put("scenario", scenario);
        body.put("guarantee_rate", demandRecord.getGuaranteeRate());
        body.put("canal_eff", demandRecord.getCanalEff());
        body.put("pond_option", pondOption);
        record.setScenario(scenario);
        record.setGuaranteeRate(demandRecord.getGuaranteeRate());
        record.setCanalEff(demandRecord.getCanalEff());
        record.setPondOption(pondOption);
        return demandRecord.getId();
    }

    private String buildSchemeName(AllocateSubmitRequest req, String mode) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "水资源配置_" + ("auto".equals(mode) ? "自动" : "手动");
    }

    /**
     * 异步任务：调模型 → 事务内写旬尺度明细+回写汇总 → 更新状态。
     * <p>事务边界：模型调用（/allocate）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> body) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_ALLOCATE, body);

            // 事务内：写旬尺度明细（20 字段 + sort_order）+ 汇总回写 + completed
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();
                    double totalInflow = 0;
                    double totalDemand = 0;
                    double totalSupply = 0;
                    double spillTotal = 0;
                    for (JsonNode item : response.path("旬尺度")) {
                        AllocateTenday tenday = new AllocateTenday();
                        tenday.setRecordId(recordId);
                        tenday.setTendayLabel(textOf(item, "日期"));
                        tenday.setBpInflow(doubleOf(item, "BP预测来水量（万方）"));
                        tenday.setEvaporation(doubleOf(item, "库面蒸发数据（万方）"));
                        tenday.setIrrigationDemand(doubleOf(item, "灌溉需水"));
                        tenday.setUrbanDemand(doubleOf(item, "城镇需水"));
                        tenday.setRuralDemand(doubleOf(item, "农村生活需水量"));
                        tenday.setEcoDemand(doubleOf(item, "河道生态需水总量"));
                        tenday.setTotalDemand(doubleOf(item, "总需水量（万方）"));
                        tenday.setDemandGtSupply(doubleOf(item, "需>供"));
                        tenday.setSupplyGtDemand(doubleOf(item, "供>需"));
                        tenday.setDiffInflowDemand(doubleOf(item, "差值（来水-需水）"));
                        tenday.setSpill(doubleOf(item, "弃水"));
                        tenday.setDischargePlusSpill(doubleOf(item, "下泄+弃水"));
                        tenday.setEndStorage(doubleOf(item, "月末库容"));
                        tenday.setEndWaterLevel(doubleOf(item, "月末水位"));
                        tenday.setPondSupply(doubleOf(item, "塘坝可供水量（万方）"));
                        tenday.setWaterworksSupply(doubleOf(item, "水厂供水"));
                        tenday.setReservoirIrrigation(doubleOf(item, "花凉亭水库灌溉供水"));
                        tenday.setTotalSupply(doubleOf(item, "总供水"));
                        tenday.setIsSatisfied(BoolTextUtils.normalize(textOf(item, "是否满足需水")));
                        Integer order = TenDayMapUtils.fullSortOrderOf(tenday.getTendayLabel());
                        tenday.setSortOrder(order == null ? null : order.doubleValue());
                        tenday.setCorpCode(corpCode);
                        tenday.setCreatedAt(now);
                        tenday.setCreatedBy(createdBy);
                        tenday.setUpdatedAt(now);
                        tenday.setUpdatedBy(createdBy);
                        tendayMapper.insert(tenday);
                        if (tenday.getBpInflow() != null) {
                            totalInflow += tenday.getBpInflow();
                        }
                        if (tenday.getTotalDemand() != null) {
                            totalDemand += tenday.getTotalDemand();
                        }
                        if (tenday.getTotalSupply() != null) {
                            totalSupply += tenday.getTotalSupply();
                        }
                        if (tenday.getSpill() != null) {
                            spillTotal += tenday.getSpill();
                        }
                    }

                    // 回写汇总 + completed
                    AllocateRecord record = new AllocateRecord();
                    record.setId(recordId);
                    record.setTotalInflow(round2(totalInflow));
                    record.setTotalDemand(round2(totalDemand));
                    record.setTotalSupply(round2(totalSupply));
                    record.setDeficit(round2(Math.max(totalDemand - totalSupply, 0)));
                    record.setSpill(round2(spillTotal));
                    record.setStatus("completed");
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("水资源配置完成：recordId={}", recordId);
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        AllocateRecord record = new AllocateRecord();
        record.setId(recordId);
        record.setStatus("failed");
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("水资源配置失败：recordId={}, error={}", recordId, errorMsg);
    }
}
