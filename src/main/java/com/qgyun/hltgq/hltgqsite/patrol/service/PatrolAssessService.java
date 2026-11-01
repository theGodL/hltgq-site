package com.qgyun.hltgq.hltgqsite.patrol.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.PatrolAssess;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolAssessMapper;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolPlanMapper;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolAssessRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolAssessVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleDetailVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 巡查考核服务（计划列表页「考核 / 查看考核」）。
 *
 * <p>闭环约束：
 * <ul>
 *   <li>只有存储状态为已完成 #3# 的计划可考核（已取消 #4# / 进行中 / 草稿均拒绝）</li>
 *   <li>一计划一考核：先查已考核再插入，schedule_id 唯一索引兜底；重复提交返回 400</li>
 *   <li>自动项在提交时重算并落库（快照），查看读快照——考核后问题再流转不改写已考核数据</li>
 *   <li>考核人取当前登录人自动留痕，不提供选择</li>
 * </ul>
 * <p>手工项：考核得分（0~100，最多 1 位小数）、考核结论（≤1000 字）；
 * 考核等级由得分分档自动生成（≥90 优秀 / 80~89 良好 / 60~79 及格 / <60 不及格），不接受人工指定。
 */
@Service
public class PatrolAssessService {

    private static final Logger log = LoggerFactory.getLogger(PatrolAssessService.class);

    /** 已完成状态编码 */
    private static final String STATUS_COMPLETED = "#3#";

    /** 考核结论长度上限（与巡查内容一致） */
    private static final int CONCLUSION_MAX_LENGTH = 1000;

    /** 考核得分格式：0~100，最多 1 位小数 */
    private static final Pattern SCORE_PATTERN = Pattern.compile("^(\\d{1,2}(\\.\\d)?|100(\\.0)?)$");

    /** 考核等级编码 → 名称 */
    private static final Map<String, String> GRADE_LABELS = new LinkedHashMap<>();

    static {
        GRADE_LABELS.put("#1#", "不及格");
        GRADE_LABELS.put("#2#", "及格");
        GRADE_LABELS.put("#3#", "良好");
        GRADE_LABELS.put("#4#", "优秀");
    }

    @Autowired
    private PatrolAssessMapper mapper;

    /** 计划详情（编号/名称/状态）复用计划模块的权威查询 */
    @Autowired
    private PatrolPlanMapper planMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    // ==================== 预览 / 提交 / 查看 ====================

    /**
     * 考核弹窗预览：返回自动项实时统计（得分/等级/结论为空）。
     * <p>已考核的计划也会返回 assessed=true，避免前端在已考核状态下重复打开考核表单。
     */
    public PatrolAssessVO preview(String scheduleId) {
        PatrolScheduleDetailVO plan = requireCompletedPlan(scheduleId);
        PatrolAssessVO vo = mapper.selectStat(plan.getId());
        if (vo == null) {
            vo = new PatrolAssessVO();
        }
        vo.setScheduleId(plan.getId());
        vo.setPlanCode(plan.getCode());
        vo.setPlanTitle(plan.getTitle());
        vo.setAssessed(mapper.countBySchedule(plan.getId()) > 0);
        return vo;
    }

    /**
     * 提交考核：重算自动项 + 校验手工项 → 落库快照，返回考核结果。
     * <p>等级按得分分档推导（{@link #resolveGrade}），以服务端为准；请求里的 grade 不采用。
     *
     * @throws IllegalArgumentException 计划不存在/非已完成/已考核，或手工项不合法
     */
    public PatrolAssessVO submit(PatrolAssessRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        PatrolScheduleDetailVO plan = requireCompletedPlan(request.getScheduleId());
        if (mapper.countBySchedule(plan.getId()) > 0) {
            throw new IllegalArgumentException("该计划已完成考核，不允许重复考核");
        }
        Double score = parseScore(request.getScore());
        String grade = resolveGrade(score);
        String conclusion = CheckUtils.trim(request.getConclusion());
        if (conclusion != null && conclusion.length() > CONCLUSION_MAX_LENGTH) {
            throw new IllegalArgumentException("考核结论不能超过 " + CONCLUSION_MAX_LENGTH + " 字");
        }

        PatrolAssessVO stat = mapper.selectStat(plan.getId());
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();

        PatrolAssess entity = new PatrolAssess();
        entity.setId(shortIdGenerator.nextUUID(null));
        entity.setScheduleId(plan.getId());
        entity.setPlanCode(plan.getCode());
        entity.setPlanTitle(plan.getTitle());
        entity.setPlanSite(nvl(stat == null ? null : stat.getPlanSite()));
        entity.setPatrolledSite(nvl(stat == null ? null : stat.getPatrolledSite()));
        entity.setRecordCount(nvl(stat == null ? null : stat.getRecordCount()));
        entity.setIssueTotal(nvl(stat == null ? null : stat.getIssueTotal()));
        entity.setIssueFixed(nvl(stat == null ? null : stat.getIssueFixed()));
        entity.setIssueFixing(nvl(stat == null ? null : stat.getIssueFixing()));
        entity.setIssueOpen(nvl(stat == null ? null : stat.getIssueOpen()));
        entity.setScore(score);
        entity.setGrade(grade);
        entity.setConclusion(conclusion);
        entity.setAssessorId(operatorId);
        entity.setAssessorName(resolveOperatorName(operatorId));
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发下两次提交同时通过前置校验：唯一索引兜底，转成业务提示
            log.warn("巡查考核重复提交被唯一索引拦截：scheduleId={}", plan.getId());
            throw new IllegalArgumentException("该计划已完成考核，不允许重复考核");
        }
        return detail(plan.getId());
    }

    /** 查看考核（读快照）；未考核抛 400。 */
    public PatrolAssessVO detail(String scheduleId) {
        String sid = CheckUtils.trim(scheduleId);
        if (sid == null) {
            throw new IllegalArgumentException("计划 id 不能为空");
        }
        PatrolAssessVO vo = mapper.selectBySchedule(sid);
        if (vo == null) {
            throw new IllegalArgumentException("该计划尚未考核");
        }
        vo.setGradeLabel(DictUtils.label(GRADE_LABELS, vo.getGrade()));
        vo.setAssessed(true);
        return vo;
    }

    /** 已考核计划 id（列表批量判断「考核 / 查看考核」按钮）。 */
    public List<String> assessedScheduleIds(List<String> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = mapper.selectAssessedScheduleIds(scheduleIds);
        return ids == null ? Collections.emptyList() : ids;
    }

    // ==================== 校验 ====================

    /** 计划必须存在且已完成（#3#）：考核的唯一入口约束。 */
    private PatrolScheduleDetailVO requireCompletedPlan(String scheduleId) {
        String sid = CheckUtils.trim(scheduleId);
        if (sid == null) {
            throw new IllegalArgumentException("计划 id 不能为空");
        }
        PatrolScheduleDetailVO plan = planMapper.selectDetail(sid);
        if (plan == null) {
            throw new IllegalArgumentException("巡检计划不存在：" + sid);
        }
        if (!STATUS_COMPLETED.equals(plan.getStatusCode())) {
            throw new IllegalArgumentException("仅已完成的巡检计划可考核");
        }
        return plan;
    }

    /** 得分：必填，0~100，最多 1 位小数。 */
    private static Double parseScore(String raw) {
        String s = CheckUtils.trim(raw);
        if (s == null) {
            throw new IllegalArgumentException("请输入考核得分");
        }
        if (!SCORE_PATTERN.matcher(s).matches()) {
            throw new IllegalArgumentException("考核得分应为 0~100 的数字，最多 1 位小数");
        }
        return Double.valueOf(s);
    }

    /**
     * 得分 → 等级分档（唯一判定点，与前端 GRADE_RULES 保持一致）：
     * ≥90 优秀 #4# / 80~89 良好 #3# / 60~79 及格 #2# / <60 不及格 #1#。
     */
    private static String resolveGrade(Double score) {
        if (score >= 90) {
            return "#4#";
        }
        if (score >= 80) {
            return "#3#";
        }
        if (score >= 60) {
            return "#2#";
        }
        return "#1#";
    }

    /** 考核人姓名：优先用户表姓名，回退登录名，最后回退 id。 */
    private String resolveOperatorName(String userId) {
        String name = mapper.selectUserName(userId);
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        UserContext user = UserContextHolder.currentUser();
        if (user != null && user.getLoginName() != null && !user.getLoginName().trim().isEmpty()) {
            return user.getLoginName().trim();
        }
        return userId;
    }

    private static Integer nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
