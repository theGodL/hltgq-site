package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.service.AlertService;
import com.qgyun.hltgq.hltgqsite.vo.AlertPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 告警查询接口
 */
@RestController
@RequestMapping("/alert")
public class AlertController {

    @Autowired
    private AlertService alertService;

    /**
     * 未关闭告警分页列表（全局，不分站点；#4# 已关闭不计）
     * <p>按发生时间倒序（最新在前，第一页第一条 = 全库最新未关闭告警），
     * 支持站点名称/设备名称模糊筛选、告警类型筛选、告警时间区间筛选。
     *
     * @param siteName   站点名称，模糊匹配，可选
     * @param deviceName 设备名称，模糊匹配，可选
     * @param type       告警类型（逻辑分类），可选：overlimit=阈值超限、other=非超限（异常告警）；不传=全部
     * @param siteType   站点类型筛选，可选：1=水位、2=雨量、3=流量、4=闸门、5=视频、7=墒情、8=水质；不传=全部
     * @param startTime  告警时间起（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime    告警时间止（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param page       页码，默认 1
     * @param size       每页条数，默认 10
     */
    @GetMapping("/page")
    public Page<AlertPageVO> page(
            @RequestParam(required = false) String siteName,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String siteType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return alertService.alertPage(siteName, deviceName, type, siteType, startTime, endTime, page, size);
    }
}
