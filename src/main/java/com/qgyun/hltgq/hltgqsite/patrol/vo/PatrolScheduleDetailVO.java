package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 巡检计划详情（GET /patrol-schedule/{id}，列表页「查看」弹窗）。
 * <p>继承列表行字段，追加创建时间与巡查范围站点明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PatrolScheduleDetailVO extends PatrolScheduleVO {

    /** 创建时间（主表 created_at） */
    private LocalDateTime createdAt;

    /** 巡查范围站点明细（按关系顺序；无范围为空数组） */
    private List<PatrolSiteVO> sites;
}
