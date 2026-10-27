package com.qgyun.hltgq.hltgqsite.patrol;

/**
 * 巡检记录业务状态冲突（映射 409）。
 * <p>用于「已提交的巡检记录不可编辑/不可重复提交」等状态机冲突场景，
 * 由 GlobalExceptionHandler 统一翻译为 {@code 409 {"message":"..."}}；
 * 参数缺失、记录不存在等仍由 IllegalArgumentException 返回 400。
 */
public class PatrolRecordConflictException extends RuntimeException {

    public PatrolRecordConflictException(String message) {
        super(message);
    }
}
