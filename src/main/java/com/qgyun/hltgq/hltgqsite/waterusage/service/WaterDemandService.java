package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport.GateConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDemandMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DemandOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.UserOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDemandSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDemandVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
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
 * 需水上报服务（列表/详情/上报/审核/删除）。
 * <p>状态机：待审核(wait) →（审核）已通过(pass)/已驳回(reject)；待审核/已驳回可编辑
 * （保存后回到待审核并清空审核痕迹，重新进入审核流程）；已通过锁定编辑（审核结论）。
 * <p>删除保护：被调度方案引用（含已取消方案，留痕不悬空）时禁止删除。
 * <p>数量口径：接口对外万m³（≤2 位小数），库内 m³；编号 XS + 4 位序号 MAX+1 生成
 * （唯一索引兜底并发，冲突重试在 Controller 层）。
 */
@Service
public class WaterDemandService {

    private final WaterDemandMapper demandMapper;

    private final WaterUserMapper userMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final ObjectMapper objectMapper;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterDemandService(WaterDemandMapper demandMapper,
                              WaterUserMapper userMapper,
                              ShortIdGenerator shortIdGenerator,
                              ObjectMapper objectMapper) {
        this.demandMapper = demandMapper;
        this.userMapper = userMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
    }

    // ==================== 元数据 / 统计 / 列表 / 详情 ====================

    /** 表单元数据（审核状态字典 + 用途建议 + 当天日期） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("statuses", WaterUsageDict.demandStatusOptions());
        out.put("usages", demandMapper.selectUsageOptions());
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** Tab 角标统计（全部/待审核/已通过/已驳回） */
    public Map<String, Object> stats() {
        return WaterUsageSupport.statusStats(demandMapper.countByStatus(),
                WaterUsageDict::demandStatusCodeOf, "wait", "pass", "reject");
    }

    /** 列表分页（status 传前端英文码 wait/pass/reject） */
    public Map<String, Object> page(int page, int size, String keyword, String status) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String st = null;
        String unit = WaterUsageSupport.trim(status);
        if (unit != null) {
            st = WaterUsageDict.demandStatusOfCode(unit);
            if (st == null) {
                throw new IllegalArgumentException("审核状态参数不正确");
            }
        }
        long total = demandMapper.countDemands(kw, st);
        List<WaterDemandVO> records = demandMapper.selectDemandPage(kw, st, s, (long) (p - 1) * s);
        for (WaterDemandVO row : records) {
            decorate(row);
        }
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情 */
    public WaterDemandVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "上报单 id 不能为空");
        WaterDemandVO vo = demandMapper.selectDemandById(key);
        if (vo == null) {
            throw new IllegalArgumentException("需水上报单不存在");
        }
        decorate(vo);
        return vo;
    }

    /** 「用水户」下拉（仅正常状态） */
    public List<UserOptionVO> users(String keyword) {
        return userMapper.selectUserOptions(WaterUsageSupport.trim(keyword));
    }

    /** 「关联需水申请」下拉（仅已通过；调度方案编制用） */
    public List<DemandOptionVO> demands(String keyword, String userId) {
        List<DemandOptionVO> rows = demandMapper.selectPassedOptions(
                WaterUsageSupport.trim(keyword), WaterUsageSupport.trim(userId));
        for (DemandOptionVO row : rows) {
            row.setAmount(WaterUsageSupport.toWan(row.getAmount()));
        }
        return rows;
    }

    // ==================== 新增 / 编辑 / 审核 / 删除 ====================

    /** 新增上报（待审核） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDemandVO create(WaterDemandSaveVO req) {
        Validated v = validate(req);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("demand_no", String.format("XS%04d", demandMapper.selectMaxDemandSeq() + 1));
        row.put("water_user_id", v.userId);
        row.put("gate_id", v.gateId);
        row.put("amount", v.amount);
        row.put("start_date", v.startDate.toString());
        row.put("end_date", v.endDate.toString());
        row.put("usage_desc", v.usage);
        row.put("remark", v.remark);
        row.put("now", now);
        row.put("operator", operator);
        demandMapper.insertDemand(row);
        return detail(id);
    }

    /** 编辑（仅待审核/已驳回；保存后回到待审核并清空审核痕迹） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDemandVO update(String id, WaterDemandSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "上报单 id 不能为空");
        WaterDemandVO current = demandMapper.selectDemandById(key);
        if (current == null) {
            throw new IllegalArgumentException("需水上报单不存在");
        }
        String rawStatus = current.getStatus();
        if (!WaterUsageDict.DEMAND_STATUS_WAIT.equals(rawStatus)
                && !WaterUsageDict.DEMAND_STATUS_REJECT.equals(rawStatus)) {
            throw new IllegalArgumentException("已通过的上报单不可编辑");
        }
        Validated v = validate(req);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key);
        row.put("water_user_id", v.userId);
        row.put("gate_id", v.gateId);
        row.put("amount", v.amount);
        row.put("start_date", v.startDate.toString());
        row.put("end_date", v.endDate.toString());
        row.put("usage_desc", v.usage);
        row.put("remark", v.remark);
        row.put("now", now);
        row.put("operator", operator);
        if (demandMapper.updateDemand(row) == 0) {
            throw new IllegalArgumentException("上报单状态已变更，请刷新后重试");
        }
        return detail(key);
    }

    /**
     * 审核（仅待审核）：action = pass 通过 / reject 驳回；驳回意见必填。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaterDemandVO audit(String id, String action, String opinion) {
        String key = WaterUsageSupport.requireText(id, "上报单 id 不能为空");
        WaterDemandVO current = demandMapper.selectDemandById(key);
        if (current == null) {
            throw new IllegalArgumentException("需水上报单不存在");
        }
        if (!WaterUsageDict.DEMAND_STATUS_WAIT.equals(current.getStatus())) {
            throw new IllegalArgumentException("仅待审核的上报单可审核");
        }
        String act = WaterUsageSupport.requireText(action, "审核动作不能为空");
        String toStatus;
        if ("pass".equals(act)) {
            toStatus = WaterUsageDict.DEMAND_STATUS_PASS;
        } else if ("reject".equals(act)) {
            toStatus = WaterUsageDict.DEMAND_STATUS_REJECT;
        } else {
            throw new IllegalArgumentException("审核动作参数不正确");
        }
        String opinionText = WaterUsageSupport.maxLength(
                WaterUsageSupport.trim(opinion), 200, "审核意见");
        if (WaterUsageDict.DEMAND_STATUS_REJECT.equals(toStatus) && opinionText == null) {
            throw new IllegalArgumentException("请填写驳回意见");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        if (demandMapper.auditDemand(key, toStatus, opinionText, now, operator) == 0) {
            throw new IllegalArgumentException("上报单状态已变更，请刷新后重试");
        }
        return detail(key);
    }

    /** 删除（被调度方案引用时禁止，含已取消方案） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "上报单 id 不能为空");
        WaterDemandVO current = demandMapper.selectDemandById(key);
        if (current == null) {
            throw new IllegalArgumentException("需水上报单不存在");
        }
        if (demandMapper.countDispatchRefs(key) > 0) {
            throw new IllegalArgumentException("该上报单已被调度方案引用，请先删除关联调度方案");
        }
        demandMapper.deleteDemand(key);
    }

    // ==================== 内部 ====================

    /** 校验产物：已归一化的入库值（m³ / LocalDate / trim 文本） */
    private static final class Validated {
        String userId;
        String gateId;
        BigDecimal amount;
        LocalDate startDate;
        LocalDate endDate;
        String usage;
        String remark;
    }

    /** 上报/编辑共用校验：用水户正常、闸口归属该户闸口组、水量/日期/文本长度 */
    private Validated validate(WaterDemandSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Validated v = new Validated();
        v.userId = WaterUsageSupport.requireText(req.getWaterUserId(), "请选择用水户");
        WaterUserVO user = userMapper.selectUserById(v.userId);
        if (user == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        if (!WaterUsageDict.USER_STATUS_NORMAL.equals(user.getStatus())) {
            throw new IllegalArgumentException("用水户已停用，不可上报需水");
        }
        v.gateId = WaterUsageSupport.trim(req.getGateId());
        if (v.gateId != null) {
            GateConfig config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
            if (config == null) {
                throw new IllegalArgumentException("该用水户未配置关联闸口，无法指定申报闸口");
            }
            if (!config.contains(v.gateId)) {
                throw new IllegalArgumentException("申报闸口不属于该用水户的关联闸口组");
            }
        }
        v.amount = WaterUsageSupport.requirePositiveWan(req.getAmount(), "申报计划水量");
        v.startDate = WaterUsageSupport.parseDate(req.getStartDate(), "用水时段起");
        v.endDate = WaterUsageSupport.parseDate(req.getEndDate(), "用水时段止");
        if (v.endDate.isBefore(v.startDate)) {
            throw new IllegalArgumentException("用水时段止不能早于用水时段起");
        }
        v.usage = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getUsage()), 200, "用途");
        v.remark = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getRemark()), 200, "备注");
        return v;
    }

    /** 出口装饰：审核状态英文码 + 中文名、水量折算万m³ */
    private void decorate(WaterDemandVO vo) {
        if (vo == null) {
            return;
        }
        String raw = vo.getStatus();
        vo.setStatusName(WaterUsageDict.demandStatusName(raw));
        vo.setStatus(WaterUsageDict.demandStatusCodeOf(raw));
        vo.setAmount(WaterUsageSupport.toWan(vo.getAmount()));
    }
}
