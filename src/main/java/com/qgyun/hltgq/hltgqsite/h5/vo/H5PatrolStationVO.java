package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * H5 巡检计划关联点位（巡查范围站点，/h5/patrol-schedule/list 行内 stations 元素）。
 * <p>站点档案表实时关联（名称 zzkaec / 经度 bviiio_x / 纬度 bviiio_y），按展示顺序；
 * 站点未填坐标时为 null；关联关系存在但站点已删时仅 id 可用。
 */
@Data
public class H5PatrolStationVO {

    /** 站点档案主键（其他业务表 site 值） */
    private String id;

    /** 站点名称（zzkaec；站点已删为 null） */
    private String name;

    /** 经度（bviiio_x；未填坐标为 null） */
    private BigDecimal lon;

    /** 纬度（bviiio_y；未填坐标为 null） */
    private BigDecimal lat;
}
