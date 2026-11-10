package com.qgyun.hltgq.hltgqsite.auth;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求来源解析：从当前请求推导浏览器实际访问的协议、主机与端口，
 * 用于拼接登录跳转、平台网关等对外地址，避免把部署域名写死在代码或配置里。
 * <p>优先取反向代理头 X-Forwarded-Proto / X-Forwarded-Host / X-Forwarded-Port
 * （多级代理取首个值，即最靠近客户端的一跳），缺失时回退 Servlet 容器解析结果。
 * <p>主机名按字符白名单校验，非法 Host 头不参与拼接，调用方回退相对地址（防开放重定向）。
 */
public final class RequestOriginResolver {

    /** 默认端口：按协议省略端口号 */
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private RequestOriginResolver() {
    }

    /**
     * 解析浏览器实际访问的站点根地址（沿用请求端口）。
     *
     * @param request 当前请求
     * @return scheme://host[:port]；主机头缺失或非法时返回 null
     */
    public static String resolveOrigin(HttpServletRequest request) {
        return resolveOrigin(request, null);
    }

    /**
     * 解析浏览器实际访问的站点根地址，可强制指定端口（如三维系统固定端口部署）。
     *
     * @param request      当前请求
     * @param overridePort 端口覆盖值；null 表示沿用请求端口
     * @return scheme://host[:port]；主机头缺失或非法时返回 null
     */
    public static String resolveOrigin(HttpServletRequest request, Integer overridePort) {
        String scheme = firstHeader(request, "X-Forwarded-Proto");
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            scheme = request.getScheme();
        }
        scheme = scheme == null ? "http" : scheme.toLowerCase();

        String forwardedHost = firstHeader(request, "X-Forwarded-Host");
        Integer port = overridePort;
        String host;
        if (forwardedHost == null) {
            host = request.getServerName();
        } else {
            host = forwardedHost;
            // 转发主机可能自带端口（host:port 或 [IPv6]:port），先剥离再统一拼装
            int colon = host.lastIndexOf(':');
            if (colon > host.lastIndexOf(']')) {
                if (port == null) {
                    port = parsePort(host.substring(colon + 1));
                }
                host = host.substring(0, colon);
            }
        }
        if (port == null) {
            String forwardedPort = firstHeader(request, "X-Forwarded-Port");
            port = forwardedPort == null ? request.getServerPort() : parsePort(forwardedPort);
        }

        host = normalizeHost(host);
        if (host == null) {
            return null;
        }
        boolean defaultPort = ("http".equals(scheme) && port == HTTP_PORT)
                || ("https".equals(scheme) && port == HTTPS_PORT);
        if (port == null || port <= 0 || defaultPort) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    /**
     * 解析对外地址：配置项优先，未配置时按当前请求动态拼接。
     * <p>配置为完整地址（http/https 开头）原样返回；配置为以 / 开头的路径拼当前来源地址；
     * 配置为空则拼「当前来源地址 + defaultPath」；来源地址无法解析时退化为相对路径。
     *
     * @param request     当前请求
     * @param configured  配置值（完整地址 / 相对路径 / 空）
     * @param defaultPath 配置为空时的默认路径（以 / 开头）
     * @return 对外可访问地址
     */
    public static String resolveUrl(HttpServletRequest request, String configured, String defaultPath) {
        String value = configured == null ? "" : configured.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.isEmpty()) {
            value = defaultPath;
        } else if (!value.startsWith("/")) {
            value = "/" + value;
        }
        String origin = resolveOrigin(request);
        return origin == null ? value : origin + value;
    }

    /** 取首个头值（多级代理逗号分隔，首段最接近客户端） */
    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        value = (comma >= 0 ? value.substring(0, comma) : value).trim();
        return value.isEmpty() ? null : value;
    }

    /** 端口解析：1~65535 之外视为无效 */
    private static Integer parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            return port > 0 && port <= 65535 ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 主机名规范化与校验：域名 / IPv4 原样返回，IPv6 补方括号；含其它字符返回 null */
    private static String normalizeHost(String rawHost) {
        if (rawHost == null) {
            return null;
        }
        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) {
            return null;
        }
        if (host.matches("[A-Za-z0-9.\\-]+")) {
            return host;
        }
        if (host.indexOf(':') >= 0 && host.matches("[0-9A-Fa-f:.]+")) {
            return "[" + host + "]";
        }
        return null;
    }
}
