package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageRules;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterPlanMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterQuotaMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterQuotaSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterQuotaVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用水定额标准库服务（列表/详情/新增/编辑/删除保护 + 表单元数据）。
 * <p>灌溉方式为必填字典（#1# 淹灌 ~ #10# 计划直供，前端下拉不写死）；
 * 定额编辑不回溯既有计划（计划明细保存 calc_water/plan_water 快照，仅新建测算取新值）；
 * 删除保护两级规则与用水户一致：被非编制中计划引用 → 禁止；
 * 仅被编制中计划引用 → 二次确认（force=true）后连带清理明细并重算计划总水量。
 */
@Service
public class WaterQuotaService {

    private final WaterQuotaMapper quotaMapper;

    private final WaterPlanMapper planMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterQuotaService(WaterQuotaMapper quotaMapper, WaterPlanMapper planMapper,
                             ShortIdGenerator shortIdGenerator) {
        this.quotaMapper = quotaMapper;
        this.planMapper = planMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== 列表 / 详情 / 元数据 ====================

    /** 表单元数据（页面加载一次：灌溉方式字典 + 规则版本） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("irrigationMethods", WaterUsageDict.irrigationMethodOptions());
        out.put("today", LocalDate.now().toString());
        out.put("rulesVersion", WaterUsageRules.version());
        return out;
    }

    /** 列表分页（被引用计划数实时 count；灌溉方式关键字经字典映射为编码匹配） */
    public Map<String, Object> page(int page, int size, String keyword) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        List<String> methodCodes = WaterUsageDict.irrigationMethodCodesOfKeyword(kw);
        long total = quotaMapper.countQuotas(kw, methodCodes);
        List<WaterQuotaVO> records = quotaMapper.selectQuotaPage(kw, methodCodes, s, (long) (p - 1) * s);
        for (WaterQuotaVO row : records) {
            decorate(row);
        }
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情 */
    public WaterQuotaVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "定额 id 不能为空");
        WaterQuotaVO vo = quotaMapper.selectQuotaById(key);
        if (vo == null) {
            throw new IllegalArgumentException("定额不存在");
        }
        decorate(vo);
        return vo;
    }

    // ==================== 新增 / 编辑 ====================

    /** 新增 */
    @Transactional(rollbackFor = Exception.class)
    public WaterQuotaVO create(WaterQuotaSaveVO req) {
        Map<String, Object> row = checkAndBuild(req);
        row.put("id", shortIdGenerator.nextUUID(null));
        row.put("now", LocalDateTime.now());
        row.put("operator", WaterUsageSupport.currentOperatorId(fallbackOperator));
        quotaMapper.insertQuota(row);
        return detail((String) row.get("id"));
    }

    /** 编辑（不回溯已生效/已归档计划核定水量，仅影响新建测算） */
    @Transactional(rollbackFor = Exception.class)
    public WaterQuotaVO update(String id, WaterQuotaSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "定额 id 不能为空");
        if (quotaMapper.selectQuotaById(key) == null) {
            throw new IllegalArgumentException("定额不存在");
        }
        Map<String, Object> row = checkAndBuild(req);
        row.put("id", key);
        row.put("now", LocalDateTime.now());
        row.put("operator", WaterUsageSupport.currentOperatorId(fallbackOperator));
        quotaMapper.updateQuota(row);
        return detail(key);
    }

    // ==================== 删除保护（两级） ====================

    /** 删除前置检查（前端据此渲染两级确认弹窗） */
    public Map<String, Object> deleteCheck(String id) {
        String key = WaterUsageSupport.requireText(id, "定额 id 不能为空");
        if (quotaMapper.selectQuotaById(key) == null) {
            throw new IllegalArgumentException("定额不存在");
        }
        return WaterUsageSupport.splitPlanRefs(quotaMapper.selectQuotaRefs(key));
    }

    /**
     * 删除：被非编制中计划引用 → 禁止；
     * 仅被编制中计划引用且 force=true → 连带清理明细并重算计划总水量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, boolean force) {
        String key = WaterUsageSupport.requireText(id, "定额 id 不能为空");
        if (quotaMapper.selectQuotaById(key) == null) {
            throw new IllegalArgumentException("定额不存在");
        }
        Map<String, Object> refs = WaterUsageSupport.splitPlanRefs(quotaMapper.selectQuotaRefs(key));
        List<String> planIds = WaterUsageSupport.resolvePlanRefsDelete(refs, force, "该定额标准");
        if (!planIds.isEmpty()) {
            quotaMapper.deleteQuotaItemsInPlans(key, planIds);
            planMapper.recalcPlanTotals(planIds, LocalDateTime.now(),
                    WaterUsageSupport.currentOperatorId(fallbackOperator));
        }
        quotaMapper.deleteQuota(key);
    }

    // ==================== 校验 / 组装 ====================

    /** 全字段校验并组装写库 Map（row key 为 snake_case 列名） */
    private Map<String, Object> checkAndBuild(WaterQuotaSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String cropType = WaterUsageSupport.requireText(req.getCropType(), "请填写作物种类");
        WaterUsageSupport.maxLength(cropType, 100, "作物种类");
        String irrigationMethod = WaterUsageSupport.requireText(req.getIrrigationMethod(), "请选择灌溉方式");
        if (!WaterUsageDict.isValidIrrigationMethod(irrigationMethod)) {
            throw new IllegalArgumentException("灌溉方式无效，请从下拉列表中选择");
        }
        BigDecimal quotaValue = WaterUsageSupport.requirePositive(req.getQuotaValue(), "定额标准", 2);
        String remark = WaterUsageSupport.trim(req.getRemark());
        WaterUsageSupport.maxLength(remark, 200, "备注");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("crop_type", cropType);
        row.put("irrigation_method", irrigationMethod);
        row.put("quota_value", quotaValue);
        row.put("remark", remark);
        return row;
    }

    /** 填充灌溉方式名称（列表/详情，前端无需本地字典） */
    private void decorate(WaterQuotaVO vo) {
        if (vo == null) {
            return;
        }
        vo.setIrrigationMethodName(WaterUsageDict.irrigationMethodName(vo.getIrrigationMethod()));
    }
}
