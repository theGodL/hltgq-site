package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.service.UcUserService;
import com.qgyun.hltgq.hltgqsite.vo.UserProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企效 UC 用户信息接口。
 * <p>全局登录拦截器（auth.enabled 开启时）覆盖本路径；userId 缺省时取当前登录人。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UcUserService ucUserService;

    public UserController(UcUserService ucUserService) {
        this.ucUserService = ucUserService;
    }

    /**
     * 按用户 ID 查询人员信息（含部门/岗位列表，均带 id/code/name）。
     *
     * @param userId 用户主键（t_apaas_uc_user.id），可选；缺省=当前登录人
     * @return 人员信息
     */
    @GetMapping("/profile")
    public UserProfileVO profile(@RequestParam(required = false) String userId) {
        String id = userId == null || userId.trim().isEmpty() ? null : userId.trim();
        if (id == null) {
            UserContext user = UserContextHolder.currentUser();
            if (user == null || user.getUserId() == null) {
                throw new IllegalArgumentException("userId 必填（未登录时无法缺省）");
            }
            id = user.getUserId();
        }
        return ucUserService.profile(id);
    }
}
