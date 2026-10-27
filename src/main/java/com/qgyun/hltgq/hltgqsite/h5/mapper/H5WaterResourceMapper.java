package com.qgyun.hltgq.hltgqsite.h5.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * H5 水资源概览查询（用水户年度计划 / 用水申请 / 需水方案支渠面积）。
 * <p>分配方案及执行改为用水户口径（业主 2026-10-27 变更：一县即一个用水户）：
 * 分配水量取该户当年有效年度计划明细，执行进度由户级闸口计量，需水取该户当年用水申请。
 * <p>灌溉配水侧的「供水」仍复用 {@code IrrigationWaterService} 四县站点流量口径，本 Mapper 不参与。
 * <p>年度计划口径：type=年度（#1#）且状态已生效（#2#）或已归档（#3#），周期起始年 = 统计年。
 * <p>用水申请口径：状态待审核（#1#）+ 已通过（#2#），已驳回不计。
 */
public interface H5WaterResourceMapper {

    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";
    String T_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan_item\"";
    String T_DEMAND = "\"qixiao-apaas\".\"t_auto_hltgq_water_demand\"";

    /**
     * 当年有效年度计划按用水户取计划水量（m³）。
     * <p>同一户当年可能落在多份有效计划上（先归档再新编），取创建时间最新的一条；
     * 计划明细「同一计划同一户唯一」由 uk_water_plan_item_user 保证，故 DISTINCT ON 结果唯一。
     * <p>返回行（key 全小写）：userid 用水户 ID、planwater 计划水量（m³）。
     */
    @Select("SELECT DISTINCT ON (i.water_user_id) i.water_user_id AS userid, i.plan_water AS planwater " +
            "FROM " + T_ITEM + " i JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            "WHERE p.corp_code = 'hltgq' AND p.plan_type = '#1#' AND p.status IN ('#2#', '#3#') " +
            "AND EXTRACT(YEAR FROM p.period_start) = #{year} " +
            "ORDER BY i.water_user_id, p.created_at DESC NULLS LAST")
    List<Map<String, Object>> selectAnnualPlanWaterByUser(@Param("year") int year);

    /**
     * 当年用水申请按用水户合计（m³，待审核 + 已通过，已驳回不计）。
     * <p>年份口径：period_year 优先；存量数据该列为空时回退用水时段起始日 start_date 的年份。
     * <p>返回行（key 全小写）：userid 用水户 ID、amount 申请水量合计（m³）。
     */
    @Select("SELECT d.water_user_id AS userid, SUM(d.amount) AS amount " +
            "FROM " + T_DEMAND + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.status IN ('#1#', '#2#') " +
            "AND COALESCE(d.period_year, EXTRACT(YEAR FROM d.start_date)) = #{year} " +
            "GROUP BY d.water_user_id")
    List<Map<String, Object>> selectDemandByUser(@Param("year") int year);
}
