package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.entity.MessageRule;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageQueryMapper;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageReceiveMapper;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageRuleMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessagePageVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * H5 消息中心服务：未读汇总 / 三类消息分页 / 标记已读，全部基于当前登录人接收记录。
 * <p>同步模型（无定时任务）：summary/page 被调用时按规则把当前登录人可见消息补建为接收记录
 * （未读 #1#，NOT EXISTS 幂等绝不重复），阅读后 UPDATE 已读 #2#；
 * 可见性规则：无规则全员可见，有规则任一命中即可见（#user# 匹配 login_name，
 * #org#/#position#/#role# 匹配用户所属编码），角色仅直接指派。
 * <p>已读口径：接收记录 is_read=#2#；告警类再叠加业务状态过滤（已关闭告警不计未读）。
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    /** 消息类型合法编码 */
    private static final List<String> MESSAGE_TYPES = Arrays.asList("#1#", "#2#", "#3#");

    /** 同步防抖窗口：同一用户该窗口内不重复同步（幂等，防抖仅减查询开销） */
    private static final Duration SYNC_COOLDOWN = Duration.ofSeconds(30);

    /** 单次批量标记已读条数上限（防 IN 列表过长，全量用 read-all） */
    private static final int MAX_READ_BATCH = 200;

    /** 投诉类型编码 → 权威名称 */
    private static final String[][] COMPLAINT_LABELS = {
            {"#1#", "流域内违法违规行为"}, {"#jsud#", "业务办理态度问题"},
            {"#cvky#", "不文明现象"}, {"#mnuu#", "其他"}
    };

    /** 意见类型编码 → 权威名称 */
    private static final String[][] SUGGESTION_LABELS = {
            {"#1#", "水资源管理"}, {"#eosz#", "防洪减灾"}, {"#olcg#", "水生态保护"},
            {"#avxv#", "公共服务"}, {"#douo#", "其他建议"}
    };

    private final MessageQueryMapper queryMapper;
    private final MessageReceiveMapper receiveMapper;
    private final MessageRuleMapper ruleMapper;
    private final StringRedisTemplate redisTemplate;

    public MessageService(MessageQueryMapper queryMapper,
                          MessageReceiveMapper receiveMapper,
                          MessageRuleMapper ruleMapper,
                          StringRedisTemplate redisTemplate) {
        this.queryMapper = queryMapper;
        this.receiveMapper = receiveMapper;
        this.ruleMapper = ruleMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 未读数汇总（角标）：先按需同步接收记录，再统计三类未读。
     * <p>前端进入首页时调用本接口即可让红点实时准确：新消息在首次调用时被补建为未读记录。
     */
    public MessageSummaryVO summary(String userId) {
        syncForUser(userId);
        MessageSummaryVO vo = new MessageSummaryVO();
        vo.setAlertUnread(receiveMapper.countAlertUnread(userId));
        vo.setComplaintUnread(receiveMapper.countUnread(userId, "#2#"));
        vo.setSuggestionUnread(receiveMapper.countUnread(userId, "#3#"));
        vo.setTotalUnread(vo.getAlertUnread() + vo.getComplaintUnread() + vo.getSuggestionUnread());
        return vo;
    }

    /** 消息分页：按类型返回对应结构行，label 由后端权威映射填充；前置同步兜底（深链接直达列表） */
    public MessagePageVO page(String userId, String messageType, long page, long size) {
        requireMessageType(messageType);
        syncForUser(userId);
        long current = page > 0 ? page : 1;
        long pageSize = size > 0 ? size : 10;
        int limit = (int) Math.min(pageSize, 100);
        int offset = (int) ((current - 1) * pageSize);

        MessagePageVO vo = new MessagePageVO();
        vo.setCurrent(current);
        vo.setSize(pageSize);

        switch (messageType) {
            case "#1#": {
                long total = queryMapper.countAlert(userId);
                List<MessagePageVO.AlertMessage> rows =
                        queryMapper.selectAlertPage(userId, limit, offset);
                vo.setTotal(total);
                vo.setPages(pages(total, pageSize));
                vo.setRecords(rows);
                break;
            }
            case "#2#": {
                long total = queryMapper.countComplaint(userId);
                List<MessagePageVO.ComplaintMessage> rows =
                        queryMapper.selectComplaintPage(userId, limit, offset);
                rows.forEach(r -> r.setComplaintTypeLabel(label(r.getComplaintType(), COMPLAINT_LABELS)));
                vo.setTotal(total);
                vo.setPages(pages(total, pageSize));
                vo.setRecords(rows);
                break;
            }
            default: {
                long total = queryMapper.countSuggestion(userId);
                List<MessagePageVO.SuggestionMessage> rows =
                        queryMapper.selectSuggestionPage(userId, limit, offset);
                rows.forEach(r -> r.setSuggestionTypeLabel(label(r.getSuggestionType(), SUGGESTION_LABELS)));
                vo.setTotal(total);
                vo.setPages(pages(total, pageSize));
                vo.setRecords(rows);
                break;
            }
        }
        return vo;
    }

    /** 批量标记已读（幂等） */
    public int read(String userId, String messageType, List<String> messageIds) {
        requireMessageType(messageType);
        if (messageIds == null || messageIds.isEmpty()) {
            throw new IllegalArgumentException("messageIds 不能为空");
        }
        if (messageIds.size() > MAX_READ_BATCH) {
            throw new IllegalArgumentException("messageIds 单次最多 " + MAX_READ_BATCH + " 条，全量已读请用 read-all");
        }
        return receiveMapper.markRead(userId, messageType, messageIds);
    }

    /** 该类型全部标记已读 */
    public int readAll(String userId, String messageType) {
        requireMessageType(messageType);
        return receiveMapper.markReadAll(userId, messageType);
    }

    /**
     * 按需同步：把当前登录人可见的三类消息补建为未读接收记录。
     * <p>Redis SETNX 防抖（窗口内重复调用跳过）；可见性规则命中才补建；
     * NOT EXISTS 幂等保证已建记录（含已读过的）绝不重复、绝不回滚。
     * <p>维度按需查询：仅查询规则实际涉及的维度（无岗位规则不查岗位表，避免无关 SQL 报错影响同步）；
     * 同步异常降级为日志（消息接口可用性优先），并释放防抖 key 允许下次调用立即重试。
     */
    private void syncForUser(String userId) {
        String syncKey = "h5:msg:sync:" + userId;
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(syncKey, "1", SYNC_COOLDOWN);
            if (acquired == null || !acquired) {
                return;
            }
        } catch (Exception e) {
            // Redis 异常时直接同步，可用性优先
        }

        try {
            Map<String, List<MessageRule>> rulesByType = ruleMapper.selectList(null).stream()
                    .collect(Collectors.groupingBy(MessageRule::getMessageType));

            Set<String> ruleDims = new HashSet<>();
            for (List<MessageRule> rules : rulesByType.values()) {
                for (MessageRule rule : rules) {
                    if (rule.getTargetType() != null) {
                        ruleDims.add(rule.getTargetType());
                    }
                }
            }

            String loginName = ruleDims.contains("#user#") ? queryMapper.selectLoginNameById(userId) : null;
            List<String> deptCodes = ruleDims.contains("#org#") ? queryMapper.selectDeptCodesByUserId(userId) : null;
            List<String> positionCodes = ruleDims.contains("#position#") ? queryMapper.selectPositionCodesByUserId(userId) : null;
            List<String> roleCodes = ruleDims.contains("#role#") ? queryMapper.selectRoleCodesByUserId(userId) : null;

            boolean alertVisible = visible(rulesByType.get("#1#"), loginName, deptCodes, positionCodes, roleCodes);
            boolean complaintVisible = visible(rulesByType.get("#2#"), loginName, deptCodes, positionCodes, roleCodes);
            boolean suggestionVisible = visible(rulesByType.get("#3#"), loginName, deptCodes, positionCodes, roleCodes);
            int alertRows = alertVisible ? queryMapper.syncAlertReceives(userId) : 0;
            int complaintRows = complaintVisible ? queryMapper.syncComplaintReceives(userId) : 0;
            int suggestionRows = suggestionVisible ? queryMapper.syncSuggestionReceives(userId) : 0;
            log.info("h5 message sync done: userId={}, loginName={}, alertVisible={} rows={}, complaintVisible={} rows={}, suggestionVisible={} rows={}",
                    userId, loginName, alertVisible, alertRows, complaintVisible, complaintRows, suggestionVisible, suggestionRows);
        } catch (Exception e) {
            log.error("h5 message sync failed: userId={}", userId, e);
            try {
                redisTemplate.delete(syncKey);
            } catch (Exception ignored) {
                // Redis 异常忽略，下次调用防抖过期后自动重试
            }
        }
    }

    /** 可见性判定：无规则 → 全员可见；有规则 → 任一规则命中即可见 */
    private boolean visible(List<MessageRule> typeRules, String loginName,
                            List<String> deptCodes, List<String> positionCodes, List<String> roleCodes) {
        if (typeRules == null || typeRules.isEmpty()) {
            return true;
        }
        for (MessageRule rule : typeRules) {
            List<String> targets = splitTargets(rule.getTargetId());
            if (targets.isEmpty()) {
                continue;
            }
            String targetType = rule.getTargetType();
            if (targetType == null) {
                continue;
            }
            switch (targetType) {
                case "#user#":
                    if (loginName != null && targets.contains(loginName)) {
                        return true;
                    }
                    break;
                case "#org#":
                    if (intersects(targets, deptCodes)) {
                        return true;
                    }
                    break;
                case "#position#":
                    if (intersects(targets, positionCodes)) {
                        return true;
                    }
                    break;
                case "#role#":
                    if (intersects(targets, roleCodes)) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    /** 两集合交集非空 */
    private boolean intersects(List<String> targets, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        for (String code : codes) {
            if (targets.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** target_id 逗号分割，去空白与空段 */
    private List<String> splitTargets(String targetId) {
        if (targetId == null || targetId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> targets = new ArrayList<>();
        for (String part : targetId.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                targets.add(trimmed);
            }
        }
        return targets;
    }

    private void requireMessageType(String messageType) {
        if (messageType == null || !MESSAGE_TYPES.contains(messageType)) {
            throw new IllegalArgumentException("messageType 仅支持 #1# 告警 / #2# 举报投诉 / #3# 意见征集");
        }
    }

    private long pages(long total, long pageSize) {
        return (total + pageSize - 1) / pageSize;
    }

    private String label(String code, String[][] mapping) {
        if (code == null) {
            return null;
        }
        for (String[] pair : mapping) {
            if (pair[0].equals(code)) {
                return pair[1];
            }
        }
        return code;
    }
}
