package com.qgyun.hltgq.hltgqsite.h5.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * H5 消息中心：消息接收表实体（发送时建未读记录，阅读后 UPDATE 已读）。
 * <p>is_read：#1# 未读（发送时默认）、#2# 已读（查看后 UPDATE）；
 * message_type：#1# 告警、#2# 举报投诉、#3# 意见征集。
 * <p>幂等：唯一约束 uk_message_receive(message_type, message_id, user_id)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\"")
public class MessageReceive extends BaseWaterEntity {

    /** 收信人主键（t_apaas_uc_user.id） */
    @TableField("\"user_id\"")
    private String userId;

    /** 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集 */
    @TableField("\"message_type\"")
    private String messageType;

    /** 消息主键（对应告警表/投诉表/意见表的 id） */
    @TableField("\"message_id\"")
    private String messageId;

    /** 已读时间（未读为 null） */
    @TableField("\"read_time\"")
    private LocalDateTime readTime;

    /** 已读状态：#1# 未读 / #2# 已读 */
    @TableField("\"is_read\"")
    private String isRead;
}
