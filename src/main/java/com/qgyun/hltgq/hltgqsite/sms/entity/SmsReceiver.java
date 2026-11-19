package com.qgyun.hltgq.hltgqsite.sms.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短信接收人表实体（{@code /message-rule-setting.html}「短信管理」Tab 维护）。
 * <p>biz_type：daily=每日 8 点报 / alarm=报警短信（雨量告警与水位涨幅共用同一清单）。
 * <p>发送取人口径见 {@link com.qgyun.hltgq.hltgqsite.sms.service.SmsReceiverService#resolvePhones}：
 * 完全以本表为准（页面增删改即生效；未建表或某类型 0 人 = 该类短信不发送）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_sms_receiver\"")
public class SmsReceiver extends BaseWaterEntity {

    /** 短信类型：daily=每日 8 点报 / alarm=报警短信 */
    @TableField("\"biz_type\"")
    private String bizType;

    /** 接收人姓名（必填，最长 32 字） */
    @TableField("\"name\"")
    private String name;

    /** 手机号码（11 位） */
    @TableField("\"phone\"")
    private String phone;
}
