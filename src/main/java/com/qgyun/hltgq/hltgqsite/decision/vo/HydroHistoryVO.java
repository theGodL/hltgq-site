package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.util.List;

/**
 * 防洪页历史实测数据（GET /flood-drought/history 响应）。
 * <p>会议 2 定稿：历史与预测分开展示，本接口只回历史实测块；
 * 预测块由前端下拉短期预报方案、从 /water-forecast/short/{id} 详情取数，不在此接口混拼。
 * <p>三序列各自带站点（可切换），values 与 dates 等长；缺数据置 null（不补 0）。
 */
@Data
public class HydroHistoryVO {

    /** 横轴逐日标签（自然日含首尾，yyyy-MM-dd） */
    private List<String> dates;

    /** 降雨序列（mm，1 位小数；水文日口径） */
    private SeriesVO rain;

    /** 水位序列（m，3 位小数；每日 8 时整点值） */
    private SeriesVO level;

    /** 流量序列（m³/s，3 位小数；每日 8 时整点值） */
    private SeriesVO flow;

    /** 单站点序列 */
    @Data
    public static class SeriesVO {

        /** 站点查询键（档案表 iofhpi） */
        private String stcd;

        /** 站点名称（档案表 zzkaec；查不到时与 stcd 一致） */
        private String stnm;

        /** 逐日数值（等长于 dates；无数据 null） */
        private List<Double> values;
    }
}
