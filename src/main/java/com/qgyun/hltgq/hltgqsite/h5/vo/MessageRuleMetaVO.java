package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.util.List;

/**
 * 消息发送设置：页面元数据（消息类型与接收维度字典，前端不写死）。
 * <p>siteSupported 标识该类型是否支持站点范围（仅告警）；
 * extraReceiver 标识该类型配置语义为附加接收人（值班/模型：原有定向不变，配置对象额外收到）；
 * desc 为各类型语义说明（页面提示文案）。
 */
@Data
public class MessageRuleMetaVO {

    /** 消息类型字典 */
    private List<MessageTypeMeta> messageTypes;

    /** 接收维度字典 */
    private List<TargetTypeMeta> targetTypes;

    /** 消息类型元数据 */
    @Data
    public static class MessageTypeMeta {

        /** 类型编码 */
        private String value;

        /** 类型名称 */
        private String label;

        /** 是否支持站点范围（仅告警 true） */
        private boolean siteSupported;

        /** 是否附加接收人语义（值班/模型 true：无规则不影响原有定向） */
        private boolean extraReceiver;

        /** 语义说明（页面提示文案） */
        private String desc;
    }

    /** 接收维度元数据 */
    @Data
    public static class TargetTypeMeta {

        /** 维度编码 */
        private String value;

        /** 维度名称 */
        private String label;
    }
}
