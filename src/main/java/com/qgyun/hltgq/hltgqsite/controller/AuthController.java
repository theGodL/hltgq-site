package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.archive.client.ArchiveClient;
import com.qgyun.hltgq.hltgqsite.archive.service.ArchiveSyncService;
import com.qgyun.hltgq.hltgqsite.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsite.auth.ThreeDimensionalSsoService;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.service.AuthService;
import com.qgyun.hltgq.hltgqsite.vo.ArchiveSsoVO;
import com.qgyun.hltgq.hltgqsite.vo.AuthRequestVO;
import com.qgyun.hltgq.hltgqsite.vo.AuthResponseVO;
import com.qgyun.hltgq.hltgqsite.vo.CurrentUserVO;
import com.qgyun.hltgq.hltgqsite.vo.ThreeDimensionalTokenVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录鉴权接口
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionContextService sessionContextService;

    @Autowired
    private ThreeDimensionalSsoService threeDimensionalSsoService;

    @Autowired
    private ArchiveSyncService archiveSyncService;

    @Autowired
    private ArchiveClient archiveClient;

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

    /**
     * 当前登录人接口（通用）：从 Cookie/Header 会话 ID 解析平台 Redis 会话，返回用户上下文。
     * <p>后续各业务模块需要获取登录人信息时统一使用此能力。
     */
    @GetMapping("/current-user")
    public CurrentUserVO currentUser(HttpServletRequest request) {
        UserContext user = resolveCurrentUser(request);
        // 补充 loginName（三维 SSO 使用）：会话 Hash 缺失时查库兜底
        sessionContextService.resolveLoginName(user);
        return buildCurrentUserVO(user);
    }

    /**
     * 三维系统单点登录 token 接口：以当前登录人 login_name 调用客户 ssoLogin，返回三维 token。
     */
    @PostMapping("/3d-token")
    public ThreeDimensionalTokenVO threeDimensionalToken(HttpServletRequest request) {
        UserContext user = resolveCurrentUser(request);
        String loginName = sessionContextService.resolveLoginName(user);
        return new ThreeDimensionalTokenVO(threeDimensionalSsoService.getToken(loginName));
    }

    /**
     * 档案系统单点登录凭证接口：返回 {token, userId}。
     * <p>前端以 http://172.27.177.30:8090/archive/index/index?UserID=userId&token=token 跳转即可单点登录。
     * <p>userId 缺失时由 ensureArchiveUserId 兜底调用人员同步获取，保证一定返回 userId。
     */
    @PostMapping("/archive-token")
    public ArchiveSsoVO archiveToken(HttpServletRequest request) {
        UserContext user = resolveCurrentUser(request);
        String loginName = sessionContextService.resolveLoginName(user);
        String archiveUserId = archiveSyncService.ensureArchiveUserId(loginName);
        return new ArchiveSsoVO(archiveClient.getToken(), archiveUserId);
    }

    /**
     * 获取当前登录人上下文：拦截器开启时优先复用 ThreadLocal（避免重复 HGETALL），
     * 否则（开关关闭/白名单场景）从请求重新解析。
     */
    private UserContext resolveCurrentUser(HttpServletRequest request) {
        UserContext user = UserContextHolder.currentUser();
        return user != null ? user : sessionContextService.resolveCurrentUser(request);
    }

    private CurrentUserVO buildCurrentUserVO(UserContext user) {
        CurrentUserVO vo = new CurrentUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
