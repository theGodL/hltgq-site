package com.qgyun.hltgq.hltgqsite.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会话上下文服务单测：
 * 覆盖「携带 sessionId 后须 Redis 中存在有效会话才算登录」的严格解析（resolveUser）行为。
 */
class SessionContextServiceTest {

    private SessionContextService service;
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);

    @BeforeEach
    void setUp() {
        service = new SessionContextService();
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
    }

    /** Redis 有会话数据：正常解析（值带 JSON 引号时自动剥离） */
    @Test
    void resolveUser_withData_parsesContext() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("userId", "\"u1\"");
        entries.put("corpCode", "\"hltgq\"");
        when(hashOperations.entries("s1")).thenReturn(entries);

        UserContext user = service.resolveUser("s1");

        assertNotNull(user);
        assertEquals("u1", user.getUserId());
        assertEquals("hltgq", user.getCorpCode());
    }

    /** Redis 无会话数据（无效/过期会话）：抛 UnauthorizedException，视为未登录 */
    @Test
    void resolveUser_emptyEntries_throwsUnauthorized() {
        when(hashOperations.entries("s2")).thenReturn(Collections.emptyMap());

        assertThrows(UnauthorizedException.class, () -> service.resolveUser("s2"));
    }

    /** Redis 不可用：抛 SessionUnavailableException，禁止降级放行 */
    @Test
    void resolveUser_redisDown_throwsSessionUnavailable() {
        when(hashOperations.entries(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThrows(SessionUnavailableException.class, () -> service.resolveUser("s3"));
    }
}
