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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOfAny;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOfAny;

/**
 * 水资源配置服务。
 * <p>2026-10-26 会议定稿：manual 模式与大表上传已删除，mode 恒 auto；
 * 参数从所选方案提取（scenario←中长期、保证率/渠系系数←需水），调 /allocate(mode=auto, save_excel=true)。
 * <p>结果旬尺度 → allocate_tenday（20 字段 + sort_order=FULL_TEN_DAY_MAP），
 * total_inflow/total_demand/total_supply/spill 对 tenday 求和、deficit=MAX(需-供,0) 回写。
 * <p>V3 两视图：响应 灌区水资源配置/水库逐旬清单/水库月汇总/水库年汇总/diagnostics/口径变更
 * 原样快照进 views_json，详情时组装 irrigation/reservoir 两个一级视图。
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
    private final String excelDir;

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
                           @Value("${hltgq.created-by}") String createdBy,
                           @Value("${model.allocate-excel-dir:./allocate-excel}") String excelDir) {
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
        this.excelDir = excelDir;
    }

    /**
     * 提交水资源配置（秒回 recordId），异步执行模型计算。
     * <p>2026-10-26 定稿：mode 后端恒 auto，前端不再传；字段名 initialLevel/spillwayOpenLevel/pondScenario。
     */
    public String submit(AllocateSubmitRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        // 旧字段名不静默忽略：用户改起始水位不生效且页面显示成功的问题必须显式报错
        if (req.hasLegacyField()) {
            throw new IllegalArgumentException(
                    "字段名已变更：startLevel→initialLevel、maxLevel→spillwayOpenLevel、pondOption→pondScenario，"
                            + "请按新字段名提交");
        }
        double initialLevel = req.getInitialLevel() == null ? 75.0 : req.getInitialLevel();
        double floodLimitLevel = req.getFloodLimitLevel() == null ? 80.0 : req.getFloodLimitLevel();
        double spillwayOpenLevel = req.getSpillwayOpenLevel() == null ? 82.8 : req.getSpillwayOpenLevel();
        // 2026-10 模型方答复：三水位公共参数计算边界（其他参数保持默认时），
        // 非法水位组合模型侧返回 HTTP 400/code:1 且不自动截断，后端前置 400 拒绝
        requireInRange("起始水位", initialLevel, 74, 99);
        requireInRange("汛限水位", floodLimitLevel, 75, 81);
        requireInRange("溢洪道开启水位", spillwayOpenLevel, 81, 99);
        // 其余 12 个 parameters 透传参数仅做非负校验（范围模型方未给出；类型由 JSON 反序列化保证，非数值直接 400）
        requireNonNegativeParams(req);
        // 2026-10 模型方书面答复 C2：16 参数交叉校验（缺失参数以默认值代入，规则见方法注释）
        requireCrossChecks(req, initialLevel, floodLimitLevel, spillwayOpenLevel);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "auto");
        body.put("save_excel", true);
        body.put("start_level", initialLevel);
        body.put("flood_limit_level", floodLimitLevel);
        body.put("max_level", spillwayOpenLevel);
        // 12 个规划参数经模型 parameters 对象透传（覆盖规划参数.json 同名键，不修改原文件）；
        // floodStartDate 只读固定 2027-05-01（与 year=2027 对应），用户改传也以此为准
        Map<String, Object> parameters = new LinkedHashMap<>();
        putParam(parameters, "flood_control_target_level", req.getFloodControlTargetLevel());
        putParam(parameters, "dead_water_level", req.getDeadWaterLevel());
        putParam(parameters, "year_end_target_level", req.getYearEndTargetLevel());
        parameters.put("flood_start_date", "2027-05-01");
        putParam(parameters, "generation_min_level", req.getGenerationMinLevel());
        putParam(parameters, "generation_max_level", req.getGenerationMaxLevel());
        putParam(parameters, "base_generation_m3s", req.getBaseGenerationM3s());
        putParam(parameters, "generation_nameplate_m3s", req.getGenerationNameplateM3s());
        putParam(parameters, "urban_annual_demand", req.getUrbanAnnualDemand());
        putParam(parameters, "rural_annual_demand", req.getRuralAnnualDemand());
        putParam(parameters, "water_plant_annual_supply", req.getWaterPlantAnnualSupply());
        putParam(parameters, "city_supply_annual_wan_m3", req.getCitySupplyAnnualWanM3());
        if (!parameters.isEmpty()) {
            body.put("parameters", parameters);
        }

        // 参数留存字段 + auto 模式参数提取
        AllocateRecord record = new AllocateRecord();
        record.setMode("auto");
        record.setInitialLevel(initialLevel);
        record.setFloodLimitLevel(floodLimitLevel);
        record.setSpillwayOpenLevel(spillwayOpenLevel);
        String demandRecordId = loadAutoParams(req, body, record);
        record.setSchemeName(buildSchemeName(req));
        record.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setDemandRecordId(demandRecordId);
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(ModelRecordCommonService.operatorId(createdBy));
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
        log.info("水资源配置任务已提交：recordId={}", recordId);
        return recordId;
    }

    /**
     * 水位公共参数计算边界校验（2026-10 模型方答复，其他参数保持默认时）。
     * <p>显式传值超出边界即拒绝；模型侧对非法水位组合不自动截断、返回 code:1，后端前置拦截避免无效计算。
     */
    private void requireInRange(String name, double value, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + "必须在 " + min + " ~ " + max + " 之间，当前值：" + value);
        }
    }

    /** 12 个 parameters 透传参数的非负校验（null 跳过，使用模型默认） */
    private void requireNonNegativeParams(AllocateSubmitRequest req) {
        checkNonNegative("泄洪隧洞启用水位", req.getFloodControlTargetLevel());
        checkNonNegative("死水位", req.getDeadWaterLevel());
        checkNonNegative("计划年末蓄水位", req.getYearEndTargetLevel());
        checkNonNegative("发电最低水位", req.getGenerationMinLevel());
        checkNonNegative("发电适用水位上界", req.getGenerationMaxLevel());
        checkNonNegative("常规发电基准流量", req.getBaseGenerationM3s());
        checkNonNegative("最大发电流量", req.getGenerationNameplateM3s());
        checkNonNegative("城镇需水量", req.getUrbanAnnualDemand());
        checkNonNegative("农村生活需水量", req.getRuralAnnualDemand());
        checkNonNegative("水厂可供水量", req.getWaterPlantAnnualSupply());
        checkNonNegative("城市供水量", req.getCitySupplyAnnualWanM3());
    }

    private void checkNonNegative(String name, Double value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + "不能为负数，当前值：" + value);
        }
    }

    /**
     * 16 参数交叉校验（2026-10 模型方书面答复 C2 规则，缺失参数以默认值代入）：
     * <ul>
     * <li>44 ≤ 死水位 ≤ 发电最低水位 ≤ 发电适用水位上界 ≤ 99</li>
     * <li>死水位 ≤ 起始水位 ≤ 99；死水位 ≤ 计划年末蓄水位 ≤ 99</li>
     * <li>发电最低水位 ≤ 汛限水位 ≤ 泄洪隧洞启用水位 ≤ 溢洪道开启水位 ≤ 99</li>
     * <li>常规发电基准流量 &gt; 0 且 ≤ 最大发电流量</li>
     * </ul>
     * 无 initial ≤ flood_limit 强制约束；供水四项仅非负（模型方未给上限）。
     */
    private void requireCrossChecks(AllocateSubmitRequest req, double initialLevel,
                                    double floodLimitLevel, double spillwayOpenLevel) {
        double dead = req.getDeadWaterLevel() == null ? 74.0 : req.getDeadWaterLevel();
        double genMin = req.getGenerationMinLevel() == null ? 75.0 : req.getGenerationMinLevel();
        double genMax = req.getGenerationMaxLevel() == null ? 90.0 : req.getGenerationMaxLevel();
        double yearEndTarget = req.getYearEndTargetLevel() == null ? 77.0 : req.getYearEndTargetLevel();
        double floodControlTarget = req.getFloodControlTargetLevel() == null ? 81.0 : req.getFloodControlTargetLevel();
        // 44 ≤ 死水位 ≤ 发电最低水位 ≤ 发电适用水位上界 ≤ 99
        requireInRange("死水位", dead, 44, 99);
        requireLe("死水位", dead, "发电最低水位", genMin);
        requireLe("发电最低水位", genMin, "发电适用水位上界", genMax);
        requireInRange("发电适用水位上界", genMax, 44, 99);
        // 死水位 ≤ 起始水位 ≤ 99（起始水位上界已由公共边界校验保证）
        requireLe("死水位", dead, "起始水位", initialLevel);
        // 死水位 ≤ 计划年末蓄水位 ≤ 99
        requireLe("死水位", dead, "计划年末蓄水位", yearEndTarget);
        requireInRange("计划年末蓄水位", yearEndTarget, 44, 99);
        // 发电最低水位 ≤ 汛限水位 ≤ 泄洪隧洞启用水位 ≤ 溢洪道开启水位 ≤ 99（上界已由公共边界校验保证）
        requireLe("发电最低水位", genMin, "汛限水位", floodLimitLevel);
        requireLe("汛限水位", floodLimitLevel, "泄洪隧洞启用水位", floodControlTarget);
        requireLe("泄洪隧洞启用水位", floodControlTarget, "溢洪道开启水位", spillwayOpenLevel);
        // 常规发电基准流量 > 0 且 ≤ 最大发电流量
        double baseGen = req.getBaseGenerationM3s() == null ? 30.0 : req.getBaseGenerationM3s();
        double nameplate = req.getGenerationNameplateM3s() == null ? 120.0 : req.getGenerationNameplateM3s();
        if (baseGen <= 0) {
            throw new IllegalArgumentException("常规发电基准流量必须大于 0，当前值：" + baseGen);
        }
        requireLe("常规发电基准流量", baseGen, "最大发电流量", nameplate);
    }

    /** 交叉约束校验：左值不得大于右值 */
    private void requireLe(String leftName, double left, String rightName, double right) {
        if (left > right) {
            throw new IllegalArgumentException(
                    leftName + "(" + left + ") 不能大于 " + rightName + "(" + right + ")");
        }
    }

    /** 参数非空才放入 parameters 透传（null 不覆盖，模型沿用规划参数.json 默认） */
    private void putParam(Map<String, Object> parameters, String key, Double value) {
        if (value != null) {
            parameters.put(key, value);
        }
    }

    /** auto 模式：提取 scenario（中长期方案）、guarantee_rate/canal_eff（需水方案）、
     *  annual_leakage_wan_m3（损失方案年渗漏量，/allocate 4.6.2 契约参数） */
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
            // 校验存在性 + 提取年渗漏量传 /allocate（手册 4.6.2：annual_leakage_wan_m3，默认 156，逐旬=/36），
            // 让所选损失方案的渗漏总量真正生效；旧方案该列为 null 时不传（模型默认 156）
            QueryWrapper<LossRecord> lossWrapper = new QueryWrapper<>();
            lossWrapper.eq("\"id\"", req.getLossRecordId().trim())
                    .eq("\"del_flag\"", BoolTextUtils.FALSE);
            LossRecord lossRecord = lossRecordMapper.selectOne(lossWrapper);
            if (lossRecord == null) {
                throw new IllegalArgumentException("水量损失方案不存在或已删除：" + req.getLossRecordId());
            }
            record.setLossRecordId(req.getLossRecordId().trim());
            Double annualLeakage = lossRecord.getAnnualLeakageWanM3();
            if (annualLeakage != null) {
                body.put("annual_leakage_wan_m3", annualLeakage);
            }
        }
        String pondScenario = req.getPondScenario() == null || req.getPondScenario().trim().isEmpty()
                ? "多年平均" : req.getPondScenario().trim();
        // /allocate 契约：塘坝可供水量档位只能是 50% / 75% / 90% / 多年平均
        if (!"50%".equals(pondScenario) && !"75%".equals(pondScenario) && !"90%".equals(pondScenario)
                && !"多年平均".equals(pondScenario)) {
            throw new IllegalArgumentException("塘坝可供水量情景必须是 50% / 75% / 90% / 多年平均 之一");
        }
        // 中长期方案 scenario 入库可能为空白串（"原始"方案提交时写 null，被库默认值落成 ''）；
        // 且 /predict 对"原始"方案会回显 "历史<年份>年" 并回写记录（如"历史1957年"），
        // 语义等价于"原始"。两者均规范化为 null 再传模型
        // （/allocate 契约：scenario 必须是 丰/平/枯/推荐值 或 null）
        String scenario = midRecord.getScenario() == null || midRecord.getScenario().trim().isEmpty()
                ? null : midRecord.getScenario().trim();
        if (scenario != null && !"丰".equals(scenario) && !"平".equals(scenario)
                && !"枯".equals(scenario) && !"推荐值".equals(scenario)) {
            log.warn("中长期方案 {} 的 scenario=[{}] 非丰/平/枯/推荐值，按原始(null)处理",
                    req.getLongPredictRecordId(), scenario);
            scenario = null;
        }
        body.put("scenario", scenario);
        body.put("guarantee_rate", demandRecord.getGuaranteeRate());
        body.put("canal_eff", demandRecord.getCanalEff());
        body.put("pond_option", pondScenario);
        record.setScenario(scenario);
        record.setGuaranteeRate(demandRecord.getGuaranteeRate());
        record.setCanalEff(demandRecord.getCanalEff());
        record.setPondScenario(pondScenario);
        return demandRecord.getId();
    }

    private String buildSchemeName(AllocateSubmitRequest req) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "水资源配置_自动";
    }

    /**
     * 异步任务：调模型 → 事务内写旬尺度明细+回写汇总 → 更新状态。
     * <p>事务边界：模型调用（/allocate）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> body) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_ALLOCATE, body);

            // V3 两视图原始响应快照（事务外序列化，避免大 JSON 占用事务时间）
            final String viewsJson = snapshotViews(response);

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
                    record.setViewsJson(viewsJson);
                    record.setStatus(ModelRecordCommonService.STATUS_COMPLETED);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("水资源配置完成：recordId={}", recordId);
            // 事务提交后：拉取模型侧结果 Excel 存为方案快照，供下载按 recordId 取用（防串档）
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
            byte[] bytes = modelClient.downloadBytes(ModelClient.PATH_ALLOCATE + "/download");
            Files.write(dir.resolve(recordId + ".xlsx"), bytes);
            log.info("配置 Excel 快照已保存：recordId={}", recordId);
        } catch (Exception e) {
            log.warn("配置 Excel 快照保存失败（下载时降级实时代理）：recordId={}, error={}",
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
            log.warn("读取配置 Excel 快照失败：recordId={}, error={}", recordId, e.getMessage());
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
            log.warn("删除配置 Excel 快照失败：recordId={}, error={}", recordId, e.getMessage());
        }
    }

    /**
     * V3 两视图原始响应快照：采集会议定稿落库的键（年汇总/灌区水资源配置/水库逐旬清单/水库月汇总/水库年汇总），
     * 附 diagnostics 与 口径变更 供详情核对展示；缺失键不入快照。序列化失败仅告警（快照为空，详情降级旧 tendays）。
     */
    private String snapshotViews(JsonNode response) {
        Map<String, Object> views = new LinkedHashMap<>();
        putIfPresent(views, response, "年汇总");
        putIfPresent(views, response, "灌区水资源配置");
        putIfPresent(views, response, "水库逐旬清单");
        putIfPresent(views, response, "水库月汇总");
        putIfPresent(views, response, "水库年汇总");
        putIfPresent(views, response, "diagnostics");
        putIfPresent(views, response, "口径变更");
        if (views.isEmpty()) {
            log.warn("水资源配置响应无 V3 两视图键：model_version={}", textOf(response, "model_version"));
            return null;
        }
        try {
            return objectMapper.writeValueAsString(views);
        } catch (Exception e) {
            log.warn("两视图快照序列化失败：{}", e.getMessage());
            return null;
        }
    }

    private static void putIfPresent(Map<String, Object> target, JsonNode source, String key) {
        JsonNode node = source.get(key);
        if (node != null && !node.isNull() && !node.isMissingNode()) {
            target.put(key, node);
        }
    }

    /**
     * 组装详情两视图（irrigation / reservoir）。
     * <p>键名按《模型优化会议定稿》目标契约解析：手册仅给表级概览、无逐列定义，
     * 候选键名 + 实际键名核对日志（联调后以日志为准收敛）。返回结构：
     * <pre>
     * {
     *   "irrigation": {"annualSummary": {...}, "process": [...]},
     *   "reservoir": {"annualSummary": {...}, "tendayDetails": [...]},
     *   "viewsRaw": {...原始快照...}
     * }
     * </pre>
     */
    public Map<String, Object> buildViews(AllocateRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (record.getViewsJson() == null || record.getViewsJson().trim().isEmpty()) {
            return result;
        }
        JsonNode views;
        try {
            views = objectMapper.readTree(record.getViewsJson());
        } catch (Exception e) {
            log.warn("两视图快照解析失败：recordId={}, error={}", record.getId(), e.getMessage());
            return result;
        }
        // 原始快照一并返回，供联调核对键名（前端正常渲染取 irrigation/reservoir）
        result.put("viewsRaw", views);

        Map<String, Object> irrigation = new LinkedHashMap<>();
        irrigation.put("annualSummary", buildIrrigationAnnual(views));
        irrigation.put("process", buildIrrigationProcess(views));
        result.put("irrigation", irrigation);

        Map<String, Object> reservoir = new LinkedHashMap<>();
        reservoir.put("annualSummary", buildReservoirAnnual(views));
        reservoir.put("tendayDetails", buildReservoirTendays(views));
        reservoir.put("monthlySummaries", buildReservoirMonthly(views));
        result.put("reservoir", reservoir);

        JsonNode diagnostics = views.get("diagnostics");
        if (diagnostics != null && !diagnostics.isNull()) {
            result.put("diagnostics", diagnostics);
        }
        JsonNode calibreChanges = views.get("口径变更");
        if (calibreChanges != null && !calibreChanges.isNull()) {
            result.put("calibreChanges", calibreChanges);
        }
        return result;
    }

    /** 灌区年汇总卡：模型「年汇总」对象（塘坝可供水量/总需水量/总供水/水库计划补充/供需缺口，键名按模型实测收敛） */
    private Map<String, Object> buildIrrigationAnnual(JsonNode views) {
        Map<String, Object> annual = new LinkedHashMap<>();
        JsonNode yearSummary = views.get("年汇总");
        if (yearSummary == null || yearSummary.isNull() || !yearSummary.isObject()) {
            // 年汇总缺失时退化为「灌区水资源配置」逐旬求和
            double[] sums = new double[3];
            for (JsonNode item : views.path("灌区水资源配置")) {
                sums[0] += nvl(doubleOfAny(item, "塘坝可供水量（万方）", "塘坝可供水量"));
                sums[1] += nvl(doubleOfAny(item, "灌区总需水（万方）", "灌区总需水"));
                sums[2] += nvl(doubleOfAny(item, "灌区计划供水总量（万方）", "灌区计划供水总量"));
            }
            annual.put("pondSupply", round2OrNull(sums[0]));
            annual.put("totalDemand", round2OrNull(sums[1]));
            annual.put("plannedSupplyTotal", round2OrNull(sums[2]));
            annual.put("reservoirSupplementSupply", null);
            annual.put("gap", null);
            annual.put("irrigationTotalDemand", annual.get("totalDemand"));
            return annual;
        }
        annual.put("pondSupply", doubleOfAny(yearSummary, "塘坝可供水量（万方）", "塘坝可供水量", "塘坝供水"));
        annual.put("totalDemand", doubleOfAny(yearSummary, "总需水量（万方）", "灌区总需水（万方）", "总需水"));
        annual.put("plannedSupplyTotal", doubleOfAny(yearSummary, "总供水", "灌区计划供水总量（万方）", "计划供水总量"));
        annual.put("reservoirSupplementSupply", doubleOfAny(yearSummary, "花凉亭水库计划补充供水量（万方）", "花凉亭水库计划补充供水量", "水库计划补水"));
        annual.put("gap", doubleOfAny(yearSummary, "灌区供需缺口（万方）"));
        annual.put("irrigationTotalDemand", doubleOfAny(yearSummary, "灌区总需水（万方）", "灌区总需水"));
        return annual;
    }

    /** 灌区过程曲线：灌区水资源配置 array 逐旬三列（键名按模型实测收敛） */
    private List<Map<String, Object>> buildIrrigationProcess(JsonNode views) {
        List<Map<String, Object>> process = new ArrayList<>();
        JsonNode array = views.path("灌区水资源配置");
        if (array.isArray()) {
            log.info("配置两视图键名核对（灌区水资源配置）：首行键={}", fieldNamesOf(firstOf(array)));
            for (JsonNode item : array) {
                Map<String, Object> row = new LinkedHashMap<>();
                String label = textOfAny(item, "日期", "旬", "旬标签");
                row.put("tendayLabel", label);
                row.put("sortOrder", fullSortOf(label));
                row.put("totalDemand", doubleOfAny(item, "灌区总需水（万方）", "灌区总需水", "总需水量（万方）"));
                row.put("plannedSupplyTotal", doubleOfAny(item, "灌区计划供水总量（万方）", "灌区计划供水总量", "总供水"));
                row.put("reservoirSupplementSupply", doubleOfAny(item, "花凉亭水库计划补充供水量（万方）", "花凉亭水库计划补充供水量", "水库计划补水"));
                process.add(row);
            }
        }
        return process;
    }

    /**
     * 水库年汇总卡：水库年汇总 object。
     * <p>七项重点指标按模型实测键名解析（英文键），26 个中文键以 JsonNode 原样全量透出（raw）供前端/联调取用。
     */
    private Map<String, Object> buildReservoirAnnual(JsonNode views) {
        Map<String, Object> annual = new LinkedHashMap<>();
        JsonNode node = views.get("水库年汇总");
        if (node == null || node.isNull() || !node.isObject()) {
            annual.put("inflow", null);
            annual.put("supplementSupply", null);
            annual.put("powerGeneration", null);
            annual.put("leakageLoss", null);
            annual.put("startLevel", null);
            annual.put("endLevel", null);
            annual.put("maxLevel", null);
            return annual;
        }
        log.info("配置两视图键名核对（水库年汇总）：键={}", fieldNamesOf(node));
        // 26 个中文键 JsonNode 原样全量透出（不解析，避免键名歧义丢字段）
        node.fields().forEachRemaining(entry -> annual.put(entry.getKey(), entry.getValue()));
        annual.put("inflow", doubleOfAny(node, "入库水量（万方）", "入库水量", "总入库水量", "来水量"));
        annual.put("supplementSupply", doubleOfAny(node, "花凉亭水库计划补充供水量（万方）", "花凉亭水库计划补充供水量", "计划补充供水量", "渠首建议供水"));
        annual.put("powerGeneration", doubleOfAny(node, "过机发电水量（万方）", "过机发电水量", "发电水量"));
        annual.put("leakageLoss", doubleOfAny(node, "渗漏损失（万方）", "渗漏损失", "总渗漏损失"));
        annual.put("startLevel", doubleOfAny(node, "期初水位（m）", "期初水位", "起始水位"));
        annual.put("endLevel", doubleOfAny(node, "期末水位（m）", "期末水位", "年末水位"));
        annual.put("maxLevel", doubleOfAny(node, "水库最高水位（m）", "最高水位", "最大水位"));
        return annual;
    }

    /** 水库逐旬清单 array → tendayDetails（入库/计划补水/总出库/旬末水位/旬末库容，键名按模型实测收敛） */
    private List<Map<String, Object>> buildReservoirTendays(JsonNode views) {
        List<Map<String, Object>> tendays = new ArrayList<>();
        JsonNode array = views.path("水库逐旬清单");
        if (array.isArray()) {
            log.info("配置两视图键名核对（水库逐旬清单）：首行键={}", fieldNamesOf(firstOf(array)));
            for (JsonNode item : array) {
                Map<String, Object> row = new LinkedHashMap<>();
                String label = textOfAny(item, "日期", "旬", "旬标签");
                row.put("tendayLabel", label);
                row.put("sortOrder", fullSortOf(label));
                row.put("inflow", doubleOfAny(item, "入库水量（万方）", "入库水量", "BP预测来水量（万方）"));
                row.put("supplementSupply", doubleOfAny(item, "花凉亭水库计划补充供水量（万方）", "花凉亭水库计划补充供水量", "渠首建议供水"));
                row.put("totalOutflow", doubleOfAny(item, "水库总出库（含渗漏）（万方）", "水库总出库", "总出库", "出库水量"));
                row.put("endWaterLevel", doubleOfAny(item, "旬末水位（m）", "旬末水位", "月末水位"));
                row.put("endStorage", doubleOfAny(item, "旬末库容（万方）", "旬末库容", "月末库容"));
                tendays.add(row);
            }
        }
        return tendays;
    }

    /** 水库月汇总 array → monthlySummaries（逐月：期末水位/期末库容，键名按模型实测收敛） */
    private List<Map<String, Object>> buildReservoirMonthly(JsonNode views) {
        List<Map<String, Object>> monthly = new ArrayList<>();
        JsonNode array = views.path("水库月汇总");
        if (array.isArray()) {
            log.info("配置两视图键名核对（水库月汇总）：首行键={}", fieldNamesOf(firstOf(array)));
            for (JsonNode item : array) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", textOfAny(item, "日期", "月份", "月"));
                row.put("endWaterLevel", doubleOfAny(item, "期末水位（m）", "期末水位", "月末水位"));
                row.put("endStorage", doubleOfAny(item, "期末库容（万方）", "期末库容", "月末库容"));
                monthly.add(row);
            }
        }
        return monthly;
    }

    private static JsonNode firstOf(JsonNode array) {
        return array != null && array.isArray() && array.size() > 0 ? array.get(0) : null;
    }

    private static String fieldNamesOf(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "";
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return String.join(",", names);
    }

    private static Integer fullSortOf(String label) {
        Integer order = TenDayMapUtils.fullSortOrderOf(label);
        return order == null ? null : order;
    }

    private static double nvl(Double value) {
        return value == null ? 0 : value;
    }

    private static Double round2OrNull(double value) {
        return value == 0 ? null : round2(value);
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        AllocateRecord record = new AllocateRecord();
        record.setId(recordId);
        record.setStatus(ModelRecordCommonService.STATUS_FAILED);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("水资源配置失败：recordId={}, error={}", recordId, errorMsg);
    }
}
