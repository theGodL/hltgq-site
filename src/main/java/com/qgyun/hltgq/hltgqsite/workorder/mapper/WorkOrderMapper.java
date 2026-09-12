package com.qgyun.hltgq.hltgqsite.workorder.mapper;

import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单管理查询：工单列表（站点/处理时间/状态过滤 + 站点/设备/部门/人员/问题名称联查）。
 */
public interface WorkOrderMapper {

    /**
     * 工单列表：按创建时间倒序（id 兜底）。
     * <p>site 参数支持 站点 id / 站点编号 iofhpi / 站点名称 zzkaec 三写法；
     * 部门名按 org（部门 id 或短码 code）双匹配；"time"/"user" 为关键字列需双引号；
     * 处理时间过滤按 pyxcen 半开区间 [startTime, endTime)。
     */
    @Select("<script>" +
            "SELECT w.id AS id, w.code AS code, w.title AS title, w.qjulvf AS typeCode, " +
            "w.content AS content, w.site AS siteId, s.zzkaec AS siteName, " +
            "w.device AS deviceId, d.name AS deviceName, w.org AS orgId, o.name AS orgName, " +
            "w.\"user\" AS userId, u.name AS userName, w.\"time\" AS \"time\", " +
            "w.pyxcen AS handleTime, w.result AS result, w.status AS statusCode, " +
            "w.azgquf AS issueId, i.title AS issueTitle " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON w.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON w.device = d.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON (o.id = w.org OR o.code = w.org) AND o.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON w.\"user\" = u.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i ON w.azgquf = i.id " +
            "WHERE w.corp_code = 'hltgq' " +
            "<if test='site != null and site != \"\"'>AND (w.site = #{site} OR s.zzkaec = #{site} OR s.iofhpi = #{site}) </if>" +
            "<if test='startTime != null'>AND w.pyxcen &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND w.pyxcen &lt; #{endTime} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND w.status = #{statusCode} </if>" +
            "ORDER BY w.created_at DESC, w.id DESC" +
            "</script>")
    List<WorkOrderListVO> selectWorkOrderList(@Param("site") String site,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              @Param("statusCode") String statusCode);
}
