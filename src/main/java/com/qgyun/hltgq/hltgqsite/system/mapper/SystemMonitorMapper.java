package com.qgyun.hltgq.hltgqsite.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.system.entity.SystemFaultRecord;
import com.qgyun.hltgq.hltgqsite.system.entity.SystemResourceSample;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统资源监控 Mapper（页面 system-monitor.html）。
 * <p>只读：当前库/表空间大小（KingbaseES pg_* 函数，实测 V008R006 可用）；
 * 读写下：故障记录分页/落库（BaseMapper）、采样记录插入与清理
 * （采样表经由本 Mapper 手写 SQL，不另建 BaseMapper，id 由 ShortIdGenerator 生成）。
 * <p>注意：全局 map-underscore-to-camel-case=false，实体查询须显式 @Results 映射列名。
 */
@Mapper
public interface SystemMonitorMapper extends BaseMapper<SystemFaultRecord> {

    /**
     * 当前库名与库大小（字节）。
     */
    @Select("SELECT current_database() AS db_name, pg_database_size(current_database()) AS db_size")
    Map<String, Object> selectDbInfo();

    /**
     * 各表空间已用大小（字节）。sys_default 为数据目录所在表空间（含全部业务数据），
     * sys_global/sysaudit 为系统表空间。
     */
    @Select("SELECT spcname AS spc_name, pg_tablespace_size(oid) AS size_bytes " +
            "FROM pg_tablespace ORDER BY spcname")
    List<Map<String, Object>> selectTablespaceSize();

    /**
     * 故障记录数（category=soft/system，level 与时间区间可选）。
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_sys_fault_record\" " +
            "WHERE \"category\" = #{category} " +
            "<if test='level != null and level != \"\"'>AND \"fault_level\" = #{level} </if>" +
            "<if test='start != null'>AND \"occur_time\" &gt;= #{start} </if>" +
            "<if test='end != null'>AND \"occur_time\" &lt;= #{end} </if>" +
            "</script>")
    long countFaults(@Param("category") String category,
                     @Param("level") String level,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    /**
     * 故障记录分页（按发生时间倒序）。
     */
    @Select("<script>" +
            "SELECT id, category, fault_type, fault_source, fault_level, fault_desc, occur_time, " +
            "corp_code, created_at, created_by, updated_at, updated_by " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_sys_fault_record\" " +
            "WHERE \"category\" = #{category} " +
            "<if test='level != null and level != \"\"'>AND \"fault_level\" = #{level} </if>" +
            "<if test='start != null'>AND \"occur_time\" &gt;= #{start} </if>" +
            "<if test='end != null'>AND \"occur_time\" &lt;= #{end} </if>" +
            "ORDER BY \"occur_time\" DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "category", property = "category"),
            @Result(column = "fault_type", property = "faultType"),
            @Result(column = "fault_source", property = "faultSource"),
            @Result(column = "fault_level", property = "faultLevel"),
            @Result(column = "fault_desc", property = "faultDesc"),
            @Result(column = "occur_time", property = "occurTime"),
            @Result(column = "corp_code", property = "corpCode"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "updated_by", property = "updatedBy")
    })
    List<SystemFaultRecord> selectFaultPage(@Param("category") String category,
                                            @Param("level") String level,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("size") int size,
                                            @Param("offset") int offset);

    /**
     * 插入一行资源采样（id 由调用方生成；采样表只写不读，无需 BaseMapper）。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_sys_resource_sample\" " +
            "(\"id\",\"sample_time\",\"scope\",\"hostname\",\"cpu_percent\",\"load1\",\"load5\",\"load15\"," +
            "\"mem_total\",\"mem_used\",\"mem_percent\",\"swap_total\",\"swap_used\",\"swap_percent\"," +
            "\"disk_json\",\"db_json\",\"corp_code\",\"created_at\",\"created_by\",\"updated_at\") " +
            "VALUES (#{id},#{sampleTime},#{scope},#{hostname},#{cpuPercent},#{load1},#{load5},#{load15}," +
            "#{memTotal},#{memUsed},#{memPercent},#{swapTotal},#{swapUsed},#{swapPercent}," +
            "#{diskJson},#{dbJson},#{corpCode},#{createdAt},#{createdBy},#{updatedAt})")
    int insertSample(SystemResourceSample sample);

    /**
     * 清理保留期外的采样记录（故障记录属审计数据，不清理）。
     */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_sys_resource_sample\" " +
            "WHERE \"sample_time\" < #{before}")
    int deleteSamplesBefore(@Param("before") LocalDateTime before);
}
