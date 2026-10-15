package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.service.PlatformUserService;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingDict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 节水管理公共元数据（字典/口径下发，前端不写死）。
 * <p>含：方案状态 / 指标计算方式（名称+单位+公式）/ 计划与实际来源 / 布尔标记 / 待复核原因 / 负责人候选。
 */
@RestController
@RequestMapping("/water-saving")
public class SavingMetaController {

    /** 平台用户选人（负责人候选池复用用水模块唯一实现，与调度负责人同源） */
    private final PlatformUserService platformUsers;

    public SavingMetaController(PlatformUserService platformUsers) {
        this.platformUsers = platformUsers;
    }

    /** 表单元数据（方案状态 / 指标计算方式 / 来源 / 布尔 / 复核原因字典 / 负责人候选） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> out = SavingDict.meta();
        out.put("owners", platformUsers.options());
        return out;
    }
}
