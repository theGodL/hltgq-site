package com.qgyun.hltgq.hltgqsite.model.util;

/**
 * 异常信息提取（唯一实现点）：落 error_msg 列与对外提示的统一取数口径。
 * <p>历史上模型服务、天气/模型客户端与导入服务各抄了一份完全相同的
 * {@code message() == null ? getClass().getSimpleName() : message()} 与根因下钻逻辑。
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /** 异常可读信息（message 为空时退化为异常类名，保证 error_msg 不留空） */
    public static String message(Throwable e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    /**
     * 异常可读信息并截断（写 error_msg / message 列用，maxLength 如 200、500）。
     * <p>注意与 {@link #message(Throwable)} 的区别：入参为异常对象，第二个参数是长度上限。
     */
    public static String message(Throwable e, int maxLength) {
        String msg = message(e);
        return msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
    }

    /** 根因可读信息（沿 cause 链下钻到最后一层；HTTP/SDK 包装异常的真实原因在根因上） */
    public static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return message(root);
    }
}
