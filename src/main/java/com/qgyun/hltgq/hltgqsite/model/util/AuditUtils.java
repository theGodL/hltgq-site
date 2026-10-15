package com.qgyun.hltgq.hltgqsite.model.util;

import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;

import java.time.LocalDateTime;

/**
 * 平台标准审计字段写入（唯一实现点）：corp_code / created_at / created_by / updated_at / updated_by 五连写。
 * <p>历史上雨量补偿、阈值设置各抄一份 {@code applyCreateAudit()}，模型需水/决策服务各抄一份
 * {@code fillBase()}，实现完全相同（仅日志前缀不同）。操作人、企业编码取值见 {@link OperatorUtils}。
 */
public final class AuditUtils {

    private AuditUtils() {
    }

    /** 新增审计五连写（时间取当前时刻；operator/corpCode 由调用方按会话或配置取值） */
    public static void fillCreate(BaseWaterEntity entity, String operator, String corpCode) {
        fillCreate(entity, LocalDateTime.now(), operator, corpCode);
    }

    /** 新增审计五连写（同一批次共用一个时间戳，模型批量落库用） */
    public static void fillCreate(BaseWaterEntity entity, LocalDateTime now, String operator, String corpCode) {
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
    }
}
