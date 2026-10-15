package com.qgyun.hltgq.hltgqsite.model.util;

/**
 * 日志载荷截断（唯一实现点）：外部系统请求/响应报文落日志时控制长度。
 * <p>历史上模型客户端、档案客户端与三个天气客户端各抄一份同构的
 * {@code summarize(String)}：头部截断版与「头部 + 尾部」版输出文本逐字相同，仅长度上限不同。
 */
public final class LogTrimUtils {

    private static final String NULL_BODY = "null";

    private LogTrimUtils() {
    }

    /** 只保留头部：超长输出「前 headLimit 字符 + ...(截断N字符)」 */
    public static String summarize(String body, int headLimit) {
        return summarize(body, headLimit, 0);
    }

    /**
     * 头部 + 尾部：超长输出「前 headLimit 字符 + ...(截断N字符)... + 后 tailLimit 字符」，
     * 兼顾头部字段结构与尾部行数据；tailLimit 非正数时退化为只留头部。
     */
    public static String summarize(String body, int headLimit, int tailLimit) {
        if (body == null) {
            return NULL_BODY;
        }
        if (tailLimit <= 0) {
            if (body.length() <= headLimit) {
                return body;
            }
            return body.substring(0, headLimit) + "...(截断" + body.length() + "字符)";
        }
        if (body.length() <= headLimit + tailLimit) {
            return body;
        }
        int tail = Math.min(tailLimit, body.length() - headLimit);
        return body.substring(0, headLimit)
                + "...(截断" + body.length() + "字符)..."
                + body.substring(body.length() - tail);
    }
}
