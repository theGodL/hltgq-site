package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterDispatchRecordService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DispatchOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordVO;
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

import java.util.List;
import java.util.Map;

/**
 * 调度记录管理（灌区用水模块页面四之三）。
 * <p>闸口操作留痕，不参与水量与费用计算；宽进管理（编辑/删除不限状态），
 * 仅校验存在性与关联一致性（填写关联指令时用水户/闸口须与指令一致）。
 */
@RestController
@RequestMapping("/water-dispatch-record")
public class WaterDispatchRecordController {

    private static final Logger log = LoggerFactory.getLogger(WaterDispatchRecordController.class);

    @Autowired
    private WaterDispatchRecordService recordService;

    /** 表单元数据（操作人建议 + 当天日期） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return recordService.meta();
    }

    /** 列表分页（关键字匹配操作人/备注/指令编号） */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String waterUserId,
                                    @RequestParam(required = false) String gateId) {
        return recordService.page(page, size, keyword, waterUserId, gateId);
    }

    /** 详情 */
    @GetMapping("/detail")
    public WaterDispatchRecordVO detail(@RequestParam String id) {
        return recordService.detail(id);
    }

    /** 「关联调度指令」下拉（未取消全部，编号倒序） */
    @GetMapping("/plans")
    public List<DispatchOptionVO> plans(@RequestParam(required = false) String keyword) {
        return recordService.planOptions(keyword);
    }

    /** 新增（关联指令选填；操作后闸门高度 0~999 cm 选填） */
    @PostMapping("/create")
    public WaterDispatchRecordVO create(@RequestBody WaterDispatchRecordSaveVO body) {
        log.info("收到调度记录新增请求：dispatchId={}，userId={}，gateId={}",
                body == null ? null : body.getDispatchId(),
                body == null ? null : body.getWaterUserId(),
                body == null ? null : body.getGateId());
        return recordService.create(body);
    }

    /** 编辑（留痕数据宽进：全字段可改） */
    @PutMapping("/update")
    public WaterDispatchRecordVO update(@RequestParam String id, @RequestBody WaterDispatchRecordSaveVO body) {
        log.info("收到调度记录编辑请求：id={}", id);
        return recordService.update(id, body);
    }

    /** 删除（留痕数据宽进：不限状态） */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到调度记录删除请求：id={}", id);
        recordService.delete(id);
        return WaterUsageSupport.ok();
    }
}
