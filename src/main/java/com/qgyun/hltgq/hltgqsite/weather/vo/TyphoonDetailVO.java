package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 台风路径详情响应 VO（方案 §5.2 ②）
 * <p>数据来自 NMC `view_{id}` 的 JSONP 响应；`track` 为实况路径（前端画实线），
 * `forecast` 为预报路径（前端画虚线），`current` 为最新实况点（= track 末点）。
 * <p>⚠️ NMC 不提供风圈半径（7/10 级风圈），无法绘制影响范围圈（方案 §5.2 备注）。
 */
@Data
public class TyphoonDetailVO {

    /** 台风 ID（上游编号） */
    private String typhoonId;

    /** 英文名（上游 `nameless` 时归一为「未命名」） */
    private String nameEn;

    /** 中文名 */
    private String nameCn;

    /** 台风编号（如 2622） */
    private String code;

    /** 状态：start=活跃 / stop=已停止编号 */
    private String status;

    /** 最新实况点（= track 末点，额外带移向与移速） */
    private TrackPoint current;

    /** 实况路径点（时间升序） */
    private List<TrackPoint> track = new ArrayList<>();

    /** 预报路径点（hourOffset 升序） */
    private List<ForecastPoint> forecast = new ArrayList<>();

    /** 预报机构（默认 BABJ=中央气象台；无预报路径时为 null） */
    private String agency;

    /**
     * 实况路径点（也用于承载 `current`）。
     * <p>`moveDirection` / `moveSpeed` 仅 `current` 有值——上游实况路径点不携带移向移速，
     * 仅最新点对应字段有效（方案 §5.2 契约）。
     */
    @Data
    public static class TrackPoint {

        /** 时间（北京时间，取上游下标 2 的 epoch 毫秒为权威） */
        private String time;

        /** 强度缩写：TD / TS / STS / TY / STY / SuperTY */
        private String level;

        /** 强度中文名 */
        private String levelName;

        /** 经度（WGS-84） */
        private Double lon;

        /** 纬度（WGS-84） */
        private Double lat;

        /** 中心气压（hPa） */
        private Integer pressure;

        /** 中心风速（m/s） */
        private Integer windSpeed;

        /** 移向（16 方位中文，仅 current 有值） */
        private String moveDirection;

        /** 移速（km/h，仅 current 有值） */
        private Integer moveSpeed;
    }

    /** 预报路径点（来自上游下标 11 的机构预报字典，默认取 BABJ） */
    @Data
    public static class ForecastPoint {

        /** 预报时效（小时，相对起始时间） */
        private Integer hourOffset;

        /** 预报时间（北京时间） */
        private String time;

        /** 强度缩写 */
        private String level;

        /** 强度中文名 */
        private String levelName;

        /** 经度（WGS-84） */
        private Double lon;

        /** 纬度（WGS-84） */
        private Double lat;

        /** 中心气压（hPa） */
        private Integer pressure;

        /** 中心风速（m/s） */
        private Integer windSpeed;

        /** 预报机构（BABJ=中央气象台） */
        private String agency;
    }
}
