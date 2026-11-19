package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信接收人行视图（短信管理 Tab 列表）。
 * <p>数据来源固定为接收人表（source=table；历史 config 虚拟行机制已移除）。
 */
@Data
public class SmsReceiverVO {

    /** 记录 id */
    private String id;

    /** 短信类型：daily=每日 8 点报 / alarm=报警短信 */
    private String bizType;

    /** 短信类型中文名（页面直接展示，不在前端写死字典） */
    private String bizTypeLabel;

    /** 姓名（页面新增/编辑必填） */
    private String name;

    /** 手机号码 */
    private String phone;

    /** 数据来源：固定 table */
    private String source;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
