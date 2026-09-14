package com.qgyun.hltgq.hltgqsite.h5.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * H5 水资源概览查询（配水方案 / 用水计划 / 需水明细面积）。
 * <p>需水方案侧（实际供水量/需水量）复用 {@code IrrigationWaterService} 口径；
 * 本 Mapper 负责三块直查：配水方案供水来源聚合、年度用水计划四县分配量、
 * 需水方案支渠面积按片区求和（供各区域灌溉详情）。
 * <p>配水方案口径：status='completed'（纯值）且 del_flag='#2#'（未删除），
 * 创建时间落在查询年度区间内，按创建时间倒序取最新。
 * <p>用水计划口径：年度计划（plsuub='#1#'）且已确认（byihlu='#ikok#'），
 * year 列兼容数字与时间戳两种形态（::text LIKE 年份前缀）。
 */
public interface H5WaterResourceMapper {

    /** 年度区间内最新已完成配水方案 id（无方案返回 null） */
    @Select("SELECT p.id FROM \"qixiao-apaas\".\"t_auto_hltgq_water_allocate_record\" p " +
            "WHERE p.corp_code = 'hltgq' AND p.status = 'completed' AND p.del_flag = '#2#' " +
            "AND p.created_at >= #{startDate} AND p.created_at < #{endDate} " +
            "ORDER BY p.created_at DESC LIMIT 1")
    String selectLatestAllocateId(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 方案供水来源求和（万方）：塘坝 / 水厂 / 花凉亭水库灌溉。
     * <p>无明细行时 SUM 为 NULL，COALESCE 归 0（Map key：pond / waterworks / reservoir）。
     * <p>值类型取决于 JDBC 驱动（PG numeric 可能映射为 Double/BigDecimal），
     * 由 Service 用 asBigDecimal 统一转换，勿在此声明 BigDecimal。
     */
    @Select("SELECT COALESCE(SUM(t.pond_supply), 0) AS pond, " +
            "COALESCE(SUM(t.waterworks_supply), 0) AS waterworks, " +
            "COALESCE(SUM(t.reservoir_irrigation), 0) AS reservoir " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_allocate_tenday\" t " +
            "WHERE t.record_id = #{recordId}")
    Map<String, Object> selectSupplySourceSums(@Param("recordId") String recordId);

    /**
     * 年度已确认用水计划四县分配量（万m³）。
     * <p>取当年最新一条（created_at 倒序）；Map key：susong / huaining / wangjiang / taihu / total。
     * <p>无年度已确认计划返回 null。值类型同 selectSupplySourceSums，由 Service 统一转换。
     */
    @Select("SELECT onwxkk AS susong, yqcpol AS huaining, hbkznj AS wangjiang, sdfuaf AS taihu, ewkocb AS total " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hjwwcb\" " +
            "WHERE corp_code = 'hltgq' AND plsuub = '#1#' AND byihlu = '#ikok#' " +
            "AND \"year\"::text LIKE CONCAT(#{year}, '%') " +
            "ORDER BY created_at DESC LIMIT 1")
    Map<String, Object> selectAnnualWaterPlan(@Param("year") int year);

    /**
     * 需水方案支渠面积按片区求和（亩）：支渠名去重后按片区（district）分组。
     * <p>明细表每支渠纵向展开 18 旬，DISTINCT ON (branch_name) 取每支渠一条后求和，
     * 与方案主表 irrigatedArea「支渠面积按支渠名去重求和」同口径。
     * <p>返回行：district（片区名，如 总干渠/南干渠/太宿干渠）、area（亩）。
     */
    @Select("SELECT x.district, SUM(x.area) AS area FROM ( " +
            "SELECT DISTINCT ON (d.branch_name) d.district, d.area " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_demand_branch_detail\" d " +
            "WHERE d.record_id = #{recordId} " +
            "ORDER BY d.branch_name, d.sort_order) x " +
            "GROUP BY x.district")
    List<Map<String, Object>> selectBranchAreaByDistrict(@Param("recordId") String recordId);
}
