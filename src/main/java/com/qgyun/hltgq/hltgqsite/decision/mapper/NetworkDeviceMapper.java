package com.qgyun.hltgq.hltgqsite.decision.mapper;

import com.qgyun.hltgq.hltgqsite.decision.vo.NetworkDeviceVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 网络设备监控聚合查询：全量查设备表（千级），分类/状态聚合由 Service 内存一次遍历完成。
 * <p>设备运行状态取设备表 status（#1# 在线、#2# 离线，报文入库项目维护）。
 */
public interface NetworkDeviceMapper {

    /**
     * 设备全量列表：id / 名称 / 类型编码（多值 | 分割）/ 运行状态。
     * 列别名与 NetworkDeviceVO.Device 属性同名自动映射（map-underscore-to-camel-case=false）。
     */
    @Select("SELECT id, name, type, status " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\"")
    List<NetworkDeviceVO.Device> selectAllDevices();
}
