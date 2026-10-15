package com.qgyun.hltgq.hltgqsite.watersaving.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingDict;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingSupport;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingIndicatorMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingSchemeMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorSaveVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorVO;
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
 * 节水指标服务（指标体系维护：新增/编辑/启停/删除 + 指标联动）。
 * <p>口径（方案 V1.4 §2.3）：
 * <ul>
 *   <li>计算方式固定三种（#1# 节水量 / #2# 节水率 / #3# 目标完成率），方法驱动服务端计算（SavingDict.methodResult）；</li>
 *   <li>名称全局唯一（含停用指标）；目标值可空（未设 → 方案"待设置目标"）；</li>
 *   <li>已关联方案的指标：计算方式锁定（不可变化）、不可删除（可停用）；停用后新方案不可勾选、既有关联保留；</li>
 *   <li>联动重算（§6.1-5）：目标值变更 / 启停变更 → 关联方案 last_recalc 留痕（达标展示为派生计算，无需改状态）。</li>
 * </ul>
 */
@Service
public class SavingIndicatorService {

    private static final Logger log = LoggerFactory.getLogger(SavingIndicatorService.class);

    /** 指标用途长度上限（防御性，TEXT 列） */
    private static final int PURPOSE_MAX_LENGTH = 200;

    private final SavingIndicatorMapper indicatorMapper;

    private final SavingSchemeMapper schemeMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public SavingIndicatorService(SavingIndicatorMapper indicatorMapper,
                                  SavingSchemeMapper schemeMapper,
                                  ShortIdGenerator shortIdGenerator) {
        this.indicatorMapper = indicatorMapper;
        this.schemeMapper = schemeMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== 查询 ====================

    /** 全量列表（含已关联方案数 schemeCount；keyword 名称模糊） */
    public List<SavingIndicatorVO> list(String keyword) {
        List<SavingIndicatorVO> rows = indicatorMapper.selectList(SavingSupport.trim(keyword));
        for (SavingIndicatorVO row : rows) {
            decorate(row);
            row.setSchemeCount(schemeMapper.countByIndicatorRef(row.getId()));
        }
        return rows;
    }

    /** 详情（编辑回显） */
    public SavingIndicatorVO detail(String id) {
        SavingIndicatorVO vo = requireIndicator(id);
        decorate(vo);
        vo.setSchemeCount(schemeMapper.countByIndicatorRef(vo.getId()));
        return vo;
    }

    // ==================== 新增 / 编辑 / 启停 / 删除 ====================

    /** 新增（名称全局唯一含停用；计算方式三选一；目标值选填 >0） */
    @Transactional(rollbackFor = Exception.class)
    public SavingIndicatorVO create(SavingIndicatorSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = SavingSupport.maxLength(
                SavingSupport.requireText(req.getName(), "请填写指标名称"), 200, "指标名称");
        String method = requireMethod(req.getMethod());
        BigDecimal targetValue = req.getTargetValue() == null ? null
                : SavingSupport.requirePositive(req.getTargetValue(), "目标值");
        String purpose = SavingSupport.maxLength(SavingSupport.trim(req.getPurpose()), PURPOSE_MAX_LENGTH, "指标用途");
        if (indicatorMapper.countByName(name, null) > 0) {
            throw new IllegalArgumentException("指标名称已存在（停用指标的名称同样占用）");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("method", method);
        row.put("target_value", targetValue);
        row.put("purpose", purpose);
        row.put("enabled", SavingDict.YES);
        row.put("now", now);
        row.put("operator", operator);
        indicatorMapper.insert(row);
        log.info("[节水管理] 指标新增：{}（{}），操作人={}", name, SavingDict.methodName(method), operator);
        return detail(id);
    }

    /**
     * 编辑：已关联方案的指标计算方式锁定；目标值变更 → 关联方案联动留痕（指标目标值变更）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SavingIndicatorVO update(String id, SavingIndicatorSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        SavingIndicatorVO exist = requireIndicator(id);
        String name = SavingSupport.maxLength(
                SavingSupport.requireText(req.getName(), "请填写指标名称"), 200, "指标名称");
        String method = requireMethod(req.getMethod());
        BigDecimal targetValue = req.getTargetValue() == null ? null
                : SavingSupport.requirePositive(req.getTargetValue(), "目标值");
        String purpose = SavingSupport.maxLength(SavingSupport.trim(req.getPurpose()), PURPOSE_MAX_LENGTH, "指标用途");
        if (indicatorMapper.countByName(name, exist.getId()) > 0) {
            throw new IllegalArgumentException("指标名称已存在（停用指标的名称同样占用）");
        }
        int refs = schemeMapper.countByIndicatorRef(exist.getId());
        if (refs > 0 && !exist.getMethod().equals(method)) {
            throw new IllegalArgumentException("该指标已被 " + refs
                    + " 个方案关联，计算方式已锁定不可修改（可新建指标替代）");
        }
        boolean targetChanged = !SavingSupport.sameValue(targetValue, exist.getTargetValue());
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", exist.getId());
        row.put("name", name);
        row.put("method", exist.getMethod().equals(method) ? exist.getMethod() : method);
        row.put("target_value", targetValue);
        row.put("purpose", purpose);
        row.put("now", now);
        row.put("operator", operator);
        indicatorMapper.update(row);
        if (targetChanged && refs > 0) {
            touchRelatedSchemes(exist.getId(), "指标目标值变更", now, operator);
        }
        log.info("[节水管理] 指标编辑：{}（id={}），目标值{}，操作人={}",
                name, exist.getId(), targetChanged ? "变更" : "未变", operator);
        return detail(exist.getId());
    }

    /** 启停切换（停用后新方案不可勾选、既有关联保留；联动留痕"指标状态变更"） */
    @Transactional(rollbackFor = Exception.class)
    public SavingIndicatorVO toggle(String id) {
        SavingIndicatorVO exist = requireIndicator(id);
        String to = SavingDict.YES.equals(exist.getEnabled()) ? SavingDict.NO : SavingDict.YES;
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        indicatorMapper.updateEnabled(exist.getId(), to, now, operator);
        touchRelatedSchemes(exist.getId(), "指标状态变更", now, operator);
        log.info("[节水管理] 指标{}：{}（id={}），操作人={}",
                SavingDict.YES.equals(to) ? "启用" : "停用", exist.getName(), exist.getId(), operator);
        return detail(exist.getId());
    }

    /** 物理删除（被方案关联禁止；建议改为停用） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SavingIndicatorVO exist = requireIndicator(id);
        int refs = schemeMapper.countByIndicatorRef(exist.getId());
        if (refs > 0) {
            throw new IllegalArgumentException("该指标已被 " + refs
                    + " 个方案关联，不可删除（可改为停用，既有关联与计算结果保留）");
        }
        indicatorMapper.delete(exist.getId());
        log.info("[节水管理] 指标删除：{}（id={}）", exist.getName(), exist.getId());
    }

    // ==================== 内部 ====================

    private SavingIndicatorVO requireIndicator(String id) {
        String key = SavingSupport.requireText(id, "指标 id 不能为空");
        SavingIndicatorVO vo = indicatorMapper.selectById(key);
        if (vo == null) {
            throw new IllegalArgumentException("指标不存在或已删除");
        }
        return vo;
    }

    private static String requireMethod(String code) {
        if (!SavingDict.isValidMethod(code)) {
            throw new IllegalArgumentException("请选择计算方式（节水量 / 节水率 / 目标完成率）");
        }
        return code;
    }

    /** 名称/单位/公式/状态文案装饰 */
    private void decorate(SavingIndicatorVO vo) {
        vo.setMethodName(SavingDict.methodName(vo.getMethod()));
        vo.setUnit(SavingDict.methodUnit(vo.getMethod()));
        vo.setFormula(SavingDict.methodFormula(vo.getMethod()));
        vo.setEnabledName(SavingDict.YES.equals(vo.getEnabled()) ? "启用" : "停用");
    }

    /** 关联方案联动留痕（达标展示为派生计算；此处仅记录最近重算时间与原因） */
    private void touchRelatedSchemes(String indicatorId, String reason, LocalDateTime now, String operator) {
        List<String> schemeIds = schemeMapper.selectIdsByIndicatorRef(indicatorId);
        if (!schemeIds.isEmpty()) {
            schemeMapper.touchRecalc(schemeIds, reason, now, operator);
            log.info("[节水管理] 指标联动：{} 个关联方案重算留痕（{}）", schemeIds.size(), reason);
        }
    }
}
