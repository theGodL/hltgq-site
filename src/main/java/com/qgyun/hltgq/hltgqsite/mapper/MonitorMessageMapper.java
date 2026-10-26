package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 监测页通用报文取数：各监测表历史行 → 该时刻入库的原始报文（供设备厂家核对数据口径）
 * <p>关联口径：报文表 t_auto_hltgq_water_msg_info 同时存 stcd（测站编码）与 site（站点 UUID），
 * 各监测数据表的站点标识为 COALESCE(stcd, site)（MQTT 站点无 stcd 时回退 UUID），
 * 故统一用 {@code (m.stcd = key OR m.site = key)} 关联，与各监测历史接口的站点筛选口径一致。
 * <p>报文形态：RabbitMQ 站有 7E 开头的十六进制原始帧；MQTT 站原始报文以 ':' 或 '##' 开头，
 * 解析后的业务报文以 msg 标签区分（rainInfo/riverInfo/soilData/nmIspInfo/wtInfo...）。
 */
@Mapper
public interface MonitorMessageMapper {

    /**
     * 按站点标识 + 报文入库时间窗口查询原始报文
     *
     * @param key       站点标识（测站编码或站点 UUID，任一命中即可）
     * @param startTime 窗口起点（含）
     * @param endTime   窗口终点（含）
     * @param limit     最大返回条数
     * @return 每行含 type（frame/json）、tag、frame（原始帧文本）、tm、sendtm、afn、msg_text
     */
    @Select("SELECT CASE WHEN m.msg LIKE '7E%' OR m.msg LIKE ':%' OR m.msg LIKE '##%' THEN 'frame' ELSE 'json' END AS type, " +
            // 原始帧统一标为 rawHex（同闸门报文接口口径），避免整帧文本进入 tag 标签
            "CASE WHEN m.msg LIKE '7E%' OR m.msg LIKE ':%' OR m.msg LIKE '##%' THEN 'rawHex' ELSE m.msg END AS tag, " +
            "CASE WHEN m.msg LIKE '7E%' OR m.msg LIKE ':%' OR m.msg LIKE '##%' THEN m.msg ELSE NULL END AS frame, " +
            "to_char(m.tm::timestamp, 'YYYY-MM-DD HH24:MI:SS') AS tm, " +
            "to_char(m.sendtm::timestamp, 'YYYY-MM-DD HH24:MI:SS') AS sendtm, " +
            "m.afn, m.msg_text AS msg_text " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_msg_info\" m " +
            "WHERE (m.stcd = #{key} OR m.site = #{key}) " +
            "AND m.tm::timestamp >= #{startTime}::timestamp " +
            "AND m.tm::timestamp <= #{endTime}::timestamp " +
            "ORDER BY m.tm, m.sendtm " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectByStationWindow(@Param("key") String key,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("limit") int limit);
}
