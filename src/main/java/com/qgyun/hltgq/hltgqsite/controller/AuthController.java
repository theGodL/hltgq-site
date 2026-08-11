package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.service.AuthService;
import com.qgyun.hltgq.hltgqsite.vo.AuthRequestVO;
import com.qgyun.hltgq.hltgqsite.vo.AuthResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录鉴权接口
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 登录接口
     * <p>先校验 key 和 secret，通过后调用外部登录接口获取 sessionId。
     *
     * @param request 包含 key 和 secret 的请求体
     * @return 登录响应（含 sessionId）
     */
    @PostMapping("/login")
    public AuthResponseVO login(@RequestBody AuthRequestVO request) {
        return authService.login(request.getKey(), request.getSecret());
    }
}
