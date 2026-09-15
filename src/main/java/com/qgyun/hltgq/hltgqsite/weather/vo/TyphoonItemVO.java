package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

/**
 * 活跃台风列表项 VO（方案 §5.2）
 * <p>`distanceKm` 为台风中心到灌区参考中心点（`weather.site-lon/lat`）的球面直线距离，
 * <b>仅供前端排序/初筛参考</b>，不作为影响判定依据（评审 B5 口径）。
 */
@Data
public class TyphoonItemVO {

    /** 台风 ID（上游编号，用于详情查询） */
    private String typhoonId;

    /** 英文名（上游 `nameless` 时归一为「未命名」） */
    private String nameEn;

    /** 中文名 */
    private String nameCn;

    /** 台风编号（如 2622） */
    private String code;

    /** 状态：start=活跃 / stop=已停止编号 */
    private String status;

    /** 强度缩写：TD / TS / STS / TY / STY / SuperTY */
    private String level;

    /** 强度中文名 */
    private String levelName;

    /** 最新位置经度（WGS-84） */
    private Double lon;

    /** 最新位置纬度（WGS-84） */
    private Double lat;

    /** 中心气压（hPa，上游原始单位） */
    private Integer pressure;

    /** 中心风速（m/s，上游原始单位） */
    private Integer windSpeed;

    /** 移向（16 方位中文） */
    private String moveDirection;

    /** 移速（km/h） */
    private Integer moveSpeed;

    /** 最新实况时间（北京时间） */
    private String latestTime;

    /** 到灌区参考中心点距离（km，球面直线距离，仅供排序参考） */
    private Double distanceKm;
}
