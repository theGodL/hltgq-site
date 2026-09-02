package com.qgyun.hltgq.hltgqsite.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 登录验证拦截器单测：
 * 覆盖「所有页面、接口统一 sessionId 验证，携带 sessionId 且 Redis 会话有效才算登录」。
 */
class AuthInterceptorTest {

    private static final String LOGIN_PAGE_URL = "http://220.179.1.110:8081/login/user/login";

    private AuthInterceptor interceptor;
    private SessionContextService sessionContextService;
    private RolePermissionService rolePermissionService;
    private MockHttpServletResponse response;
    private HandlerMethod handler;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        interceptor = new AuthInterceptor();
        // spy 保留 extractSessionId 真实提取逻辑（Authorization/Cookie → sessionId）
        sessionContextService = spy(new SessionContextService());
        rolePermissionService = mock(RolePermissionService.class);
        ReflectionTestUtils.setField(interceptor, "sessionContextService", sessionContextService);
        ReflectionTestUtils.setField(interceptor, "rolePermissionService", rolePermissionService);
        ReflectionTestUtils.setField(interceptor, "loginPageUrl", LOGIN_PAGE_URL);
        ReflectionTestUtils.setField(interceptor, "whiteListConfig", "");
        response = new MockHttpServletResponse();
        handler = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        UserContextHolder.clear();
    }

    /** 无凭证 API 请求：拦截并返回 401 JSON */
    @Test
    void apiWithoutSession_returns401() throws Exception {
        MockHttpServletRequest request = apiRequest("/alert/page");

        boolean passed = interceptor.preHandle(request, response, handler);

        assertFalse(passed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("未登录"));
    }

    /** 无凭证页面请求（Accept: text/html）：302 跳转平台登录页 */
    @Test
    void pageWithoutSession_redirects302() throws Exception {
        MockHttpServletRequest request = apiRequest("/gate-monitor-page.html");
        request.addHeader("Accept", "text/html,application/xhtml+xml");

        boolean passed = interceptor.preHandle(request, response, handler);

        assertFalse(passed);
        assertEquals(302, response.getStatus());
        assertEquals(LOGIN_PAGE_URL, response.getRedirectedUrl());
    }

    /** Authorization 头携带有效 sessionId：放行并写入用户上下文 */
    @Test
    void authorizationHeaderWithValidSession_passes() throws Exception {
        MockHttpServletRequest request = apiRequest("/alert/page");
        request.addHeader("Authorization", "dev_hltgq_session:NuslwIVlegG7AEpvH4E");
        UserContext user = new UserContext();
        user.setUserId("u1");
        doReturn(user).when(sessionContextService).resolveUser("dev_hltgq_session:NuslwIVlegG7AEpvH4E");

        boolean passed = interceptor.preHandle(request, response, handler);

        assertTrue(passed);
        assertEquals(user, UserContextHolder.currentUser());
        verify(sessionContextService).resolveUser("dev_hltgq_session:NuslwIVlegG7AEpvH4E");
    }

    /** Cookie sessionId 携带有效会话：放行 */
    @Test
    void cookieSessionIdWithValidSession_passes() throws Exception {
        MockHttpServletRequest request = apiRequest("/alert/page");
        request.setCookies(new Cookie("sessionId", "dev_hltgq_session:NuslwIVlegG7AEpvH4E"));
        doReturn(new UserContext()).when(sessionContextService).resolveUser(anyString());

        boolean passed = interceptor.preHandle(request, response, handler);

        assertTrue(passed);
    }

    /** Authorization 带 Bearer 前缀：剥离后按有效会话放行 */
    @Test
    void authorizationBearerWithValidSession_passes() throws Exception {
        MockHttpServletRequest request = apiRequest("/alert/page");
        request.addHeader("Authorization", "Bearer dev_hltgq_session:NuslwIVlegG7AEpvH4E");
        doReturn(new UserContext()).when(sessionContextService).resolveUser("dev_hltgq_session:NuslwIVlegG7AEpvH4E");

        boolean passed = interceptor.preHandle(request, response, handler);

        assertTrue(passed);
        verify(sessionContextService).resolveUser("dev_hltgq_session:NuslwIVlegG7AEpvH4E");
    }

    /** 携带无效/过期 sessionId（Redis 无该会话）：按未登录拦截，返回 401 */
    @Test
    void invalidSession_returns401() throws Exception {
        MockHttpServletRequest request = apiRequest("/alert/page");
        request.addHeader("Authorization", "dev_hltgq_session:ExpiredToken123");
        doThrow(new UnauthorizedException("未登录或会话已过期"))
                .when(sessionContextService).resolveUser("dev_hltgq_session:ExpiredToken123");

        boolean passed = interceptor.preHandle(request, response, handler);

        assertFalse(passed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("未登录"));
    }

    /** 会话服务不可用（Redis 故障）：503，禁止降级放行 */
    @Test
    void sessionServiceDown_returns503() throws Exception {
        MockHttpServletRequest request = apiRequest("/alert/page");
        request.addHeader("Authorization", "dev_hltgq_session:NuslwIVlegG7AEpvH4E");
        doThrow(new SessionUnavailableException("会话服务不可用，请稍后重试", new RuntimeException("redis down")))
                .when(sessionContextService).resolveUser(anyString());

        boolean passed = interceptor.preHandle(request, response, handler);

        assertFalse(passed);
        assertEquals(503, response.getStatus());
    }

    /** 白名单 /auth/login 无凭证：放行且不做会话校验 */
    @Test
    void whitelistedLoginPath_passes() throws Exception {
        MockHttpServletRequest request = apiRequest("/auth/login");

        boolean passed = interceptor.preHandle(request, response, handler);

        assertTrue(passed);
        verify(sessionContextService, never()).extractSessionId(any());
    }

    /** 静态资源（非 HandlerMethod）携带有效会话：放行，但不写入用户上下文 */
    @Test
    void staticResourceWithValidSession_passesWithoutContextSet() throws Exception {
        MockHttpServletRequest request = apiRequest("/lib/echarts.min.js");
        request.addHeader("Authorization", "dev_hltgq_session:NuslwIVlegG7AEpvH4E");
        doReturn(new UserContext()).when(sessionContextService).resolveUser("dev_hltgq_session:NuslwIVlegG7AEpvH4E");

        boolean passed = interceptor.preHandle(request, response, new Object());

        assertTrue(passed);
        verify(sessionContextService).resolveUser("dev_hltgq_session:NuslwIVlegG7AEpvH4E");
        assertNull(UserContextHolder.currentUser());
    }

    /** 静态资源无凭证：同样拦截（所有页面、资源统一验证） */
    @Test
    void staticResourceWithoutSession_blocked() throws Exception {
        MockHttpServletRequest request = apiRequest("/lib/echarts.min.js");

        boolean passed = interceptor.preHandle(request, response, new Object());

        assertFalse(passed);
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest apiRequest(String uri) {
        // 不预设 Accept：API 用例（无 Accept）走 401 JSON 分支，页面用例自行设置 text/html 走 302 分支
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("");
        return request;
    }
}
