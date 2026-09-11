package com.qgyun.hltgq.hltgqsite.wateruse.mapper;

import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseFeeRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用水总结取数 Mapper（水费计算表单 t_auto_hltgq_yn8cm_gfiidm）。
 * <p>业主手动录入（当前粒度=年月，统计周期 pyyftf 为时间戳）；
 * 后端按「周期锚点归桶」（月/灌季/年）聚合，不在库侧做日期截断。
 * <p>字段口径：ztmhwx 水费编号 / fbnomc 用水单位 / pyyftf 统计周期 /
 * hqwvsf 计算用水量 / lgwutj 执行水价(元/m³) / hsfvdh 应收水费。
 * <p>各数值项均为外部程序算好入库的现成值，后端只取值、不做推算。
 */
@Mapper
public interface WaterUseSummaryMapper {

    /**
     * 时间窗口 [startTime, endTime] 内的水费计算记录（按统计周期升序）。
     *
     * @param startTime 窗口起点（桶范围外扩后的整桶边界，必填）
     * @param endTime   窗口终点（必填）
     * @return 水费计算记录（各数值为表单现成值，单位换算在服务层处理）
     */
    @Select("SELECT ztmhwx AS \"feeNo\", fbnomc AS \"unitName\", pyyftf AS \"periodTime\", " +
            "hqwvsf AS \"usageRaw\", lgwutj AS \"priceRaw\", hsfvdh AS \"feeRaw\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_gfiidm\" " +
            "WHERE pyyftf >= #{startTime} AND pyyftf <= #{endTime} " +
            "ORDER BY pyyftf")
    List<WaterUseFeeRecordVO> selectFeeRecords(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);
}
