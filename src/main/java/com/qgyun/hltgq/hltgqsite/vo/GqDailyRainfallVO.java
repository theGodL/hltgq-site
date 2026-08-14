package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 灌区日雨情 VO — 双视角设计（非水库站点，排除 RESERVOIR_STCD_NEW 及水库站点名称）
 * - stations[]: 实时雨情视角，各站点最新观测快照（latestTm = 实际观测时间）
 * - days[]:    日雨情视角，逐日雨量透视表（水文日 8:00 切分）
 * <p>字段结构复用 {@link ReservoirRainfallVO.StationInfo} / {@link ReservoirRainfallVO.DayRainfall}，
 * 与水库日雨情保持同一口径，前端渲染逻辑通用。
 */
@Data
public class GqDailyRainfallVO {

    /** 非水库雨量站点信息（含最新观测） */
    private List<ReservoirRainfallVO.StationInfo> stations;

    /** 逐日雨情数据（水文日维度） */
    private List<ReservoirRainfallVO.DayRainfall> days;
}
