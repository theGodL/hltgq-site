package com.qgyun.hltgq.hltgqsite.h5.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * H5 消息中心：消息接收规则表实体（某类消息发给哪些部门/岗位/角色/人员）。
 * <p>target_type：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员；
 * target_id 为对应编码（部门/岗位/角色取 code、人员取 login_name），多个以英文逗号分隔。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_message_rule\"")
public class MessageRule extends BaseWaterEntity {

    /** 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集 */
    @TableField("\"message_type\"")
    private String messageType;

    /** 接收维度：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员 */
    @TableField("\"target_type\"")
    private String targetType;

    /** 目标编码，多个以英文逗号分隔 */
    @TableField("\"target_id\"")
    private String targetId;
}
