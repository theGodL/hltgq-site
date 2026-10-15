package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterBillService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterBillVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 水费账单管理（灌区用水模块页面四之四）。
 * <p>四个视图：台账 /ledger（按户归集 + 汇总 stat）、户方案明细 /user-plans、
 * 账单列表 /page、账单详情 /detail（含方案快照）。
 * <p>出账 POST /bills/create：请求体为调度方案 id 数组，按 (用水户, 分期 key) 分组拆分多张账单
 * （四个出账入口 genSelected/genPeriod/genSingle/genAll 共用）；账单删除恢复方案未计费。
 * 编号 SF + 期间码 + '-' + 户号 + 4 位序号，冲突重试同用水户。
 */
@RestController
@RequestMapping("/water-bill")
public class WaterBillController {

    private static final Logger log = LoggerFactory.getLogger(WaterBillController.class);

    @Autowired
    private WaterBillService billService;

    /** 表单元数据（账单状态 + 统计周期字典 + 当天日期） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return billService.meta();
    }

    /** 账单列表分页（关键字 / 状态筛选；status 传前端英文码 generated/paid） */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status) {
        return billService.page(page, size, keyword, status);
    }

    /** 账单列表统计条（全量汇总：账单总数/计费总水量/应收/已收/收缴率；无筛选参数） */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return billService.stats();
    }

    /** 账单详情（含方案明细：计费快照） */
    @GetMapping("/detail")
    public WaterBillVO detail(@RequestParam String id) {
        return billService.detail(id);
    }

    /** 水费台账（视图一：全量正常用水户 × 本期/待出账/已出账汇总 + 汇总 stat） */
    @GetMapping("/ledger")
    public Map<String, Object> ledger(@RequestParam(required = false) String keyword) {
        return billService.ledger(keyword);
    }

    /** 户方案明细（视图二：单户全部未取消方案；未计费实时计量、已计费取快照） */
    @GetMapping("/user-plans")
    public Map<String, Object> userPlans(@RequestParam String userId) {
        return billService.userPlans(userId);
    }

    /** 出账（请求体 = 调度方案 id 数组；按户×分期分组拆分；编号冲突最多重试 3 次） */
    @PostMapping("/bills/create")
    public Map<String, Object> createBills(@RequestBody List<String> ids) {
        log.info("收到出账请求：方案数={}", ids == null ? 0 : ids.size());
        return WaterUsageSupport.retryOnNoConflict("uk_water_bill_no", "账单",
                () -> billService.createBills(ids), log);
    }

    /** 确认收款（仅已生成：状态已缴清、实收 = 应收、记录收款时间） */
    @PostMapping("/bills/pay")
    public Map<String, Object> pay(@RequestParam String id) {
        log.info("收到账单收款请求：id={}", id);
        billService.pay(id);
        return WaterUsageSupport.ok();
    }

    /** 删除账单（任意状态；方案挂接清空恢复未计费，可重新出账） */
    @DeleteMapping("/bills/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到账单删除请求：id={}", id);
        billService.delete(id);
        return WaterUsageSupport.ok();
    }
}
