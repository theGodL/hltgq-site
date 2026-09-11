package com.qgyun.hltgq.hltgqsite.wateruse.mapper;

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
 * mlljya 计划供水量(m³) / lgwutj 执行水价(元/m³) / hsfvdh 应收水费(元)。
 * <p>归桶锚点 = 统计周期区间终点 xxmefs_max（跨桶按终点，终点缺失回退起点，业主 2026-09-11 确认）；
 * 用水量直取 mlljya，不做推算；后端按「周期锚点归桶」（月/灌季/年）聚合，不在库侧做日期截断。
 */
@Mapper
public interface WaterUseSummaryMapper {

    /**
     * 统计周期区间终点落在 [startTime, endTime] 内的水费计算记录（按终点升序）。
     *
     * @param startTime 窗口起点（桶范围外扩后的整桶边界，必填）
     * @param endTime   窗口终点（必填）
     * @return 水费计算记录（归桶与单位换算在服务层处理）
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
}
