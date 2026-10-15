package com.qgyun.hltgq.hltgqsite.watersaving.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingDict;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingSupport;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingAnnualMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingSchemeMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingAnnualSaveVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingAnnualVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingPrevPlanVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 年度水量记录服务（节水管理：年度水量设置 / 潜力计算 / 上一年计划带出）。
 * <p>口径（方案 V1.4 §2.1）：
 * <ul>
 *   <li>潜力 = 上一年实际放水量 − 上一年计划放水量（万m³，可为负、不截断；任一为空 → 不可计算）；</li>
 *   <li>计划自动带出：取依据年度的年度用水计划（plan_type '#1#' 且状态 ≠ '#4#' 已作废，
 *       按创建时间倒序第一条；total_water 库内 m³ ÷10000 转万m³），可手工覆盖；</li>
 *   <li>实际放水量：本期手工录入（渠首自动计量为预留来源）；</li>
 *   <li>潜力年度全表唯一；被方案引用（scheme.evidence_id）后禁止删除、潜力年度禁止修改（方案A）。</li>
 * </ul>
 * <p>水量变更联动：prev_plan_volume / prev_actual_volume 变化时，引用该记录的已核验方案核验作废
 * （status '#4#'、verify_invalid '#1#'、达标强制"待核验"、last_recalc 留痕）。
 */
@Service
public class SavingAnnualWaterService {

    private static final Logger log = LoggerFactory.getLogger(SavingAnnualWaterService.class);

    private final SavingAnnualMapper annualMapper;

    private final SavingSchemeMapper schemeMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public SavingAnnualWaterService(SavingAnnualMapper annualMapper,
                                    SavingSchemeMapper schemeMapper,
                                    ShortIdGenerator shortIdGenerator) {
        this.annualMapper = annualMapper;
        this.schemeMapper = schemeMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== 查询 ====================

    /** 全量列表（潜力年度倒序；含服务端计算的潜力与来源文案） */
    public List<SavingAnnualVO> list() {
        List<SavingAnnualVO> rows = annualMapper.selectList();
        for (SavingAnnualVO row : rows) {
            decorate(row);
        }
        return rows;
    }

    /**
     * 上一年度计划带出：year = 潜力年度（弹窗正在编辑的年度），返回其依据年度（year−1）的年度计划。
     * <p>未找到时 found=false（前端提示手工录入），不报错。
     */
    public SavingPrevPlanVO prevYearPlan(Integer year) {
        int potentialYear = SavingSupport.requireYear(year, "潜力年度");
        int basisYear = potentialYear - 1;
        // 边界日期在应用层拼好传入（KingbaseES 下 SQL 内 ? || '-' 拼接会按布尔解释报错）
        SavingPrevPlanVO vo = annualMapper.selectPrevYearPlan(basisYear + "-01-01", basisYear + "-12-31");
        if (vo == null) {
            vo = new SavingPrevPlanVO();
            vo.setFound(false);
        } else {
            vo.setFound(true);
        }
        vo.setBasisYear(basisYear);
        return vo;
    }

    // ==================== 保存 / 删除 ====================

    /**
     * 保存（有 id 更新 / 无 id 新增）。
     * <p>新增：潜力年度唯一预检 + 唯一索引兜底（并发 409）；
     * 更新：被方案引用时潜力年度不可修改；水量变化 → 引用方案的已核验数据作废（方案A，含留痕）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SavingAnnualVO save(SavingAnnualSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        int year = SavingSupport.requireYear(req.getPotentialYear(), "潜力年度");
        BigDecimal planVolume = SavingSupport.optionalNonNegative(req.getPrevPlanVolume(), "上一年计划放水量");
        BigDecimal actualVolume = SavingSupport.requireNonNegative(req.getPrevActualVolume(), "上一年实际放水量");
        String isDemo = SavingDict.normalizeFlag(req.getIsDemo());
        String planSource = SavingDict.PLAN_SOURCE_AUTO.equals(req.getPlanSource())
                ? SavingDict.PLAN_SOURCE_AUTO : SavingDict.PLAN_SOURCE_MANUAL;
        String planId = SavingDict.PLAN_SOURCE_AUTO.equals(planSource) ? SavingSupport.trim(req.getPlanId()) : null;

        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);

        if (req.getId() == null) {
            // 新增
            if (annualMapper.countByYear(year, null) > 0) {
                throw new IllegalArgumentException("潜力年度 " + year + " 已存在水量记录，请直接编辑");
            }
            String id = shortIdGenerator.nextUUID(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("potential_year", year);
            row.put("basis_year", year - 1);
            row.put("prev_plan_volume", planVolume);
            row.put("prev_actual_volume", actualVolume);
            row.put("plan_source", planSource);
            row.put("plan_id", planId);
            row.put("actual_source", SavingDict.ACTUAL_SOURCE_MANUAL);
            row.put("is_demo", isDemo);
            row.put("now", now);
            row.put("operator", operator);
            annualMapper.insert(row);
            log.info("[节水管理] 年度水量记录新增：year={}，计划={}，实际={}，操作人={}",
                    year, planVolume, actualVolume, operator);
            return detail(id);
        }

        // 更新
        SavingAnnualVO exist = requireAnnual(req.getId());
        boolean yearChanged = !exist.getPotentialYear().equals(year);
        if (yearChanged) {
            int refs = annualMapper.countSchemesByEvidence(exist.getId());
            if (refs > 0) {
                throw new IllegalArgumentException("该记录已被 " + refs + " 个方案引用，潜力年度不可修改");
            }
            if (annualMapper.countByYear(year, exist.getId()) > 0) {
                throw new IllegalArgumentException("潜力年度 " + year + " 已存在水量记录，请直接编辑");
            }
        }
        boolean waterChanged = !SavingSupport.sameValue(planVolume, exist.getPrevPlanVolume())
                || !SavingSupport.sameValue(actualVolume, exist.getPrevActualVolume());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", exist.getId());
        row.put("potential_year", year);
        row.put("basis_year", year - 1);
        row.put("prev_plan_volume", planVolume);
        row.put("prev_actual_volume", actualVolume);
        row.put("plan_source", planSource);
        row.put("plan_id", planId);
        row.put("actual_source", SavingDict.ACTUAL_SOURCE_MANUAL);
        row.put("is_demo", isDemo);
        row.put("now", now);
        row.put("operator", operator);
        annualMapper.update(row);

        if (waterChanged) {
            int invalidated = schemeMapper.invalidateVerifyByEvidence(exist.getId(),
                    SavingDict.RECHECK_REASON_INVALIDATED, now, operator);
            log.info("[节水管理] 年度水量记录变更（id={}，year={}）：水量有变化，方案核验作废 {} 个",
                    exist.getId(), year, invalidated);
        }
        return detail(exist.getId());
    }

    /** 物理删除（被方案引用禁止） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SavingAnnualVO exist = requireAnnual(id);
        int refs = annualMapper.countSchemesByEvidence(exist.getId());
        if (refs > 0) {
            throw new IllegalArgumentException("该记录已被 " + refs + " 个方案引用，不可删除（可先处理关联方案）");
        }
        annualMapper.delete(exist.getId());
        log.info("[节水管理] 年度水量记录删除：id={}，year={}", exist.getId(), exist.getPotentialYear());
    }

    // ==================== 内部 ====================

    /** 单条查询（含装饰；服务内部/详情复用） */
    public SavingAnnualVO detail(String id) {
        SavingAnnualVO vo = requireAnnual(id);
        decorate(vo);
        return vo;
    }

    private SavingAnnualVO requireAnnual(String id) {
        String key = SavingSupport.requireText(id, "年度水量记录 id 不能为空");
        SavingAnnualVO vo = annualMapper.selectById(key);
        if (vo == null) {
            throw new IllegalArgumentException("年度水量记录不存在");
        }
        return vo;
    }

    /** 潜力与来源文案装饰（服务端计算唯一实现） */
    private void decorate(SavingAnnualVO vo) {
        vo.setPotential(SavingDict.potential(vo.getPrevPlanVolume(), vo.getPrevActualVolume()));
        vo.setPlanSourceName(SavingDict.planSourceName(vo.getPlanSource()));
        vo.setActualSourceName(SavingDict.actualSourceName(vo.getActualSource()));
    }
}
