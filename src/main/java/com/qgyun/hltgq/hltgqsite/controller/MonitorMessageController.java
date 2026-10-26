package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.service.MonitorMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 监测页通用报文接口：各监测页「详情 → 历史数据」行内「查看报文」使用
 * <p>闸门监测已有专用接口 /gate-monitor/messages（按站点 UUID 查询、窗口固定），
 * 本接口面向其余监测页（流量 / 雨量 / 墒情 / 水质 / 灌区水位），站点标识同时兼容测站编码与站点 UUID。
 */
@RestController
@RequestMapping("/monitor-messages")
public class MonitorMessageController {

    @Autowired
    private MonitorMessageService monitorMessageService;

    /**
     * 按站点 + 监测时间查询该时刻入库的原始报文（供设备厂家核对数据口径）
     * <p>查询窗口：默认监测时间前 2 分钟 ~ 后 2 分钟（实测各监测表与报文入库时刻基本对齐）；
     * 水质历史为整点补齐行，页面传 after=60 覆盖该小时内的实际上报报文。
     *
     * @param key    站点标识（测站编码或站点 UUID，与各监测历史接口的筛选值一致）
     * @param tm     监测时间（历史数据行的时间），格式 yyyy-MM-dd HH:mm:ss
     * @param before 窗口前伸分钟数，默认 2（0~720）
     * @param after  窗口后延分钟数，默认 2（0~720）
     * @param limit  最大返回条数，默认 30（1~200）
     * @return 报文列表（type=frame 原始帧 / json 解析报文，含 tag/frame/tm/sendtm/afn/msgText）
     */
    @GetMapping
    public List<Map<String, Object>> messages(
            @RequestParam String key,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tm,
            @RequestParam(defaultValue = "2") int before,
            @RequestParam(defaultValue = "2") int after,
            @RequestParam(defaultValue = "30") int limit) {
        return monitorMessageService.messages(key, tm, before, after, limit);
    }
}
