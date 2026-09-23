package com.qgyun.hltgq.hltgqsite.decision.mapper;

import com.qgyun.hltgq.hltgqsite.decision.vo.NetworkDeviceVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 网络设备监控聚合查询：全量查设备表（千级），分类/状态聚合由 Service 内存一次遍历完成。
 * <p>设备运行状态口径：设备表 status 有值时以自身为准（mq 报文入库时维护全部设备状态，
 * #1# 在线 / #2# 离线）；status 为空时回退所属站点档案 zebpsu（与大屏、水位页同源）；
 * 两处均无值时保持为空，Service 按离线处理。
 */
public interface NetworkDeviceMapper {

    /**
     * 设备全量列表：id / 名称 / 类型编码（多值 | 分割）/ 运行状态 / 状态来源。
     * 列别名与 NetworkDeviceVO.Device 属性同名自动映射（map-underscore-to-camel-case=false）。
     * statusFrom：device = 取设备表 status，site = 设备 status 为空回退站点档案 zebpsu。
     */
    @Select("SELECT d.id, d.name, d.type, " +
            "COALESCE(NULLIF(d.status, ''), s.zebpsu) AS status, " +
            "CASE WHEN d.status IS NULL OR d.status = '' THEN 'site' ELSE 'device' END AS \"statusFrom\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = d.site")
    List<NetworkDeviceVO.Device> selectAllDevices();

    /**
     * 存在未关闭告警的设备 ID（去重）：未关闭即未处理（#1# 未确认 / #2# 已确认 / #3# 处理中，#4# 已关闭不计），
     * 与 /dashboard/overview 的 unhandledAlarmCount 同一过滤口径，差别只在计量单位：
     * 本接口按设备去重（告警设备台数，同一台设备多条告警算 1），大屏按告警条数计。
     * <p>device 为空或 '' 的站点级告警不计入（无法归属到设备）；
     * 台账无档案的设备 ID 由 Service 自然丢弃（不在设备列表内，不会计入分类）。
     */
    @Select("SELECT DISTINCT a.device FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a " +
            "WHERE a.status IN ('#1#', '#2#', '#3#') " +
            "AND a.device IS NOT NULL AND a.device <> ''")
    List<String> selectAlertDeviceIds();
}
