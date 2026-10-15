package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息发送设置：规则列表行视图。
 * <p>基础字段由 MessageRuleMapper.selectRulePage 分页取出；
 * messageTypeLabel/targetTypeLabel 与 targetNames/siteNames 由 Service 批量解析填充
 * （名称解析为展示字段，档案缺失项原样回退编码）。
 */
@Data
public class MessageRuleVO {

    /** 规则主键 */
    private String id;

    /** 消息类型编码：#1# 告警 / #2# 举报投诉 / #3# 意见征集 / #4# 值班提醒 / #5# 模型计算 */
    private String messageType;

    /** 消息类型名称 */
    private String messageTypeLabel;

    /** 接收维度编码：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员 */
    private String targetType;

    /** 接收维度名称 */
    private String targetTypeLabel;

    /** 目标编码（原始逗号拼接值，编辑回填用；人员为 login_name） */
    private String targetId;

    /** 目标名称（逗号拼接展示用） */
    private String targetNames;

    /** 站点范围（仅告警有值；原始逗号拼接站点主键，编辑回填用） */
    private String siteId;

    /** 站点名称（逗号拼接展示用，仅告警有值） */
    private String siteNames;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
