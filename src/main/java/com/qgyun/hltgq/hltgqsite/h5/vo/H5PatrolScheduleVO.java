package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * H5 巡检计划列表行（/h5/patrol-schedule/list）。
 * <p>仅返回当前登录人被指派且「已发布 #2#（展示态细分 未开始/进行中）/ 已完成 #3#」的计划
 * （草稿/已取消、他人计划不入列）；计划类型与状态均为编码 + 后端权威中文映射双字段。
 * <p>draftRecordId 返回当前空白占位草稿 id（未填充的草稿），前端提交时优先填充它。
 */
@Data
public class H5PatrolScheduleVO {

    /** 计划主键 */
    private String id;

    /** 计划编号（XCJH + 13 位毫秒时间戳，或填写值） */
    private String code;

    /** 任务名称（title） */
    private String title;

    /** 巡查内容 */
    private String content;

    /** 计划开始时间 */
    private LocalDateTime startTime;

    /** 计划结束时间 */
    private LocalDateTime endTime;

    /** 计划类型编码（xhonqv：#wavn#/#1# 年度计划、#zjgg# 月度计划） */
    private String planType;

    /** 计划类型名称（权威映射） */
    private String planTypeLabel;

    /** 状态编码（展示态：#5# 未开始 / #2# 进行中 / #3# 已完成） */
    private String statusCode;

    /** 状态名称（权威映射） */
    private String statusLabel;

    /** 该计划是否已开始巡检（存在巡检记录）；未开始为 false */
    private Boolean started;

    /**
     * 当前空白占位草稿 id：该计划下最早一条草稿 #1# 且站点/设备/结果/内容全空的记录；无草稿为 null。
     * <p>H5 提交巡检记录时优先用该 id 更新 + 提交（幂等），不再另建记录，避免「草稿 + 正式」两条。
     */
    private String draftRecordId;

    /** 创建人名称（关联用户表 t_apaas_uc_user；无创建人时为 null） */
    private String creatorName;

    /** 巡检范围关联的站点ID（多值逗号分隔，按展示顺序；无范围关系时为 null） */
    private String rangeStationIds;

    /** 巡查范围关联点位（站点档案实时关联）：id/名称/经纬度，按展示顺序；无范围关系时为空数组 */
    private List<H5PatrolStationVO> stations;
}
