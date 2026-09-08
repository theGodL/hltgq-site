package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.entity.MessageReceive;
import com.qgyun.hltgq.hltgqsite.h5.entity.MessageRule;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageQueryMapper;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageReceiveMapper;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageRuleMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * H5 消息分发定时任务：扫描三类消息源的新消息（接收表无记录），按接收规则物化接收记录。
 * <p>分发模型：发送即建未读记录（is_read=#1#），阅读后 UPDATE 已读；
 * 某类型配置了规则时仅规则命中的登录人可见，未配置规则时全员可见（兜底）。
 * <p>幂等：唯一约束 uk_message_receive(message_type, message_id, user_id) ON CONFLICT 忽略重复；
 * 规则变化后增量消息按新规则分发，存量记录不回滚（已读状态保留）。
 */
@Service
public class MessageDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatchJob.class);

    /** 单次插入批大小（用户维度），防一次插入行数过大 */
    private static final int INSERT_BATCH_SIZE = 500;

    /** 每类消息单次任务最多处理条数，防首次全量分发打爆 */
    private static final int MAX_MESSAGES_PER_TYPE = 500;

    /** 接收记录创建人标记 */
    private static final String DISPATCH_CREATOR = "hltgq-message-dispatch";

    private final MessageQueryMapper queryMapper;
    private final MessageReceiveMapper receiveMapper;
    private final MessageRuleMapper ruleMapper;
    private final ShortIdGenerator shortIdGenerator;

    @Value("${message.alert-window-days:30}")
    private int alertWindowDays;

    public MessageDispatchJob(MessageQueryMapper queryMapper,
                              MessageReceiveMapper receiveMapper,
                              MessageRuleMapper ruleMapper,
                              ShortIdGenerator shortIdGenerator) {
        this.queryMapper = queryMapper;
        this.receiveMapper = receiveMapper;
        this.ruleMapper = ruleMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    /** 定时分发：每 5 分钟（message.dispatch-cron） */
    @Scheduled(cron = "${message.dispatch-cron:0 */5 * * * ?}")
    public void scheduledDispatch() {
        try {
            dispatch();
        } catch (Exception e) {
            log.error("h5 message dispatch failed", e);
        }
    }

    /** 分发主流程：按类型扫描未分发消息 → 解析接收人 → 物化接收记录 */
    private void dispatch() {
        Map<String, List<MessageRule>> rulesByType = ruleMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(MessageRule::getMessageType));

        // 告警：仅已确认/处理中 + 时间窗口内（老告警不参与首次分发）
        LocalDateTime alertSince = LocalDateTime.now().minusDays(Math.max(alertWindowDays, 1));
        List<String> alertIds = limit(queryMapper.selectUndispatchedAlertIds(alertSince));
        int alertRows = dispatchType("#1#", alertIds, rulesByType.get("#1#"));

        List<String> complaintIds = limit(queryMapper.selectUndispatchedComplaintIds());
        int complaintRows = dispatchType("#2#", complaintIds, rulesByType.get("#2#"));

        List<String> suggestionIds = limit(queryMapper.selectUndispatchedSuggestionIds());
        int suggestionRows = dispatchType("#3#", suggestionIds, rulesByType.get("#3#"));

        if (alertRows + complaintRows + suggestionRows > 0) {
            log.info("h5 message dispatch done: alert={} complaint={} suggestion={} rows={}",
                    alertIds.size(), complaintIds.size(), suggestionIds.size(),
                    alertRows + complaintRows + suggestionRows);
        }
    }

    /** 单类型分发：解析接收人（无规则 → 全员），分批物化接收记录 */
    private int dispatchType(String messageType, List<String> messageIds, List<MessageRule> typeRules) {
        if (messageIds.isEmpty()) {
            return 0;
        }
        Set<String> userIds = resolveUserIds(typeRules);
        if (userIds.isEmpty()) {
            log.warn("h5 message dispatch skipped: messageType={} has no matched receivers", messageType);
            return 0;
        }
        int rows = 0;
        for (String messageId : messageIds) {
            for (List<String> batch : partition(new ArrayList<>(userIds), INSERT_BATCH_SIZE)) {
                List<MessageReceive> receives = new ArrayList<>(batch.size());
                for (String userId : batch) {
                    MessageReceive receive = new MessageReceive();
                    receive.setId(shortIdGenerator.nextUUID(receive));
                    receive.setUserId(userId);
                    receive.setMessageType(messageType);
                    receive.setMessageId(messageId);
                    receive.setCorpCode("hltgq");
                    receive.setCreatedBy(DISPATCH_CREATOR);
                    receives.add(receive);
                }
                rows += receiveMapper.batchInsert(receives);
            }
        }
        return rows;
    }

    /** 解析接收人：无规则 → 全员；有规则 → 各维度用户主键并集去重 */
    private Set<String> resolveUserIds(List<MessageRule> typeRules) {
        Set<String> userIds = new LinkedHashSet<>();
        if (typeRules == null || typeRules.isEmpty()) {
            userIds.addAll(queryMapper.selectAllUserIds());
            return userIds;
        }
        for (MessageRule rule : typeRules) {
            List<String> targets = splitTargets(rule.getTargetId());
            if (targets.isEmpty()) {
                continue;
            }
            switch (rule.getTargetType()) {
                case "#org#":
                    userIds.addAll(queryMapper.selectUserIdsByOrgCodes(targets));
                    break;
                case "#position#":
                    userIds.addAll(queryMapper.selectUserIdsByPositionCodes(targets));
                    break;
                case "#role#":
                    userIds.addAll(queryMapper.selectUserIdsByRoleCodes(targets));
                    break;
                case "#user#":
                    userIds.addAll(queryMapper.selectUserIdsByLoginNames(targets));
                    break;
                default:
                    log.warn("h5 message rule ignored: unknown targetType={} ruleId={}",
                            rule.getTargetType(), rule.getId());
            }
        }
        return userIds;
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

    /** 单次任务消息数上限，超出截断（下轮继续） */
    private List<String> limit(List<String> ids) {
        return ids.size() > MAX_MESSAGES_PER_TYPE ? new ArrayList<>(ids.subList(0, MAX_MESSAGES_PER_TYPE)) : ids;
    }

    /** 固定大小分批 */
    private List<List<String>> partition(List<String> list, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}
