package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsite.entity.RainAdjust;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 雨量补偿配置表 Mapper（监测数据删除方案 §3.2）。
 * <p>列表按站点档案/设备表左联翻译展示名（缺失回退主键）；模糊匹配统一
 * {@code LIKE CONCAT('%', #{x}, '%')}（KingbaseES 下 || 拼接的绑定参数会静默失效，
 * 与全库既有 SQL 一致）。
 */
@Mapper
public interface RainAdjustMapper extends BaseMapper<RainAdjust> {

    /** 列表分页：站点名/编号、设备名/编号一次补齐；按更新时间倒序（NULL 最后） */
    @Select("<script>" +
            "SELECT r.id, r.site, r.device, r.stcd, r.offset_value, r.enabled, r.remark, " +
            "r.created_at, r.created_by, r.updated_at, r.updated_by, " +
            "COALESCE(s.zzkaec, r.site) AS site_name, s.iofhpi AS site_code, " +
            "COALESCE(d.name, r.device) AS device_name, d.code AS device_code " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_adjust r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = r.site " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_water_device d ON d.id = r.device " +
            "WHERE 1=1 " +
            "<if test='siteId != null'>AND r.site = #{siteId} </if>" +
            "<if test='keyword != null'>" +
            "AND (COALESCE(s.zzkaec, '') LIKE CONCAT('%', #{keyword}, '%') " +
            "OR COALESCE(s.iofhpi, '') LIKE CONCAT('%', #{keyword}, '%') " +
            "OR COALESCE(d.name, '') LIKE CONCAT('%', #{keyword}, '%') " +
            "OR COALESCE(d.code, '') LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY r.updated_at DESC NULLS LAST, r.id " +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "site", property = "site"),
            @Result(column = "device", property = "device"),
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "offset_value", property = "offsetValue"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "site_name", property = "siteName"),
            @Result(column = "site_code", property = "siteCode"),
            @Result(column = "device_name", property = "deviceName"),
            @Result(column = "device_code", property = "deviceCode")
    })
    IPage<RainAdjustVO> selectPageWithNames(IPage<RainAdjustVO> page,
                                            @Param("siteId") String siteId,
                                            @Param("keyword") String keyword);

    /** 站点候选（全部站点档案，按站名排序；几百行量级前端搜索过滤） */
    @Select("SELECT id AS id, iofhpi AS stcd, zzkaec AS stnm " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" ORDER BY zzkaec")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "stnm", property = "stnm")
    })
    List<RainAdjustVO.SiteOption> selectSiteOptions();

    /** 站点档案单行（新增校验 + stcd 识别列取值） */
    @Select("SELECT id AS id, iofhpi AS stcd, zzkaec AS stnm " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" WHERE id = #{siteId} LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "stnm", property = "stnm")
    })
    RainAdjustVO.SiteOption selectSiteById(@Param("siteId") String siteId);

    /** 站点档案按测站编码反查（数据维护删除联动：由删除行 stcd 解析站点） */
    @Select("SELECT id AS id, iofhpi AS stcd, zzkaec AS stnm " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" WHERE iofhpi = #{stcd} LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "stnm", property = "stnm")
    })
    RainAdjustVO.SiteOption selectSiteByStcd(@Param("stcd") String stcd);

    /** 某站设备候选（设备表 site 列，按设备名排序；单站设备量小不分页） */
    @Select("SELECT id AS id, name AS name, code AS code, type AS type, site AS site " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_device " +
            "WHERE site = #{siteId} ORDER BY name")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "code", property = "code"),
            @Result(column = "type", property = "type"),
            @Result(column = "site", property = "site")
    })
    List<RainAdjustVO.DeviceOption> selectDeviceOptions(@Param("siteId") String siteId);

    /** 设备单行（新增校验：存在且归属站点匹配；名称回显） */
    @Select("SELECT id AS id, name AS name, code AS code, type AS type, site AS site " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_device WHERE id = #{deviceId} LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "code", property = "code"),
            @Result(column = "type", property = "type"),
            @Result(column = "site", property = "site")
    })
    RainAdjustVO.DeviceOption selectDeviceById(@Param("deviceId") String deviceId);

    /** 某站雨量类设备（数据维护删除联动：无补偿配置时按 type 含 #2# 定位，单站至多返回 1 台） */
    @Select("SELECT id AS id, name AS name, code AS code, type AS type, site AS site " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_device " +
            "WHERE site = #{siteId} AND type LIKE '%#2#%' ORDER BY name LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "code", property = "code"),
            @Result(column = "type", property = "type"),
            @Result(column = "site", property = "site")
    })
    RainAdjustVO.DeviceOption selectRainDeviceBySite(@Param("siteId") String siteId);
}
