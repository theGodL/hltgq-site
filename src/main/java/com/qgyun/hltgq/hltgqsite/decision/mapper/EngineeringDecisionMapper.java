package com.qgyun.hltgq.hltgqsite.decision.mapper;

import com.qgyun.hltgq.hltgqsite.decision.vo.EngineeringSummaryVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工程管理决策聚合统计：站点风险等级分布 + 工单类型分布 + 问题状态分布（3 条聚合 SQL）。
 */
public interface EngineeringDecisionMapper {

    /**
     * 站点 × 风险等级计数：问题表 LEFT JOIN 站点表取站点名，未关联站点聚合为「未关联站点」。
     * 列别名与 RiskStation 属性同名自动映射（map-underscore-to-camel-case=false）。
     * 按问题总数倒序，风险集中的站点靠前。
     */
    @Select("SELECT COALESCE(s.zzkaec, '未关联站点') AS name, " +
            "COUNT(*) FILTER (WHERE i.risk_level = '#1#') AS low, " +
            "COUNT(*) FILTER (WHERE i.risk_level = '#2#') AS mid, " +
            "COUNT(*) FILTER (WHERE i.risk_level = '#3#') AS high " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON i.site = s.id " +
            "GROUP BY s.zzkaec " +
            "ORDER BY COUNT(*) DESC")
    List<EngineeringSummaryVO.RiskStation> selectRiskStations();

    /**
     * 工单类型分布：name = 类型编码（如 #yjje#），value = 计数。
     */
    @Select("SELECT qjulvf AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "GROUP BY qjulvf")
    List<EngineeringSummaryVO.NamedValue> selectOrderTypes();

    /**
     * 问题状态分布：name = 状态编码（如 #1#），value = 计数。
     */
    @Select("SELECT status AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "GROUP BY status")
    List<EngineeringSummaryVO.NamedValue> selectIssueStatus();
}
