package com.qgyun.hltgq.hltgqsite.wateruse.mapper;

import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseCollectionRecordVO;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseFeeRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用水总结取数 Mapper（水费计算表单 t_auto_hltgq_yn8cm_gfiidm）。
 * <p>业主手动录入；统计周期为区间字段 xxmefs（物理列 xxmefs_min / xxmefs_max）。
 * <p>字段口径：ztmhwx 水费编号 / fbnomc 用水单位 / xxmefs 统计周期（区间）/
 * mlljya 计划供水量(万m³) / lgwutj 执行水价(元/m³) / hsfvdh 应收水费(元) / vdhlhm 已收水费(元)
 * ——金额按「元」入库（表单标签即「应收水费（元）」），接口对外口径为万元，由服务层 ÷10^4 换算后按展示精度截断。
 * <p>归桶锚点 = 统计周期区间终点 xxmefs_max（跨桶按终点，终点缺失回退起点，业主 2026-09-11 确认）；
 * 用水量直取 mlljya，不做推算；后端按「周期锚点归桶」（月/灌季/年）聚合，不在库侧做日期截断。
 * <p>征收/收缴统计另经用水户外键 xqaoxx 左连用水户表 t_auto_hltgq_yn8cm_kooivg 取区域名 iiatzj。
 */
@Mapper
public interface WaterUseSummaryMapper {

    /**
     * 统计周期区间终点落在 [startTime, endTime] 内的水费计算记录（按终点升序）。
     *
     * @param startTime 窗口起点（桶范围外扩后的整桶边界，必填）
     * @param endTime   窗口终点（必填）
     * @return 水费计算记录（归桶与展示精度截断在服务层处理）
     */
    @Select("SELECT ztmhwx AS \"feeNo\", fbnomc AS \"unitName\", " +
            "xxmefs_min::timestamp AS \"periodStartTime\", xxmefs_max::timestamp AS \"periodEndTime\", " +
            "mlljya AS \"plannedSupplyRaw\", lgwutj AS \"priceRaw\", hsfvdh AS \"feeRaw\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_gfiidm\" " +
            "WHERE COALESCE(xxmefs_max, xxmefs_min)::timestamp >= #{startTime} " +
            "AND COALESCE(xxmefs_max, xxmefs_min)::timestamp <= #{endTime} " +
            "ORDER BY COALESCE(xxmefs_max, xxmefs_min)::timestamp")
    List<WaterUseFeeRecordVO> selectFeeRecords(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 征收/收缴统计取数：统计周期区间终点落在 [startTime, endTime] 内的水费计算记录（按终点升序），
     * 左连用水户表取区域名（iiatzj，如望江县）。
     * <p>只取应收/已收两个金额与归桶锚点，不取用水量（征收图不展示用水量，减少无谓列传输）。
     * <p>统计周期两端皆空（锚点为 NULL）的记录被 WHERE 过滤，不参与任何维度；
     * 用水户缺失（xqaoxx 为空 / 用水户记录不存在 / 名称为空）时 regionName 为 null，
     * 此类记录不计入区域图、仍计入月度趋势（服务层输出计数日志）。
     * <p>本表为业主手录小表（单年记录量级小），单次全量取数后由服务层聚合，无性能风险。
     *
     * @param startTime 窗口起点（统计年 1月1日 0点，必填）
     * @param endTime   窗口终点（统计年 12月31日 23:59:59，必填）
     * @return 水费记录（区域/月度聚合在服务层处理）
     */
    @Select("SELECT g.ztmhwx AS \"feeNo\", c.iiatzj AS \"regionName\", " +
            "COALESCE(g.xxmefs_max, g.xxmefs_min)::timestamp AS \"anchorTime\", " +
            "g.hsfvdh AS \"receivableRaw\", g.vdhlhm AS \"receivedRaw\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_gfiidm\" g " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_kooivg\" c ON c.id = g.xqaoxx " +
            "WHERE COALESCE(g.xxmefs_max, g.xxmefs_min)::timestamp >= #{startTime} " +
            "AND COALESCE(g.xxmefs_max, g.xxmefs_min)::timestamp <= #{endTime} " +
            "ORDER BY COALESCE(g.xxmefs_max, g.xxmefs_min)::timestamp")
    List<WaterUseCollectionRecordVO> selectCollectionRecords(@Param("startTime") LocalDateTime startTime,
                                                             @Param("endTime") LocalDateTime endTime);
}
