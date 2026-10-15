package com.qgyun.hltgq.hltgqsite.h5.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * H5 消息中心：消息接收规则表实体（某类消息发给哪些部门/岗位/角色/人员，告警可再限定站点）。
 * <p>target_type：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员；
 * target_id 为对应编码（部门/岗位/角色取 code、人员取 login_name），多个以英文逗号分隔。
 * <p>site_id：站点范围（仅 #1# 告警生效），站点档案主键（t_auto_hltgq_5nw74_vnqqef.id），
 * 多个以英文逗号分隔；空 = 不限站点（存量规则语义）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_message_rule\"")
public class MessageRule extends BaseWaterEntity {

    /** 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集 / #4# 值班提醒 / #5# 模型计算 */
    @TableField("\"message_type\"")
    private String messageType;

    /** 接收维度：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员 */
    @TableField("\"target_type\"")
    private String targetType;

    /** 目标编码，多个以英文逗号分隔 */
    @TableField("\"target_id\"")
    private String targetId;

    /** 站点范围（仅告警 #1# 生效）：站点档案主键，多个以英文逗号分隔；空 = 不限站点 */
    @TableField("\"site_id\"")
    private String siteId;
}
