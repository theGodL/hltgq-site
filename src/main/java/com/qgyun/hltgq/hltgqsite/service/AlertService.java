package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.AlertPageVO;

import java.time.LocalDateTime;

/**
 * 告警查询服务
 */
public interface AlertService {

    /**
     * 未关闭告警分页查询（#4# 已关闭不计），按发生时间倒序（最新在前）
     *
     * @param siteName   站点名称，模糊匹配，可选
     * @param deviceName 设备名称，模糊匹配，可选
     * @param type       告警类型（逻辑分类），可选：overlimit=阈值超限、other=非超限（异常告警）；不传=全部
     * @param siteType   站点类型筛选，可选：1=水位、2=雨量、3=流量、4=闸门、5=视频、7=墒情、8=水质；不传=全部
     * @param startTime  告警时间起（含），可选
     * @param endTime    告警时间止（含），可选
     * @param page       页码，默认 1
     * @param size       每页条数，默认 10
     */
    Page<AlertPageVO> alertPage(String siteName, String deviceName, String type, String siteType,
                                LocalDateTime startTime, LocalDateTime endTime,
                                long page, long size);
}
