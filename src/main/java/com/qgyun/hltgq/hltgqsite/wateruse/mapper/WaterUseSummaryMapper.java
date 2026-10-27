package com.qgyun.hltgq.hltgqsite.wateruse.mapper;

import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterBillFeeRecordVO;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseFeeRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用水总结取数 Mapper。
 * <p>两条数据源并存，用途不同：
 * <ul>
 *   <li>用水总结报表（{@link #selectFeeRecords}）仍取平台水费计算表单 t_auto_hltgq_yn8cm_gfiidm
 *       ——业主按月手录；统计周期为区间字段 xxmefs（物理列 xxmefs_min / xxmefs_max）。
 *       字段口径：ztmhwx 水费编号 / fbnomc 用水单位 / mlljya 计划供水量(万m³) /
 *       lgwutj 执行水价(元/m³) / hsfvdh 应收水费(元)；金额按「元」入库，出参万元由服务层 ÷10^4 换算。
 *       归桶锚点 = 统计周期区间终点 xxmefs_max（跨桶按终点，终点缺失回退起点，业主 2026-09-11 确认）。</li>
 *   <li>大屏征收/收缴统计（{@link #selectBillCollectionRecords}）自 2026-10-27 起改取
 *       新版水费账单表 t_auto_hltgq_water_bill（调度方案出账 + 手工台账出账统一沉淀），
 *       不再读旧表单。金额 amount(应收) / paid(已收) 单位为元；区域直接取用水户名称
 *       （业务口径「一县即一个用水户」）。</li>
 * </ul>
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
     * 征收/收缴统计取数（新版水费账单）：账期终点 period_end 落在 [startDate, endDate] 内的账单（按终点升序），
     * 左连用水户表取名称作为区域（一县一户，户名即行政区名）。
     * <p>只取应收/已收两个金额与归桶锚点，不取计费水量（征收图不展示用水量）。
     * <p>账期终点必填（NOT NULL），不存在锚点缺失；用水户缺失（water_user_id 悬空 / 名称为空）时
     * userName 为 null，此类账单不计入区域图、仍计入月度趋势（服务层输出计数日志）。
     * <p>账单表体量为按户按期的汇总行（每户每月/季/年至多一条），单次全量取数后由服务层聚合，无性能风险。
     *
     * @param startDate 窗口起点（统计年 1月1日，必填）
     * @param endDate   窗口终点（统计年 12月31日，必填）
     * @return 账单记录（区域/月度聚合在服务层处理）
     */
    @Select("SELECT b.bill_no AS \"billNo\", u.name AS \"userName\", " +
            "b.period_end::timestamp AS \"anchorTime\", " +
            "b.amount AS \"receivableRaw\", b.paid AS \"receivedRaw\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_bill\" b " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_user\" u ON u.id = b.water_user_id " +
            "WHERE b.corp_code = 'hltgq' " +
            "AND b.period_end >= #{startDate} AND b.period_end <= #{endDate} " +
            "ORDER BY b.period_end")
    List<WaterBillFeeRecordVO> selectBillCollectionRecords(@Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate);
}
