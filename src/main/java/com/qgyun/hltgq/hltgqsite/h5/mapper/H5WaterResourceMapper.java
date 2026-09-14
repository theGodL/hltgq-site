package com.qgyun.hltgq.hltgqsite.h5.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Map;

/**
 * H5 水资源概览查询（water-allocation 配水方案供水来源侧）。
 * <p>需水方案侧（分配水量/执行进度/四县卡片）复用 {@code IrrigationWaterService} 口径；
 * 本 Mapper 仅补配水方案（AllocateRecord / AllocateTenday）的供水来源聚合。
 * <p>方案口径：status='completed'（纯值）且 del_flag='#2#'（未删除），按创建时间倒序取最新。
 */
public interface H5WaterResourceMapper {

    /** 最新已完成配水方案 id（无方案返回 null） */
    @Select("SELECT p.id FROM \"qixiao-apaas\".\"t_auto_hltgq_water_allocate_record\" p " +
            "WHERE p.corp_code = 'hltgq' AND p.status = 'completed' AND p.del_flag = '#2#' " +
            "ORDER BY p.created_at DESC LIMIT 1")
    String selectLatestAllocateId();

    /**
     * 方案供水来源求和（万方）：塘坝 / 水厂 / 花凉亭水库灌溉。
     * <p>无明细行时 SUM 为 NULL，COALESCE 归 0（Map key：pond / waterworks / reservoir）。
     */
    @Select("SELECT COALESCE(SUM(t.pond_supply), 0) AS pond, " +
            "COALESCE(SUM(t.waterworks_supply), 0) AS waterworks, " +
            "COALESCE(SUM(t.reservoir_irrigation), 0) AS reservoir " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_allocate_tenday\" t " +
            "WHERE t.record_id = #{recordId}")
    Map<String, BigDecimal> selectSupplySourceSums(@Param("recordId") String recordId);
}
