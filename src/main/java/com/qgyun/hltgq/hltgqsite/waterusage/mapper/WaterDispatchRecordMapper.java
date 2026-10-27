package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 调度记录（t_auto_hltgq_water_dispatch_record）。表结构见 init.sql 灌区用水管理 2.3 / water-usage-v3-ddl.sql。
 * <p>闸口操作留痕，不参与水量与费用计算；关联指令可空（指令删除时 dispatch_id 置空保留）。
 * <p>记录内容 = 操作时间 + 操作后闸门高度（cm）+ 操作人 + 备注；op_type / height 为历史列，
 * 保留旧数据但不参与读写（op_type 有 DEFAULT 可省略）。
 * <p>宽进策略：记录为留痕数据，编辑/删除不限状态（Service 仅做关联一致性与存在性校验）。
 * <p>写入走 {@code @Param("row") Map}：key 为 snake_case 列名；operator 为业务列（操作人，NOT NULL），
 * 审计列 created_by/updated_by 用独立 key {@code auditor} 传入，避免同名前缀冲突。
 */
public interface WaterDispatchRecordMapper {

    String T_RECORD = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch_record\"";
    String T_DISPATCH = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch\"";
    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_STATION = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";

    /** 列表条件（page/count 共用，作用于别名 r；keyword 匹配操作人/备注/指令编号） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (r.operator LIKE CONCAT('%', #{keyword}, '%') " +
            "OR r.remark LIKE CONCAT('%', #{keyword}, '%') " +
            "OR d.dispatch_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='waterUserId != null and waterUserId != \"\"'>AND r.water_user_id = #{waterUserId} </if>" +
            "<if test='gateId != null and gateId != \"\"'>AND r.gate_id = #{gateId} </if>";

    /** 基础列（列表/详情共用；op_time 输出文本；gate_height_after 为操作后闸门高度 cm） */
    String BASE_COLS = "r.id AS id, r.dispatch_id AS dispatchId, d.dispatch_no AS dispatchNo, " +
            "r.water_user_id AS waterUserId, u.user_no AS waterUserNo, u.name AS waterUserName, " +
            "r.gate_id AS gateId, s.zzkaec AS gateName, " +
            "to_char(r.op_time, 'YYYY-MM-DD HH24:MI') AS opTime, r.gate_height_after AS gateHeightAfter, " +
            "r.operator AS operator, r.remark AS remark, " +
            "to_char(r.created_at, 'YYYY-MM-DD HH24:MI') AS createTime";

    /** 基础联表（户/站档/指令；别名 r/d/u/s；指令删除后 d 为空 LEFT JOIN 仍出记录） */
    String BASE_JOINS = " FROM " + T_RECORD + " r " +
            "LEFT JOIN " + T_DISPATCH + " d ON d.id = r.dispatch_id " +
            "LEFT JOIN " + T_USER + " u ON u.id = r.water_user_id " +
            "LEFT JOIN " + T_STATION + " s ON s.id = r.gate_id ";

    /** 列表分页（操作时间倒序，最新操作在前） */
    @Select("<script>SELECT " + BASE_COLS + BASE_JOINS + "WHERE r.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY r.op_time DESC, r.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<WaterDispatchRecordVO> selectRecordPage(@Param("keyword") String keyword,
                                                 @Param("waterUserId") String waterUserId,
                                                 @Param("gateId") String gateId,
                                                 @Param("size") int size,
                                                 @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectRecordPage} 一致） */
    @Select("<script>SELECT COUNT(*)" + BASE_JOINS + "WHERE r.corp_code = 'hltgq' " + LIST_CONDITIONS + "</script>")
    long countRecords(@Param("keyword") String keyword,
                      @Param("waterUserId") String waterUserId,
                      @Param("gateId") String gateId);

    /** 按 ID 查（详情/编辑删除前置校验共用） */
    @Select("SELECT " + BASE_COLS + BASE_JOINS + "WHERE r.id = #{id} AND r.corp_code = 'hltgq'")
    WaterDispatchRecordVO selectRecordById(@Param("id") String id);

    /**
     * 新增（row key：id/dispatch_id/water_user_id/gate_id/op_time/gate_height_after/operator/remark/now/auditor）。
     * <p>op_time 由 Service 解析为 LocalDateTime；operator 为业务列（操作人）、auditor 为审计列值；
     * op_type 走列默认值（历史列，不再写入）。
     */
    @Insert("INSERT INTO " + T_RECORD + " " +
            "(\"id\", \"dispatch_id\", \"water_user_id\", \"gate_id\", \"op_time\", \"gate_height_after\", " +
            "\"operator\", \"remark\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.dispatch_id}, #{row.water_user_id}, #{row.gate_id}, #{row.op_time}, " +
            "#{row.gate_height_after}, #{row.operator}, #{row.remark}, 'hltgq', #{row.now}, " +
            "#{row.auditor}, #{row.now}, #{row.auditor})")
    int insertRecord(@Param("row") Map<String, Object> row);

    /** 编辑（业务字段；op_type/height 历史列不覆盖，保留原值；留痕数据宽进，无状态守卫） */
    @Update("UPDATE " + T_RECORD + " SET \"dispatch_id\" = #{row.dispatch_id}, " +
            "\"water_user_id\" = #{row.water_user_id}, \"gate_id\" = #{row.gate_id}, " +
            "\"op_time\" = #{row.op_time}, \"gate_height_after\" = #{row.gate_height_after}, " +
            "\"operator\" = #{row.operator}, \"remark\" = #{row.remark}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.auditor} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq'")
    int updateRecord(@Param("row") Map<String, Object> row);

    /** 物理删除（留痕数据宽进，无状态守卫） */
    @Delete("DELETE FROM " + T_RECORD + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteRecord(@Param("id") String id);
}
