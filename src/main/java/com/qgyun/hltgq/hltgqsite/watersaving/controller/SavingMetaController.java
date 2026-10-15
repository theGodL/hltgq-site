package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.watersaving.SavingDict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 节水管理公共元数据（字典/口径下发，前端不写死）。
 * <p>含：方案状态 / 指标计算方式（名称+单位+公式）/ 计划与实际来源 / 布尔标记 / 待复核原因。
 */
@RestController
@RequestMapping("/water-saving")
public class SavingMetaController {

    /** 表单元数据（方案状态 / 指标计算方式 / 来源 / 布尔 / 复核原因字典） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return SavingDict.meta();
    }
}
