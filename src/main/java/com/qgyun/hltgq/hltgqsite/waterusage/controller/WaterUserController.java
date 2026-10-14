package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterUserService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用水户管理（灌区用水模块页面一）。
 * <p>编号 YH + 3 位序号由服务端生成；新增遇编号唯一索引冲突（并发生成撞号）在 Controller 重试，
 * 名称冲突与其余约束冲突由 GlobalExceptionHandler 翻译为 409 文案。
 */
@RestController
@RequestMapping("/water-user")
public class WaterUserController {

    private static final Logger log = LoggerFactory.getLogger(WaterUserController.class);

    @Autowired
    private WaterUserService userService;

    /** 表单元数据（字典 + 闸口下拉 + 当天日期） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return userService.meta();
    }

    /**
     * 列表分页（关键字 / 计算策略 / 统计周期 / 状态筛选）。
     *
     * @return total / page / size / pages / records
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String calcMode,
                                    @RequestParam(required = false) String settleCycle,
                                    @RequestParam(required = false) String status) {
        return userService.page(page, size, keyword, calcMode, settleCycle, status);
    }

    /** 详情（编辑回显 + 闸口结构解析） */
    @GetMapping("/detail")
    public WaterUserVO detail(@RequestParam String id) {
        return userService.detail(id);
    }

    /** 新增（编号冲突最多重试 3 次，每次新事务重新取号） */
    @PostMapping("/create")
    public WaterUserVO create(@RequestBody WaterUserSaveVO body) {
        log.info("收到用水户新增请求：name={}，type={}，calcMode={}",
                body == null ? null : body.getName(), body == null ? null : body.getUserType(),
                body == null ? null : body.getCalcMode());
        return WaterUsageSupport.retryOnNoConflict("uk_water_user_no", "用水户",
                () -> userService.create(body), log);
    }

    /** 编辑（编号与状态不变） */
    @PutMapping("/update")
    public WaterUserVO update(@RequestParam String id, @RequestBody WaterUserSaveVO body) {
        log.info("收到用水户编辑请求：id={}，name={}", id, body == null ? null : body.getName());
        return userService.update(id, body);
    }

    /** 删除前置检查（blocked 非空禁止删除；pending 非空需二次确认） */
    @GetMapping("/delete-check")
    public Map<String, Object> deleteCheck(@RequestParam String id) {
        return userService.deleteCheck(id);
    }

    /** 删除（force=true 时连带清理编制中计划明细并重算总水量） */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id,
                                      @RequestParam(defaultValue = "false") boolean force) {
        log.info("收到用水户删除请求：id={}，force={}", id, force);
        userService.delete(id, force);
        return WaterUsageSupport.ok();
    }
}
