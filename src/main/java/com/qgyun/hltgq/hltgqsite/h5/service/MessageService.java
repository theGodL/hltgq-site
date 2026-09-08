package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageQueryMapper;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageReceiveMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessagePageVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageSummaryVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * H5 消息中心服务：未读汇总 / 三类消息分页 / 标记已读，全部基于当前登录人接收记录。
 * <p>已读口径：接收记录 is_read=#2#；告警类再叠加业务状态过滤（已关闭告警不计未读）。
 */
@Service
public class MessageService {

    /** 消息类型合法编码 */
    private static final List<String> MESSAGE_TYPES = Arrays.asList("#1#", "#2#", "#3#");

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

    public MessageService(MessageQueryMapper queryMapper, MessageReceiveMapper receiveMapper) {
        this.queryMapper = queryMapper;
        this.receiveMapper = receiveMapper;
    }

    /** 未读数汇总（角标）：告警未读 JOIN 业务状态，投诉/意见直接按接收记录未读 */
    public MessageSummaryVO summary(String userId) {
        MessageSummaryVO vo = new MessageSummaryVO();
        vo.setAlertUnread(queryMapper.countAlert(userId));
        vo.setComplaintUnread(receiveMapper.countUnread(userId, "#2#"));
        vo.setSuggestionUnread(receiveMapper.countUnread(userId, "#3#"));
        vo.setTotalUnread(vo.getAlertUnread() + vo.getComplaintUnread() + vo.getSuggestionUnread());
        return vo;
    }

    /** 消息分页：按类型返回对应结构行，label 由后端权威映射填充 */
    public MessagePageVO page(String userId, String messageType, long page, long size) {
        requireMessageType(messageType);
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
        return receiveMapper.markRead(userId, messageType, messageIds);
    }

    /** 该类型全部标记已读 */
    public int readAll(String userId, String messageType) {
        requireMessageType(messageType);
        return receiveMapper.markReadAll(userId, messageType);
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
