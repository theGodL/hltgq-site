package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

/**
 * 消息发送设置：规则新增/编辑请求体。
 * <p>target_id 传逗号分隔编码（部门/岗位/角色传 code，人员传 login_name）；
 * site_id 仅告警（#1#）允许：逗号分隔站点主键、空表示不限站点，其余类型忽略并归一化为空。
 */
@Data
public class MessageRuleSaveVO {

    /** 消息类型编码 */
    private String messageType;

    /** 接收维度编码 */
    private String targetType;

    /** 目标编码，多个以英文逗号分隔 */
    private String targetId;

    /** 站点范围（仅告警）：站点主键逗号分隔，空 = 不限站点 */
    private String siteId;
}
