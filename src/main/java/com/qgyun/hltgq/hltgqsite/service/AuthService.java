package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.AuthResponseVO;

/**
 * 登录鉴权服务
 */
public interface AuthService {

    /**
     * 登录获取会话 ID
     *
     * @param key    32 位鉴权 key
     * @param secret 32 位鉴权 secret
     * @return 登录响应（含 sessionId）
     */
    AuthResponseVO login(String key, String secret);
}
