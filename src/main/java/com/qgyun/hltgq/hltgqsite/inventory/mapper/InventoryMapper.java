package com.qgyun.hltgq.hltgqsite.inventory.mapper;

import com.qgyun.hltgq.hltgqsite.inventory.vo.InventoryVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备库存查询（t_auto_hltgq_knc3g_tzkioe，平台动态列扩展表）。
 * <p>列别名与 VO 属性同名（map-underscore-to-camel-case=false 下 MyBatis 依赖同名忽略大小写映射）；
 * 动态 SQL 走 &lt;script&gt;；库存数量/安全库存/明细列按文本取出（库中可能以文本存储，如 '30'），
 * 由 Service 转换/解析，避免驱动层隐式类型转换异常。
 * <p>部门名称 LEFT JOIN t_apaas_uc_org（czdwpj = org.id 且 corp_code='hltgq'），
 * 部门记录缺失/被删时库存行不丢（departmentName 为 null）。
 */
public interface InventoryMapper {

    /**
     * 库存列表：按创建时间倒序（id 降序兜底排序稳定）。
     * <p>过滤参数均传存储原值：departmentId = czdwpj（部门记录 ID）、
     * stockStatusCode = mojktw（#1#/#2#，中文已在 Service 归一为编码）。
     */
    @Select("<script>" +
            "SELECT t.id AS id, t.czdwpj AS departmentId, o.name AS departmentName, " +
            "t.jsnowd AS categoryCode, t.mojktw AS stockStatusCode, t.kxhsks AS unit, " +
            "t.ejgxlq AS stockQtyText, t.vhvhja AS safetyStockText, t.obompq AS detailsJson " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_tzkioe\" t " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON o.id = t.czdwpj AND o.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' " +
            "<if test='departmentId != null and departmentId != \"\"'>AND t.czdwpj = #{departmentId} </if>" +
            "<if test='stockStatusCode != null and stockStatusCode != \"\"'>AND t.mojktw = #{stockStatusCode} </if>" +
            "ORDER BY t.created_at DESC, t.id DESC" +
            "</script>")
    List<InventoryVO> selectInventoryList(@Param("departmentId") String departmentId,
                                          @Param("stockStatusCode") String stockStatusCode);
}
