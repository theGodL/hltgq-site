package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.watersaving.service.WaterSavingPotentialService;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.WaterSavingPotentialSaveRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节水潜力测算接口（/water-saving-potential）。
 * <p>页面：static/water-saving-potential.html（基础数据 + 分行业测算 + 结果指标前端实时计算）。
 * <p>一个「年度 + 分析范围」一行记录：GET 读回显（无记录 record=null），POST 保存
 * （存在即整行覆盖、不存在即新增）。数值单位统一万m³。
 */
@RestController
@RequestMapping("/water-saving-potential")
public class WaterSavingPotentialController {

    @Autowired
    private WaterSavingPotentialService waterSavingPotentialService;

    /**
     * 读取指定「年度 + 分析范围」的测算记录。
     *
     * @param year  年度（如 2026）
     * @param scope 分析范围：#1# 全灌区 / #2# 太湖县 / #3# 望江县 / #4# 宿松县 / #5# 怀宁县
     * @return {"record": {...} | null}——无记录时 record 为 null，前端按空表单处理
     */
    @GetMapping("/record")
    public Map<String, Object> record(@RequestParam Integer year,
                                      @RequestParam String scope,
                                      HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", waterSavingPotentialService.getRecord(year, scope, request));
        return result;
    }

    /**
     * 保存测算记录（upsert）。
     *
     * @param req 请求体：年度 + 分析范围 + 基础数据/分行业数值/附件引用/测算依据/措施/状态
     * @return {"ok": true, "id": 记录id}
     */
    @PostMapping("/record")
    public Map<String, Object> save(@RequestBody WaterSavingPotentialSaveRequest req) {
        String id = waterSavingPotentialService.save(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("id", id);
        return result;
    }
}
