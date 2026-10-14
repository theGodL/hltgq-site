package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.GateOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.PlanRefVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.UserOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 用水户（t_auto_hltgq_water_user）。表结构见 init.sql 灌区用水管理 1.1；
 * 引用/派生查询联 water_plan + water_plan_item。
 * <p>写入走 {@code @Param("row") Map}：key 为 snake_case 列名（Service 组装）；
 * 审计列 corp_code 恒 'hltgq'、时间由 Java 传入（与出入库/巡检模块写库风格一致）。
 * <p>KES 注意：模糊匹配一律 {@code CONCAT('%', #{x}, '%')}，禁用 {@code ||}；日期参数 CAST 为 date。
 */
public interface WaterUserMapper {

    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";
    String T_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan_item\"";
    String T_STATION = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";
    String T_DEMAND = "\"qixiao-apaas\".\"t_auto_hltgq_water_demand\"";
    String T_DISPATCH = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch\"";
    String T_RECORD = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch_record\"";
    String T_BILL = "\"qixiao-apaas\".\"t_auto_hltgq_water_bill\"";

    /** 列表条件（page/count 共用，作用于别名 u） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.user_no LIKE CONCAT('%', #{keyword}, '%') OR u.contact_name LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='calcMode != null and calcMode != \"\"'>AND u.calc_mode = #{calcMode} </if>" +
            "<if test='settleCycle != null and settleCycle != \"\"'>AND u.settle_cycle = #{settleCycle} </if>" +
            "<if test='status != null and status != \"\"'>AND u.status = #{status} </if>";

    /** 基础列（列表/详情共用；别名 u） */
    String BASE_COLS = "u.id AS id, u.user_no AS userNo, u.name AS name, u.user_type AS userType, " +
            "u.contact_name AS contactName, u.contact_phone AS contactPhone, " +
            "u.irrigation_area AS irrigationArea, u.water_price AS waterPrice, " +
            "u.calc_mode AS calcMode, u.gate_config AS gateConfigRaw, u.settle_cycle AS settleCycle, " +
            "u.status AS status";

    /**
     * 列表分页：年度计划水量 = 当年（周期起始年）已生效+已归档年度计划的明细之和；
     * 年度已调度 = 该户当年年度计划上未取消调度方案的 plan_amount 合计（与该户配额口径一致）；
     * 无当年年度计划时 SQL 返 NULL，前端显示「—」。
     */
    @Select("<script>" +
            "SELECT " + BASE_COLS + ", " +
            "(SELECT SUM(i.plan_water) FROM " + T_ITEM + " i JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            " WHERE i.water_user_id = u.id AND p.plan_type = '#1#' AND p.status IN ('#2#', '#3#') " +
            " AND EXTRACT(YEAR FROM p.period_start) = #{year}) AS annualPlanWater, " +
            "(SELECT COALESCE(SUM(dd.plan_amount), 0) FROM " + T_DISPATCH + " dd " +
            " JOIN " + T_PLAN + " p2 ON p2.id = dd.plan_id " +
            " WHERE dd.water_user_id = u.id AND dd.corp_code = 'hltgq' AND dd.status != '#4#' " +
            " AND p2.plan_type = '#1#' AND p2.status IN ('#2#', '#3#') " +
            " AND EXTRACT(YEAR FROM p2.period_start) = #{year}) AS annualScheduledQty " +
            "FROM " + T_USER + " u WHERE u.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY u.user_no DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<WaterUserVO> selectUserPage(@Param("keyword") String keyword,
                                     @Param("calcMode") String calcMode,
                                     @Param("settleCycle") String settleCycle,
                                     @Param("status") String status,
                                     @Param("year") int year,
                                     @Param("size") int size,
                                     @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectUserPage} 一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_USER + " u WHERE u.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "</script>")
    long countUsers(@Param("keyword") String keyword,
                    @Param("calcMode") String calcMode,
                    @Param("settleCycle") String settleCycle,
                    @Param("status") String status);

    /** 按 ID 查（详情/校验共用） */
    @Select("SELECT " + BASE_COLS + " FROM " + T_USER + " u WHERE u.id = #{id} AND u.corp_code = 'hltgq'")
    WaterUserVO selectUserById(@Param("id") String id);

    /** 名称唯一性预检（编辑时排除自身） */
    @Select("<script>SELECT COUNT(*) FROM " + T_USER + " WHERE corp_code = 'hltgq' AND name = #{name} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByName(@Param("name") String name, @Param("excludeId") String excludeId);

    /** 当前最大编号序号（YH + 3 位序号；表空返回 0） */
    @Select("SELECT COALESCE(MAX(CAST(substr(user_no, 3) AS INTEGER)), 0) FROM " + T_USER +
            " WHERE corp_code = 'hltgq' AND user_no ~ '^YH[0-9]+$'")
    int selectMaxUserSeq();

    /** 新增（row key：id/user_no/name/user_type/contact_name/contact_phone/irrigation_area/
     *  water_price/calc_mode/gate_config/settle_cycle/now/operator） */
    @Insert("INSERT INTO " + T_USER + " " +
            "(\"id\", \"user_no\", \"name\", \"user_type\", \"contact_name\", \"contact_phone\", " +
            "\"irrigation_area\", \"water_price\", \"calc_mode\", \"gate_config\", \"settle_cycle\", \"status\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.user_no}, #{row.name}, #{row.user_type}, #{row.contact_name}, " +
            "#{row.contact_phone}, #{row.irrigation_area}, #{row.water_price}, #{row.calc_mode}, " +
            "#{row.gate_config}, #{row.settle_cycle}, '#1#', 'hltgq', #{row.now}, #{row.operator}, " +
            "#{row.now}, #{row.operator})")
    int insertUser(@Param("row") Map<String, Object> row);

    /** 编辑（不含编号与状态） */
    @Update("UPDATE " + T_USER + " SET \"name\" = #{row.name}, \"user_type\" = #{row.user_type}, " +
            "\"contact_name\" = #{row.contact_name}, \"contact_phone\" = #{row.contact_phone}, " +
            "\"irrigation_area\" = #{row.irrigation_area}, \"water_price\" = #{row.water_price}, " +
            "\"calc_mode\" = #{row.calc_mode}, \"gate_config\" = #{row.gate_config}, " +
            "\"settle_cycle\" = #{row.settle_cycle}, \"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq'")
    int updateUser(@Param("row") Map<String, Object> row);

    /** 物理删除（引用保护由 Service 前置校验） */
    @Delete("DELETE FROM " + T_USER + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteUser(@Param("id") String id);

    /**
     * 引用查询（两级删除保护）：引用方计划 + 明细行数（GROUP BY plan）。
     * 状态 #1# 编制中 → 二次确认后连带清理；其余状态 → 禁止删除。
     */
    @Select("SELECT p.id AS planId, p.plan_no AS planNo, p.plan_name AS planName, p.status AS status, " +
            "COUNT(*) AS itemCount " +
            "FROM " + T_ITEM + " i JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            "WHERE i.water_user_id = #{userId} AND p.corp_code = 'hltgq' " +
            "GROUP BY p.id, p.plan_no, p.plan_name, p.status ORDER BY p.plan_no")
    List<PlanRefVO> selectUserRefs(@Param("userId") String userId);

    /** 连带清理：删除指定计划中该用水户的明细行（仅编制中计划，Service 已分流） */
    @Delete("<script>DELETE FROM " + T_ITEM + " WHERE water_user_id = #{userId} AND plan_id IN " +
            "<foreach collection='planIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></script>")
    int deleteUserItemsInPlans(@Param("userId") String userId, @Param("planIds") List<String> planIds);

    /** 闸口下拉（站档 epjutj 含 #4# 闸站；按站码升序） */
    @Select("<script>SELECT s.id AS id, s.iofhpi AS code, s.zzkaec AS name FROM " + T_STATION + " s " +
            "WHERE s.epjutj LIKE '%#4#%' " +
            "<if test='keyword != null and keyword != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "ORDER BY s.iofhpi</script>")
    List<GateOptionVO> selectGates(@Param("keyword") String keyword);

    /** 按 ID 批量回查闸口名称（保存时固化快照，不信任前端名称） */
    @Select("<script>SELECT s.id AS id, s.zzkaec AS name FROM " + T_STATION + " s " +
            "WHERE s.epjutj LIKE '%#4#%' AND s.id IN " +
            "<foreach collection='ids' item='gid' open='(' separator=',' close=')'>#{gid}</foreach></script>")
    List<GateOptionVO> selectGateNamesByIds(@Param("ids") List<String> ids);

    /** 用水户下拉选项（计划明细；仅正常状态，按编号升序） */
    @Select("<script>SELECT u.id AS id, u.name AS name, u.user_no AS userNo, " +
            "u.irrigation_area AS irrigationArea FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' AND u.status = '#1#' " +
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.user_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.user_no</script>")
    List<UserOptionVO> selectUserOptions(@Param("keyword") String keyword);

    /**
     * 水费台账全量用户（仅正常状态 #1#，无分页；含闸口配置原文供计量策略解析）。
     * <p>annualPlanWater 同 {@link #selectUserPage} 口径（当年已生效+已归档年度计划明细之和，无则 NULL）。
     */
    @Select("<script>SELECT " + BASE_COLS + ", " +
            "(SELECT SUM(i.plan_water) FROM " + T_ITEM + " i JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            " WHERE i.water_user_id = u.id AND p.plan_type = '#1#' AND p.status IN ('#2#', '#3#') " +
            " AND EXTRACT(YEAR FROM p.period_start) = #{year}) AS annualPlanWater " +
            "FROM " + T_USER + " u WHERE u.corp_code = 'hltgq' AND u.status = '#1#' " +
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.user_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.user_no</script>")
    List<WaterUserVO> selectLedgerUsers(@Param("keyword") String keyword,
                                        @Param("year") int year);

    /**
     * 四页模块业务引用计数（删除保护扩展）：需水上报 / 调度方案 / 调度记录 / 水费账单。
     * 任一引用存在即禁止删除（避免业务与财务留痕悬空的唯一路径为清理业务数据或保留档案）。
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM " + T_DEMAND + " WHERE water_user_id = #{userId} AND corp_code = 'hltgq') AS demands, " +
            "(SELECT COUNT(*) FROM " + T_DISPATCH + " WHERE water_user_id = #{userId} AND corp_code = 'hltgq') AS dispatches, " +
            "(SELECT COUNT(*) FROM " + T_RECORD + " WHERE water_user_id = #{userId} AND corp_code = 'hltgq') AS records, " +
            "(SELECT COUNT(*) FROM " + T_BILL + " WHERE water_user_id = #{userId} AND corp_code = 'hltgq') AS bills")
    Map<String, Object> countBusinessRefs(@Param("userId") String userId);
}
