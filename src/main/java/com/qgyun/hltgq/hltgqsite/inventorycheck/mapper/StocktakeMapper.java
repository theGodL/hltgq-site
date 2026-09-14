package com.qgyun.hltgq.hltgqsite.inventorycheck.mapper;

import com.qgyun.hltgq.hltgqsite.inventorycheck.vo.InventoryCheckExportVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备盘点查询（t_auto_hltgq_knc3g_apsypa，平台动态列扩展表）。
 * <p>列别名与 VO 属性同名（map-underscore-to-camel-case=false 下 MyBatis 依赖同名忽略大小写映射）；
 * 动态 SQL 走 &lt;script&gt;；盘点日期过滤沿用值班记录口径（兼容时间戳与文本两种形态，
 * 用 [当日, 次日) 半开区间字符串比较），盘点明细/备注列按文本取出，由 Service 解析。
 */
public interface StocktakeMapper {

    /**
     * 盘点记录列表（按盘点日期倒序，id 兜底排序稳定）。
     * <p>过滤参数：code = yogpxf（编号，模糊 LIKE）、dayStart/dayEnd = khntiy（盘点日期半开区间，
     * 两端独立可选：仅起=从该日起、仅止=截至该日含当日）。
     */
    @Select("<script>" +
            "SELECT t.id AS id, t.yogpxf AS code, t.khntiy AS checkDateRaw, " +
            "t.himanc AS detailsJson, t.ymiwpu AS remark, t.xfeiey AS attachmentId " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_apsypa\" t " +
            "WHERE t.corp_code = 'hltgq' " +
            "<if test='code != null and code != \"\"'>AND t.yogpxf LIKE CONCAT('%', #{code}, '%') </if>" +
            "<if test='dayStart != null'>AND t.khntiy &gt;= #{dayStart} </if>" +
            "<if test='dayEnd != null'>AND t.khntiy &lt; #{dayEnd} </if>" +
            "ORDER BY t.khntiy DESC, t.id DESC" +
            "</script>")
    List<InventoryCheckExportVO> selectCheckList(@Param("code") String code,
                                                 @Param("dayStart") String dayStart,
                                                 @Param("dayEnd") String dayEnd);
}
