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

import java.math.BigDecimal;
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
    String T_FLOW = "\"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\"";
    /** 平台用户表（联系人下拉数据源；与值班模块人员选择同源） */
    String T_APAAS_USER = "\"qixiao-apaas\".\"t_apaas_uc_user\"";

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
            "u.irrigation_area AS irrigationArea, u.lng AS lng, u.lat AS lat, " +
            "u.water_price AS waterPrice, " +
            "u.calc_mode AS calcMode, u.gate_config AS gateConfigRaw, u.settle_cycle AS settleCycle, " +
            "u.annual_plan_water AS annualPlanWater, u.remark AS remark, u.status AS status";

    /**
     * 列表分页：年度计划水量为档案字段（BASE_COLS 直出）；
     * 年度已调度 = 该户当年年度计划上未取消调度方案的 plan_amount 合计（调度模块回写口径）。
     */
    @Select("<script>" +
            "SELECT " + BASE_COLS + ", " +
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

    /**
     * 按 ID 集合批量查（计划灌溉进度 / 实际保证率计量用：批量取闸口配置解析口径）。
     * <p>与单查同列（BASE_COLS 含 gateConfigRaw）；不存在 / 已删除的 id 不返回。
     */
    @Select("<script>SELECT " + BASE_COLS + " FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' AND u.id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<WaterUserVO> selectUsersByIds(@Param("ids") List<String> ids);

    /** 名称唯一性预检（编辑时排除自身） */
    @Select("<script>SELECT COUNT(*) FROM " + T_USER + " WHERE corp_code = 'hltgq' AND name = #{name} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByName(@Param("name") String name, @Param("excludeId") String excludeId);

    /** 当前最大编号序号（YH + 3 位序号；表空返回 0）——新增时服务端自动取号 */
    @Select("SELECT COALESCE(MAX(CAST(substr(user_no, 3) AS INTEGER)), 0) FROM " + T_USER +
            " WHERE corp_code = 'hltgq' AND user_no ~ '^YH[0-9]+$'")
    int selectMaxUserSeq();

    /** 新增（row key：id/user_no/name/user_type/status/contact_name/contact_phone/irrigation_area/
     *  lng/lat/annual_plan_water/water_price/calc_mode/gate_config/settle_cycle/remark/now/operator） */
    @Insert("INSERT INTO " + T_USER + " " +
            "(\"id\", \"user_no\", \"name\", \"user_type\", \"contact_name\", \"contact_phone\", " +
            "\"irrigation_area\", \"lng\", \"lat\", \"annual_plan_water\", \"water_price\", \"calc_mode\", " +
            "\"gate_config\", \"settle_cycle\", \"remark\", \"status\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.user_no}, #{row.name}, #{row.user_type}, #{row.contact_name}, " +
            "#{row.contact_phone}, #{row.irrigation_area}, #{row.lng}, #{row.lat}, #{row.annual_plan_water}, " +
            "#{row.water_price}, #{row.calc_mode}, #{row.gate_config}, #{row.settle_cycle}, #{row.remark}, " +
            "#{row.status}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertUser(@Param("row") Map<String, Object> row);

    /** 编辑（编号由服务端保持原值不可改；状态可变） */
    @Update("UPDATE " + T_USER + " SET \"user_no\" = #{row.user_no}, \"name\" = #{row.name}, " +
            "\"user_type\" = #{row.user_type}, \"status\" = #{row.status}, " +
            "\"contact_name\" = #{row.contact_name}, \"contact_phone\" = #{row.contact_phone}, " +
            "\"irrigation_area\" = #{row.irrigation_area}, \"lng\" = #{row.lng}, \"lat\" = #{row.lat}, " +
            "\"annual_plan_water\" = #{row.annual_plan_water}, \"water_price\" = #{row.water_price}, " +
            "\"calc_mode\" = #{row.calc_mode}, \"gate_config\" = #{row.gate_config}, " +
            "\"settle_cycle\" = #{row.settle_cycle}, \"remark\" = #{row.remark}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
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

    /** 闸口下拉（站档 epjutj 含 #4# 闸站；按站点名称升序） */
    @Select("<script>SELECT s.id AS id, s.zzkaec AS name FROM " + T_STATION + " s " +
            "WHERE s.epjutj LIKE '%#4#%' " +
            "<if test='keyword != null and keyword != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "ORDER BY s.zzkaec</script>")
    List<GateOptionVO> selectGates(@Param("keyword") String keyword);

    /** 按 ID 批量回查闸口（保存时固化名称快照 / 户关联闸口下拉补名称） */
    @Select("<script>SELECT s.id AS id, s.zzkaec AS name FROM " + T_STATION + " s " +
            "WHERE s.epjutj LIKE '%#4#%' AND s.id IN " +
            "<foreach collection='ids' item='gid' open='(' separator=',' close=')'>#{gid}</foreach></script>")
    List<GateOptionVO> selectGateNamesByIds(@Param("ids") List<String> ids);

    /**
     * 闸口最新累计流量读数（ttf，m³）：差额策略保存校验用（主闸口读数 − 扣减合计 &gt; 0）。
     * <p>口径与账单计量一致：闸口 id 对 flow.stcd / flow.site 双列匹配；取最新一条非空 ttf；
     * 无记录返回 null（读数缺失时保存校验跳过）。哨兵值由 Service 经 usableFlow 过滤。
     */
    @Select("SELECT f.ttf FROM " + T_FLOW + " f WHERE (f.stcd = #{gateId} OR f.site = #{gateId}) " +
            "AND f.ttf IS NOT NULL ORDER BY f.tm DESC LIMIT 1")
    BigDecimal selectLatestGateReading(@Param("gateId") String gateId);

    /** 联系人候选（下拉用：平台用户 id + 姓名 + 手机号；本单位 hltgq，按姓名升序） */
    @Select("SELECT u.id AS id, u.name AS name, u.mobile AS mobile FROM " + T_APAAS_USER + " u " +
            "WHERE u.corp_code = 'hltgq' ORDER BY u.name ASC, u.id ASC")
    List<Map<String, Object>> selectContactOptions();

    /**
     * 平台用户选人候选池（id + 姓名；本单位 hltgq，按姓名升序）。
     * <p>调度负责人 / 调度记录操作人下拉共用（不需手机号，故不复用上方的联系人候选）。
     */
    @Select("SELECT u.id AS id, u.name AS name FROM " + T_APAAS_USER + " u " +
            "WHERE u.corp_code = 'hltgq' AND u.name IS NOT NULL AND u.name != '' " +
            "ORDER BY u.name ASC, u.id ASC")
    List<Map<String, Object>> selectPlatformUserOptions();

    /** 按 id 回查平台用户（保存时固化姓名/手机号快照，不信任前端传入） */
    @Select("SELECT u.id AS id, u.name AS name, u.mobile AS mobile FROM " + T_APAAS_USER + " u " +
            "WHERE u.id = #{id} AND u.corp_code = 'hltgq'")
    Map<String, Object> selectPlatformUserById(@Param("id") String id);

    /**
     * 用水户下拉选项（计划明细 / 需水上报 / 调度方案编制；仅正常状态，按编号升序）。
     * <p>planId 非空时仅返回该计划明细内的户（编制弹窗「选计划 → 户下拉收敛」）。
     */
    @Select("<script>SELECT u.id AS id, u.name AS name, u.user_no AS userNo, " +
            "u.irrigation_area AS irrigationArea FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' AND u.status = '#1#' " +
            "<if test='planId != null and planId != \"\"'>AND EXISTS (SELECT 1 FROM " + T_ITEM + " i " +
            "WHERE i.plan_id = #{planId} AND i.water_user_id = u.id) </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.user_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.user_no</script>")
    List<UserOptionVO> selectUserOptions(@Param("keyword") String keyword, @Param("planId") String planId);

    /**
     * 水费台账全量用户（仅正常状态 #1#，无分页；含闸口配置原文供计量策略解析）。
     * <p>annualPlanWater 为档案字段（BASE_COLS 直出）。
     */
    @Select("<script>SELECT " + BASE_COLS + " " +
            "FROM " + T_USER + " u WHERE u.corp_code = 'hltgq' AND u.status = '#1#' " +
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.user_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.user_no</script>")
    List<WaterUserVO> selectLedgerUsers(@Param("keyword") String keyword);

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
