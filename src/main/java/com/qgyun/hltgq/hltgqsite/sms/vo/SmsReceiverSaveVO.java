package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

/**
 * 短信接收人保存入参（新增/编辑）。
 * <p>编辑时 bizType 不可修改（页面锁定）；姓名与手机号可改（手机号可多个）。
 * <p>新增/编辑时 phone 均支持逗号分隔多个号码，合并存为一条记录（英文逗号分隔文本）。
 */
@Data
public class SmsReceiverSaveVO {

    /** 短信类型：daily=每日 8 点报 / evening=晚 8 点报 / alarm=报警短信（新增必填；编辑时忽略） */
    private String bizType;

    /** 姓名（选填，填写时最长 32 字） */
    private String name;

    /** 手机号码（必填，1 开头 11 位；支持英文/中文逗号等分隔多个，最多 10 个） */
    private String phone;
}
