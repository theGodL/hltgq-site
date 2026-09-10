package com.qgyun.hltgq.hltgqsite.stationdetail.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.stationdetail.service.StationDetailService;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.DeviceVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.IssueRecordVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.PatrolDetailVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.PatrolRecordVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.StationBasicVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.StationOptionsVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.WorkOrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.time.LocalDate;

/**
 * 站点详情接口（/station-detail）：站点详情页（基础信息/巡检记录/问题记录/维修工单记录/设备信息）。
 * <p>契约见 src/main/resources/站点详情接口.md；全部为实时查询（无轮询任务）；
 * 参数越界由全局 IllegalArgumentException 处理返回 400；走现有登录会话鉴权。
 * <p>stationId 支持双键：站点档案主键 id（监测/业务表 site 值）或站点编号（档案表 iofhpi）。
 */
@RestController
@RequestMapping("/station-detail")
public class StationDetailController {

    @Autowired
    private StationDetailService stationDetailService;

    /**
     * 基础信息聚合：站点档案 + 闸口数量 + 供电/网络（电压表、报文表最新）+ 视频通道，
     * 一次请求渲染「基础信息」整页。
     *
     * @param stationId 站点键（档案 id 或站点编号 iofhpi），必填
     */
    @GetMapping("/basic")
    public StationBasicVO basic(@RequestParam String stationId) {
        return stationDetailService.basic(stationId);
    }

    /**
     * 巡检记录分页（全状态含草稿），按巡检时间倒序。
     *
     * @param stationId 站点键，必填
     * @param date      巡检日期 yyyy-MM-dd，可选（命中当日）
     * @param person    巡检人员姓名模糊，可选
     * @param result    巡检结果：正常/异常（异常含隐患/缺陷/故障档），可选
     * @param hasIssue  是否发现问题：1/0，可选
     */
    @GetMapping("/patrol/page")
    public Page<PatrolRecordVO> patrolPage(
            @RequestParam String stationId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String person,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String hasIssue,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return stationDetailService.patrolPage(stationId, date, person, result, hasIssue, page, size);
    }

    /**
     * 巡检记录详情（「查看」弹窗）：计划名/站点名/巡检对象设备名/人员/结果/说明/状态/关联问题；
     * photos 为现场照片对象数组（图片关联表 + 文件服务签名地址实时组装）。
     *
     * @param recordId 巡检记录主键 id
     */
    @GetMapping("/patrol/{recordId}")
    public PatrolDetailVO patrolDetail(@PathVariable String recordId, HttpServletRequest request) {
        return stationDetailService.patrolDetail(recordId, request);
    }

    /**
     * 问题记录分页（全状态），按发现时间倒序。
     *
     * @param stationId 站点键，必填
     * @param date      发现日期 yyyy-MM-dd，可选（命中当日）
     * @param finder    发现人姓名模糊，可选
     * @param handle    处理方式：转工单/待确定/直接处理，可选
     * @param status    状态：已转工单/待处理/已关闭，可选
     */
    @GetMapping("/issue/page")
    public Page<IssueRecordVO> issuePage(
            @RequestParam String stationId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String finder,
            @RequestParam(required = false) String handle,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return stationDetailService.issuePage(stationId, date, finder, handle, status, page, size);
    }

    /**
     * 维修工单记录分页（全状态），按工单 time（要求完成时间）倒序，页面无筛选。
     *
     * @param stationId 站点键，必填
     */
    @GetMapping("/order/page")
    public Page<WorkOrderVO> orderPage(
            @RequestParam String stationId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return stationDetailService.orderPage(stationId, page, size);
    }

    /**
     * 设备信息分页 + 实时数据（按监测类型取对应监测表最新值）+ 所属闸口解析。
     *
     * @param stationId 站点键，必填
     * @param code      设备编号模糊，可选
     * @param name      设备名称精确（下拉选择），可选
     * @param type      设备类型：中文名称 水位/雨量/流量/闸门/视频/墒情/水质 或监测类型数字 1/2/3/4/5/7/8（等价），可选
     * @param status    设备状态：正常（在线）/关闭（离线），可选
     */
    @GetMapping("/device/page")
    public Page<DeviceVO> devicePage(
            @RequestParam String stationId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return stationDetailService.devicePage(stationId, code, name, type, status, page, size);
    }

    /**
     * 筛选下拉选项：巡检人员/问题发现人/设备名称（去重升序），一次请求返回三组。
     *
     * @param stationId 站点键，必填
     */
    @GetMapping("/options")
    public StationOptionsVO options(@RequestParam String stationId) {
        return stationDetailService.options(stationId);
    }
}
