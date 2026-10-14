package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.BillPlanRowVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DispatchOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调度方案（t_auto_hltgq_water_dispatch）。表结构见 init.sql 灌区用水管理 2.2 / water-dispatch-bill-ddl.sql。
 * <p>行 = 闸口 × 时段的放水载体：计入水量 = 该闸口 [start_time, end_time] 实测累计流量（带符号），
 * 出账时以 billed_qty 快照计费、bill_id 挂接账单。
 * <p>状态机：待执行(#1#) →（开始执行）执行中(#2#) →（完成）已完成(#3#)；待执行/执行中 →（取消）已取消(#4#)。
 * 已计费（bill_id 非空）禁改、禁取消、禁删，须先删除关联账单恢复未计费。
 * <p>配额闭环：关联计划时受「该计划该户 plan_water 合计」约束（lockPlanForUpdate 锁行防并发），
 * 每次创建/编辑/调整/删除/取消后经 recalcPlanScheduled 回写计划 scheduled_qty（= Σ 未取消方案 plan_amount）。
 * <p>编号 DD + 4 位序号：应用层 MAX+1 取号、唯一索引 uk_water_dispatch_no 兜底并发（Controller 重试）。
 */
public interface WaterDispatchMapper {

    String T_DISPATCH = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch\"";
    String T_RECORD = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch_record\"";
    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_STATION = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";
    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";
    String T_PLAN_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan_item\"";
    String T_DEMAND = "\"qixiao-apaas\".\"t_auto_hltgq_water_demand\"";
    String T_BILL = "\"qixiao-apaas\".\"t_auto_hltgq_water_bill\"";

    /** 列表条件（page/count 共用，作用于别名 d；status 传库内 #N# 编码） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (d.dispatch_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.name LIKE CONCAT('%', #{keyword}, '%') OR u.user_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR s.zzkaec LIKE CONCAT('%', #{keyword}, '%') OR s.iofhpi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='status != null and status != \"\"'>AND d.status = #{status} </if>" +
            "<if test='planId != null and planId != \"\"'>AND d.plan_id = #{planId} </if>";

    /** 基础列（列表/详情共用；plan_amount 库内 m³，Service 出口折算万m³；trail = 记录数） */
    String BASE_COLS = "d.id AS id, d.dispatch_no AS dispatchNo, " +
            "to_char(d.created_at, 'YYYY-MM-DD HH24:MI') AS createTime, " +
            "d.plan_id AS planId, p.plan_no AS planNo, p.plan_name AS planName, " +
            "d.demand_id AS demandId, dm.demand_no AS demandNo, " +
            "d.water_user_id AS waterUserId, u.user_no AS waterUserNo, u.name AS waterUserName, " +
            "d.gate_id AS gateId, s.iofhpi AS gateCode, s.zzkaec AS gateName, " +
            "d.plan_amount AS planAmount, " +
            "to_char(d.start_time, 'YYYY-MM-DD HH24:MI') AS startTime, " +
            "to_char(d.end_time, 'YYYY-MM-DD HH24:MI') AS endTime, " +
            "d.owner AS owner, d.status AS status, d.bill_id AS billId, b.bill_no AS billNo, " +
            "(SELECT COUNT(*) FROM " + T_RECORD + " r WHERE r.dispatch_id = d.id) AS trail, " +
            "d.remark AS remark";

    /** 基础联表（户/站档/计划/上报/账单；别名 d） */
    String BASE_JOINS = " FROM " + T_DISPATCH + " d " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.water_user_id " +
            "LEFT JOIN " + T_STATION + " s ON s.id = d.gate_id " +
            "LEFT JOIN " + T_PLAN + " p ON p.id = d.plan_id " +
            "LEFT JOIN " + T_DEMAND + " dm ON dm.id = d.demand_id " +
            "LEFT JOIN " + T_BILL + " b ON b.id = d.bill_id ";

    /** 列表分页（编号倒序） */
    @Select("<script>SELECT " + BASE_COLS + BASE_JOINS + "WHERE d.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY d.dispatch_no DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<WaterDispatchVO> selectDispatchPage(@Param("keyword") String keyword,
                                             @Param("status") String status,
                                             @Param("planId") String planId,
                                             @Param("size") int size,
                                             @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectDispatchPage} 一致） */
    @Select("<script>SELECT COUNT(*)" + BASE_JOINS + "WHERE d.corp_code = 'hltgq' " + LIST_CONDITIONS + "</script>")
    long countDispatches(@Param("keyword") String keyword,
                         @Param("status") String status,
                         @Param("planId") String planId);

    /** 按 ID 查（详情/流转前置校验共用） */
    @Select("SELECT " + BASE_COLS + BASE_JOINS + "WHERE d.id = #{id} AND d.corp_code = 'hltgq'")
    WaterDispatchVO selectDispatchById(@Param("id") String id);

    /** 按 ID 集合批量查（出账校验与计量共用） */
    @Select("<script>SELECT " + BASE_COLS + BASE_JOINS +
            "WHERE d.corp_code = 'hltgq' AND d.id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>" +
            "ORDER BY d.dispatch_no</script>")
    List<WaterDispatchVO> selectDispatchesByIds(@Param("ids") List<String> ids);

    /** 当前最大编号序号（DD + 4 位；表空返回 0） */
    @Select("SELECT COALESCE(MAX(CAST(substr(dispatch_no, 3) AS INTEGER)), 0) FROM " + T_DISPATCH +
            " WHERE corp_code = 'hltgq' AND dispatch_no ~ '^DD[0-9]+$'")
    int selectMaxDispatchSeq();

    /** 新增（row key：id/dispatch_no/plan_id/demand_id/water_user_id/gate_id/plan_amount/start_time/end_time/owner/remark/now/operator） */
    @Insert("INSERT INTO " + T_DISPATCH + " " +
            "(\"id\", \"dispatch_no\", \"plan_id\", \"demand_id\", \"water_user_id\", \"gate_id\", \"plan_amount\", " +
            "\"start_time\", \"end_time\", \"owner\", \"status\", \"remark\", \"corp_code\", \"created_at\", " +
            "\"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.dispatch_no}, #{row.plan_id}, #{row.demand_id}, #{row.water_user_id}, " +
            "#{row.gate_id}, #{row.plan_amount}, #{row.start_time}, #{row.end_time}, #{row.owner}, '#1#', " +
            "#{row.remark}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertDispatch(@Param("row") Map<String, Object> row);

    /** 编辑（仅待执行且未计费：全字段；状态流转经 updateDispatchStatus/cancelDispatch，水费页调整经 adjustDispatch） */
    @Update("UPDATE " + T_DISPATCH + " SET \"plan_id\" = #{row.plan_id}, \"demand_id\" = #{row.demand_id}, " +
            "\"water_user_id\" = #{row.water_user_id}, \"gate_id\" = #{row.gate_id}, " +
            "\"plan_amount\" = #{row.plan_amount}, \"start_time\" = #{row.start_time}, " +
            "\"end_time\" = #{row.end_time}, \"owner\" = #{row.owner}, \"remark\" = #{row.remark}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"status\" = '#1#' AND \"bill_id\" IS NULL AND \"corp_code\" = 'hltgq'")
    int updateDispatch(@Param("row") Map<String, Object> row);

    /**
     * 调整（水费页「编辑水量」：计划放水量/执行时段/备注）：未计费且未取消即可（不限执行态），
     * 台账与待出账金额随新时段实时重算、账期归属按新开始时间推导。
     */
    @Update("UPDATE " + T_DISPATCH + " SET \"plan_amount\" = #{row.plan_amount}, " +
            "\"start_time\" = #{row.start_time}, \"end_time\" = #{row.end_time}, \"remark\" = #{row.remark}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"bill_id\" IS NULL AND \"status\" != '#4#' AND \"corp_code\" = 'hltgq'")
    int adjustDispatch(@Param("row") Map<String, Object> row);

    /** 状态流转（带原状态守卫；影响行数 0 = 状态已变更，报「请刷新」） */
    @Update("UPDATE " + T_DISPATCH + " SET \"status\" = #{toStatus}, \"updated_at\" = #{now}, " +
            "\"updated_by\" = #{operator} WHERE \"id\" = #{id} AND \"status\" = #{fromStatus} " +
            "AND \"corp_code\" = 'hltgq'")
    int updateDispatchStatus(@Param("id") String id,
                             @Param("fromStatus") String fromStatus,
                             @Param("toStatus") String toStatus,
                             @Param("now") LocalDateTime now,
                             @Param("operator") String operator);

    /** 取消（待执行/执行中 → 已取消；已计费不可取消，须先删除关联账单） */
    @Update("UPDATE " + T_DISPATCH + " SET \"status\" = '#4#', \"updated_at\" = #{now}, " +
            "\"updated_by\" = #{operator} WHERE \"id\" = #{id} AND \"status\" IN ('#1#', '#2#') " +
            "AND \"bill_id\" IS NULL AND \"corp_code\" = 'hltgq'")
    int cancelDispatch(@Param("id") String id,
                       @Param("now") LocalDateTime now,
                       @Param("operator") String operator);

    /** 物理删除（未计费即可；记录挂接由 clearRecordDispatchRefs 置空留痕，Service 顺序调用） */
    @Delete("DELETE FROM " + T_DISPATCH + " WHERE id = #{id} AND bill_id IS NULL AND corp_code = 'hltgq'")
    int deleteDispatch(@Param("id") String id);

    /** 记录挂接置空（方案删除时调用：历史操作留痕不随方案消失） */
    @Update("UPDATE " + T_RECORD + " SET \"dispatch_id\" = NULL, \"updated_at\" = #{now}, " +
            "\"updated_by\" = #{operator} WHERE \"dispatch_id\" = #{dispatchId}")
    int clearRecordDispatchRefs(@Param("dispatchId") String dispatchId,
                                @Param("now") LocalDateTime now,
                                @Param("operator") String operator);

    /** 账单挂接清空（账单删除时调用：方案恢复未计费，恢复实时计量、可重新出账） */
    @Update("UPDATE " + T_DISPATCH + " SET \"bill_id\" = NULL, \"billed_qty\" = NULL, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} WHERE \"bill_id\" = #{billId}")
    int clearBillRefs(@Param("billId") String billId,
                      @Param("now") LocalDateTime now,
                      @Param("operator") String operator);

    /** 出账打标（bill_id IS NULL 守卫防并发重复计费；billedQty = 出账时点计入水量快照，带符号） */
    @Update("UPDATE " + T_DISPATCH + " SET \"bill_id\" = #{billId}, \"billed_qty\" = #{billedQty}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"bill_id\" IS NULL AND \"corp_code\" = 'hltgq'")
    int markBilled(@Param("id") String id,
                   @Param("billId") String billId,
                   @Param("billedQty") BigDecimal billedQty,
                   @Param("now") LocalDateTime now,
                   @Param("operator") String operator);

    /** 本计划中该用水户的计划水量合计（m³；无明细返回 0）——配额分母 */
    @Select("SELECT COALESCE(SUM(i.plan_water), 0) FROM " + T_PLAN_ITEM + " i " +
            "WHERE i.plan_id = #{planId} AND i.water_user_id = #{userId}")
    BigDecimal selectPlanUserTotal(@Param("planId") String planId, @Param("userId") String userId);

    /** 该计划该户已下达合计（未取消方案 plan_amount，m³；编辑/调整时经 excludeId 排除自身）——配额分子 */
    @Select("<script>SELECT COALESCE(SUM(d.plan_amount), 0) FROM " + T_DISPATCH + " d " +
            "WHERE d.plan_id = #{planId} AND d.water_user_id = #{userId} AND d.status != '#4#' " +
            "AND d.corp_code = 'hltgq' " +
            "<if test='excludeId != null and excludeId != \"\"'>AND d.id != #{excludeId} </if></script>")
    BigDecimal selectActiveSumByPlanUser(@Param("planId") String planId,
                                         @Param("userId") String userId,
                                         @Param("excludeId") String excludeId);

    /** 锁用水计划行（编制/编辑/调整方案事务内 FOR UPDATE：并发下达不超配额；不存在返回 null） */
    @Select("SELECT id FROM " + T_PLAN + " WHERE id = #{planId} AND corp_code = 'hltgq' FOR UPDATE")
    String lockPlanForUpdate(@Param("planId") String planId);

    /** 回写计划的已下达调度水量（scheduled_qty = Σ 未取消方案 plan_amount；创建/编辑/调整/删除/取消/恢复后调用） */
    @Update("UPDATE " + T_PLAN + " SET \"scheduled_qty\" = COALESCE((" +
            "SELECT SUM(d.plan_amount) FROM " + T_DISPATCH + " d " +
            "WHERE d.plan_id = #{planId} AND d.status != '#4#' AND d.corp_code = 'hltgq'), 0), " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{planId} AND \"corp_code\" = 'hltgq'")
    int recalcPlanScheduled(@Param("planId") String planId,
                            @Param("now") LocalDateTime now,
                            @Param("operator") String operator);

    /**
     * 该户未取消方案明细（水费台账/明细/出账共用）。
     * <p>amount 暂载 billed_qty 计费快照（未计费为 null，由 Service 实时计量覆盖）；
     * billed = 已计费（bill_id 非空）；billNo 为挂接账单号。
     */
    @Select("SELECT d.id AS dispatchId, d.dispatch_no AS dispatchNo, d.plan_id AS planId, " +
            "p.plan_name AS planName, d.gate_id AS gateId, s.iofhpi AS gateCode, s.zzkaec AS gateName, " +
            "to_char(d.start_time, 'YYYY-MM-DD HH24:MI') AS startTime, " +
            "to_char(d.end_time, 'YYYY-MM-DD HH24:MI') AS endTime, " +
            "d.plan_amount AS planAmount, d.billed_qty AS amount, b.bill_no AS billNo, " +
            "(d.bill_id IS NOT NULL) AS billed, d.status AS status, " +
            "(SELECT COUNT(*) FROM " + T_RECORD + " r WHERE r.dispatch_id = d.id) AS trail " +
            "FROM " + T_DISPATCH + " d " +
            "LEFT JOIN " + T_STATION + " s ON s.id = d.gate_id " +
            "LEFT JOIN " + T_PLAN + " p ON p.id = d.plan_id " +
            "LEFT JOIN " + T_BILL + " b ON b.id = d.bill_id " +
            "WHERE d.water_user_id = #{userId} AND d.corp_code = 'hltgq' AND d.status != '#4#' " +
            "ORDER BY d.start_time, d.dispatch_no")
    List<BillPlanRowVO> selectActivePlansByUser(@Param("userId") String userId);

    /** 方案下拉（调度记录「关联调度方案」：未取消全部，编号倒序） */
    @Select("<script>SELECT d.id AS id, d.dispatch_no AS dispatchNo, d.water_user_id AS waterUserId, " +
            "u.user_no AS waterUserNo, u.name AS waterUserName, d.gate_id AS gateId, " +
            "s.iofhpi AS gateCode, s.zzkaec AS gateName, d.status AS status " + BASE_JOINS +
            "WHERE d.corp_code = 'hltgq' AND d.status != '#4#' " +
            "<if test='keyword != null and keyword != \"\"'>AND (d.dispatch_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.name LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY d.dispatch_no DESC LIMIT 200</script>")
    List<DispatchOptionVO> selectActivePlanOptions(@Param("keyword") String keyword);

    /** 已生效计划下拉（方案「关联用水计划」：仅已生效 #2#，编号倒序；planType 由 Service 转中文名） */
    @Select("<script>SELECT p.id AS id, p.plan_no AS planNo, p.plan_name AS planName, " +
            "p.plan_type AS planType, p.total_water AS totalWater, " +
            "to_char(p.period_start, 'YYYY-MM-DD') AS periodStart, " +
            "to_char(p.period_end, 'YYYY-MM-DD') AS periodEnd " +
            "FROM " + T_PLAN + " p WHERE p.corp_code = 'hltgq' AND p.status = '#2#' " +
            "<if test='keyword != null and keyword != \"\"'>AND p.plan_name LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "ORDER BY p.plan_no DESC LIMIT 200</script>")
    List<WaterPlanVO> selectActivePlanOptionsForPick(@Param("keyword") String keyword);

    /** 状态计数（Tab 角标：全部/待执行/执行中/已完成/已取消；key：status/total） */
    @Select("SELECT d.status AS status, COUNT(*) AS total FROM " + T_DISPATCH +
            " d WHERE d.corp_code = 'hltgq' GROUP BY d.status")
    List<Map<String, Object>> countByStatus();
}
