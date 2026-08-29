package com.qgyun.hltgq.hltgqsite.model.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * 下载响应头工具：生成 RFC 5987 兼容的 Content-Disposition。
 * <p>HTTP 响应头仅允许 ISO-8859-1 字符，中文文件名直拼会被 Tomcat 9 严格校验拒绝
 * （header 被移除，浏览器回退用 URL 末段作文件名）。本工具输出 ASCII 兜底 filename
 * + UTF-8 百分号编码的 filename*，现代浏览器优先取 filename*，保证中文文件名正确。
 */
public final class DownloadHeaderUtils {

    private DownloadHeaderUtils() {
    }

    /**
     * 生成 attachment 型 Content-Disposition 头值。
     * 如：attachment; filename="template.xlsx"; filename*=UTF-8''%E9%85%8D%E6%B0%B4...
     */
    public static String attachment(String fileName) {
        // ASCII 兜底：非 ASCII 字符替换为下划线，兼容严格环境与旧客户端
        String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encoded = ascii;
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
