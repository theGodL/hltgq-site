package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.mapper.UcUserMapper;
import com.qgyun.hltgq.hltgqsite.vo.UserProfileVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;

/**
 * 企效 UC 用户信息服务：按用户主键组装人员信息（基本字段 + 部门/岗位列表）。
 * <p>用户不存在抛 IllegalArgumentException（全局 400）；部门/岗位为空时返回空数组，
 * 不因关联缺失而失败。
 */
@Service
public class UcUserService {

    private final UcUserMapper mapper;

    public UcUserService(UcUserMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按用户 ID 查询人员信息。
     *
     * @param userId 用户主键（t_apaas_uc_user.id）
     * @return 人员信息（含部门/岗位列表）
     */
    public UserProfileVO profile(String userId) {
        Map<String, Object> user = mapper.selectUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(ValueConvertUtils.str(user.get("id")));
        vo.setName(ValueConvertUtils.str(user.get("name")));
        vo.setLoginName(ValueConvertUtils.str(user.get("login_name")));
        vo.setMobile(ValueConvertUtils.str(user.get("mobile")));
        vo.setEmail(ValueConvertUtils.str(user.get("email")));
        vo.setSex(ValueConvertUtils.str(user.get("sex")));

        List<UserProfileVO.DeptItem> depts = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectDeptsByUserId(userId)) {
            UserProfileVO.DeptItem item = new UserProfileVO.DeptItem();
            item.setId(ValueConvertUtils.str(row.get("id")));
            item.setCode(ValueConvertUtils.str(row.get("code")));
            item.setName(ValueConvertUtils.str(row.get("name")));
            item.setMain(ValueConvertUtils.str(row.get("main")));
            depts.add(item);
        }
        vo.setDepartments(depts);

        List<UserProfileVO.PositionItem> positions = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectPositionsByUserId(userId)) {
            UserProfileVO.PositionItem item = new UserProfileVO.PositionItem();
            item.setId(ValueConvertUtils.str(row.get("id")));
            item.setCode(ValueConvertUtils.str(row.get("code")));
            item.setName(ValueConvertUtils.str(row.get("name")));
            positions.add(item);
        }
        vo.setPositions(positions);
        return vo;
    }
}
