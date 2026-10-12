package com.qgyun.hltgq.hltgqsite.duty;

/**
 * 值班交接班业务状态冲突（映射 409）。
 * <p>用于「仅未开始的排班可编辑/删除」「该排班已完成，无法开始值班」
 * 「当前记录不在值班中」「该记录已完成交班」等状态机冲突场景，
 * 由 GlobalExceptionHandler 统一翻译为 {@code 409 {"message":"..."}}。
 */
public class DutyConflictException extends RuntimeException {

    public DutyConflictException(String message) {
        super(message);
    }
}
