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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 需水上报服务（列表/详情/上报/审核/删除）。
 * <p>状态机：待审核(wait) →（审核）已通过(pass)/已驳回(reject)；待审核/已驳回可编辑
 * （保存后回到待审核并清空审核痕迹，重新进入审核流程）；已通过锁定编辑（审核结论）。
 * <p>用水时间按旬维度：年 + 月 + 旬多选（上旬 1~10 / 中旬 11~20 / 下旬 21~月末），
 * 起止日期由所选旬自动派生（最早旬首日 ~ 最晚旬末日），冗余落入 start_date/end_date
 * 供列表展示与年度统计沿用；旧数据无旬列时按起止日期反推回显。
 * <p>删除保护：被调度指令引用（含已取消指令，留痕不悬空）时禁止删除。
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

    /**
     * 表单元数据（审核状态字典 + 用途候选 + 当天日期）。
     * <p>候选全部取后端**写死字典**（{@link WaterUsageDict#usageOptions()} 等），**不查库**、
     * 不依赖库内是否已有上报数据；「用途」为封闭值域，提交时按同一字典白名单校验。
     */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("statuses", WaterUsageDict.demandStatusOptions());
        out.put("usages", WaterUsageDict.usageOptions());
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
        return userMapper.selectUserOptions(WaterUsageSupport.trim(keyword), null);
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
        row.put("period_year", v.periodYear);
        row.put("period_month", v.periodMonth);
        row.put("xun_list", v.xunValues);
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
        row.put("period_year", v.periodYear);
        row.put("period_month", v.periodMonth);
        row.put("xun_list", v.xunValues);
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

    /** 删除（被调度指令引用时禁止，含已取消指令） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "上报单 id 不能为空");
        WaterDemandVO current = demandMapper.selectDemandById(key);
        if (current == null) {
            throw new IllegalArgumentException("需水上报单不存在");
        }
        if (demandMapper.countDispatchRefs(key) > 0) {
            throw new IllegalArgumentException("该上报单已被调度指令引用，请先删除关联调度指令");
        }
        demandMapper.deleteDemand(key);
    }

    // ==================== 内部 ====================

    /** 校验产物：已归一化的入库值（m³ / LocalDate / 年月旬 / trim 文本） */
    private static final class Validated {
        String userId;
        String gateId;
        BigDecimal amount;
        Integer periodYear;
        Integer periodMonth;
        String xunValues;
        LocalDate startDate;
        LocalDate endDate;
        String usage;
        String remark;
    }

    /** 旬编码 → 中文名（顺序即展示顺序） */
    private static final String[] XUN_NAMES = {"上旬", "中旬", "下旬"};

    /**
     * 上报/编辑共用校验：用水户正常、闸口归属该户闸口组、水量合法、年月旬合法（派生起止日期）、
     * 用途属字典候选、备注长度。
     * <p>年月旬缺失时兼容旧口径 startDate/endDate（按起止日期反推年月与覆盖旬）。
     */
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
        resolvePeriod(req, v);
        v.usage = requireUsage(req.getUsage());
        v.remark = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getRemark()), 200, "备注");
        return v;
    }

    /**
     * 用水时间归一：年 + 月 + 旬多选 → xunValues（"1,3"）+ 派生起止日期；
     * 三项全空时走旧口径（起止日期反推年月与覆盖旬，旬列可为空）。
     */
    private void resolvePeriod(WaterDemandSaveVO req, Validated v) {
        Integer year = req.getPeriodYear();
        Integer month = req.getPeriodMonth();
        TreeSet<Integer> xuns = normalizeXun(req.getXunList());
        if (year == null && month == null && xuns.isEmpty()) {
            LocalDate start = WaterUsageSupport.parseDate(req.getStartDate(), "用水时段起");
            LocalDate end = WaterUsageSupport.parseDate(req.getEndDate(), "用水时段止");
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("用水时段止不能早于用水时段起");
            }
            v.periodYear = start.getYear();
            v.periodMonth = start.getMonthValue();
            v.xunValues = null;
            v.startDate = start;
            v.endDate = end;
            return;
        }
        if (year == null || year < 2000 || year > 2100) {
            throw new IllegalArgumentException("请选择用水年份");
        }
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("请选择用水月份");
        }
        if (xuns.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个旬");
        }
        v.periodYear = year;
        v.periodMonth = month;
        StringBuilder values = new StringBuilder();
        LocalDate firstStart = null;
        LocalDate lastEnd = null;
        for (Integer xun : xuns) {
            if (values.length() > 0) {
                values.append(',');
            }
            values.append(xun);
            LocalDate xunStart = xunStartDate(year, month, xun);
            LocalDate xunEnd = xunEndDate(year, month, xun);
            if (firstStart == null || xunStart.isBefore(firstStart)) {
                firstStart = xunStart;
            }
            if (lastEnd == null || xunEnd.isAfter(lastEnd)) {
                lastEnd = xunEnd;
            }
        }
        v.xunValues = values.toString();
        v.startDate = firstStart;
        v.endDate = lastEnd;
    }

    /** 旬多选归一：接受编码 1/2/3、中文 上旬/中旬/下旬、逗号拼接串；去重升序 */
    private static TreeSet<Integer> normalizeXun(List<String> raw) {
        TreeSet<Integer> out = new TreeSet<>();
        if (raw == null) {
            return out;
        }
        for (String item : raw) {
            String text = WaterUsageSupport.trim(item);
            if (text == null) {
                continue;
            }
            for (String part : text.split("[,，]")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                Integer code = xunCodeOf(token);
                if (code == null) {
                    throw new IllegalArgumentException("旬参数不正确：" + token);
                }
                out.add(code);
            }
        }
        return out;
    }

    /** 旬编码/中文名 → 1~3；无法识别返回 null */
    private static Integer xunCodeOf(String token) {
        if ("1".equals(token) || "上旬".equals(token)) {
            return 1;
        }
        if ("2".equals(token) || "中旬".equals(token)) {
            return 2;
        }
        if ("3".equals(token) || "下旬".equals(token)) {
            return 3;
        }
        return null;
    }

    /** 旬首日：上旬 1 日 / 中旬 11 日 / 下旬 21 日 */
    private static LocalDate xunStartDate(int year, int month, int xun) {
        int day = (xun - 1) * 10 + 1;
        return LocalDate.of(year, month, day);
    }

    /** 旬末日：上旬 10 日 / 中旬 20 日 / 下旬 当月最后一天 */
    private static LocalDate xunEndDate(int year, int month, int xun) {
        if (xun == 3) {
            return YearMonth.of(year, month).atEndOfMonth();
        }
        return LocalDate.of(year, month, xun * 10);
    }

    /**
     * 用途：选填，但**必须是字典写死 12 项之一**（内存白名单校验，不查库、不接受候选外的自输入值）。
     */
    private static String requireUsage(String raw) {
        String usage = WaterUsageSupport.trim(raw);
        if (usage == null) {
            return null;
        }
        if (!WaterUsageDict.isValidUsage(usage)) {
            throw new IllegalArgumentException("请从下拉列表中选择用途");
        }
        return usage;
    }

    /** 出口装饰：审核状态英文码 + 中文名、水量折算万m³、旬回显（旧数据按起止日期反推） */
    private void decorate(WaterDemandVO vo) {
        if (vo == null) {
            return;
        }
        String raw = vo.getStatus();
        vo.setStatusName(WaterUsageDict.demandStatusName(raw));
        vo.setStatus(WaterUsageDict.demandStatusCodeOf(raw));
        vo.setAmount(WaterUsageSupport.toWan(vo.getAmount()));
        List<String> xuns = splitXun(vo.getXunValues());
        LocalDate start = WaterUsageSupport.parseQuiet(vo.getStartDate());
        LocalDate end = WaterUsageSupport.parseQuiet(vo.getEndDate());
        if (xuns.isEmpty() && start != null) {
            xuns = deriveXun(start, end);
        }
        if (vo.getPeriodYear() == null && start != null) {
            vo.setPeriodYear(start.getYear());
        }
        if (vo.getPeriodMonth() == null && start != null) {
            vo.setPeriodMonth(start.getMonthValue());
        }
        vo.setXunList(xuns);
        vo.setXunText(xunTextOf(xuns));
    }

    /** 库内 "1,3" → ["1","3"]（去重升序；非法值忽略） */
    private static List<String> splitXun(String raw) {
        List<String> out = new ArrayList<>();
        String text = WaterUsageSupport.trim(raw);
        if (text == null) {
            return out;
        }
        TreeSet<Integer> codes = new TreeSet<>();
        for (String part : text.split("[,，]")) {
            Integer code = xunCodeOf(part.trim());
            if (code != null) {
                codes.add(code);
            }
        }
        for (Integer code : codes) {
            out.add(String.valueOf(code));
        }
        return out;
    }

    /** 旧数据反推：起止日期覆盖到的旬（仅起始年月范围） */
    private static List<String> deriveXun(LocalDate start, LocalDate end) {
        List<String> out = new ArrayList<>();
        if (start == null) {
            return out;
        }
        LocalDate stop = end == null ? start : end;
        for (int xun = 1; xun <= 3; xun++) {
            LocalDate from = xunStartDate(start.getYear(), start.getMonthValue(), xun);
            LocalDate to = xunEndDate(start.getYear(), start.getMonthValue(), xun);
            if (!stop.isBefore(from) && !start.isAfter(to)) {
                out.add(String.valueOf(xun));
            }
        }
        return out;
    }

    /** 旬编码列表 → 中文（「上旬、中旬」） */
    private static String xunTextOf(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String code : codes) {
            int index = Integer.parseInt(code) - 1;
            if (index >= 0 && index < XUN_NAMES.length) {
                names.add(XUN_NAMES[index]);
            }
        }
        return names.isEmpty() ? null : String.join("、", names);
    }
}
