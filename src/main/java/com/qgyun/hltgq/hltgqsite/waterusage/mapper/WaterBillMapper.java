package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.BillPlanRowVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.GateIntervalVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterBillVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 水费账单（t_auto_hltgq_water_bill）。表结构见 init.sql 灌区用水管理 2.4 / water-dispatch-bill-ddl.sql。
 * <p>计费关系：1 账单 : N 方案（dispatch.bill_id 反向挂接）；quantity 为出账时点 Σ方案计入水量快照（带符号）；
 * 账单删除时由 Service 先 clearBillRefs（方案恢复未计费、实时重新计量）再物理删除。
 * <p>编号：SF + 期间码 + '-' + 户号 + 4 位全局序号（如 SF202610-YH0010001）：应用层
 * MAX(right(bill_no,4))+1 取号、唯一索引 uk_water_bill_no 兜底并发（Controller 重试）。
 * <p>另含单闸口区间流量取数（计入水量计量）：口径与流量监测/灌溉用水一致——
 * 区间累计 = ttf(窗口内末行) − ttf(起点前最近一条 ttf 非空行)，起点前无基准按 0。
 */
public interface WaterBillMapper {

    String T_BILL = "\"qixiao-apaas\".\"t_auto_hltgq_water_bill\"";
    String T_DISPATCH = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch\"";
    String T_RECORD = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch_record\"";
    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_STATION = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";
    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";
    String T_FLOW = "\"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\"";
    /** 手工用水台账（来源反显：账单 ledger_id → 台账编号） */
    String T_LEDGER = "\"qixiao-apaas\".\"t_auto_hltgq_water_ledger\"";

    /** 列表条件（page/count 共用，作用于别名 b；keyword 匹配账单号/户名/户号/分期 key） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (b.bill_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.name LIKE CONCAT('%', #{keyword}, '%') OR u.user_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR b.cycle_key LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='status != null and status != \"\"'>AND b.status = #{status} </if>";

    /** 基础列（列表/详情共用；quantity 库内 m³，Service 出口折算万m³；status 出口映射英文码） */
    String BASE_COLS = "b.id AS id, b.bill_no AS billNo, b.water_user_id AS waterUserId, " +
            "u.user_no AS waterUserNo, u.name AS waterUserName, " +
            "b.settle_cycle AS cycle, b.cycle_key AS cycleKey, " +
            "to_char(b.period_start, 'YYYY-MM-DD') AS startDate, " +
            "to_char(b.period_end, 'YYYY-MM-DD') AS endDate, " +
            "b.quantity AS quantity, b.price AS price, b.amount AS amount, b.paid AS paid, " +
            "b.status AS status, " +
            "to_char(b.created_at, 'YYYY-MM-DD HH24:MI') AS createTime, " +
            "to_char(b.pay_time, 'YYYY-MM-DD HH24:MI') AS payTime, " +
            "b.ledger_id AS ledgerId, l.ledger_no AS ledgerNo, " +
            "(SELECT COUNT(*) FROM " + T_DISPATCH + " d WHERE d.bill_id = b.id) AS planCount";

    /** 基础联表（户 + 来源手工台账；别名 b/u/l） */
    String BASE_JOINS = " FROM " + T_BILL + " b " +
            "LEFT JOIN " + T_USER + " u ON u.id = b.water_user_id " +
            "LEFT JOIN " + T_LEDGER + " l ON l.id = b.ledger_id ";

    /** 列表分页（编号倒序，最新账单在前） */
    @Select("<script>SELECT " + BASE_COLS + BASE_JOINS + "WHERE b.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY b.bill_no DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<WaterBillVO> selectBillPage(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("size") int size,
                                     @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectBillPage} 一致） */
    @Select("<script>SELECT COUNT(*)" + BASE_JOINS + "WHERE b.corp_code = 'hltgq' " + LIST_CONDITIONS + "</script>")
    long countBills(@Param("keyword") String keyword,
                    @Param("status") String status);

    /** 按 ID 查（详情/收款/删除前置校验共用） */
    @Select("SELECT " + BASE_COLS + BASE_JOINS + "WHERE b.id = #{id} AND b.corp_code = 'hltgq'")
    WaterBillVO selectBillById(@Param("id") String id);

    /** 当前最大编号尾序号（right(bill_no,4)；表空返回 0）——账单号全局序号 MAX+1 取号 */
    @Select("SELECT COALESCE(MAX(CAST(right(bill_no, 4) AS INTEGER)), 0) FROM " + T_BILL +
            " WHERE corp_code = 'hltgq' AND bill_no LIKE 'SF%' AND right(bill_no, 4) ~ '^[0-9]+$'")
    int selectMaxBillSeq();

    /** 新增（row key：id/bill_no/water_user_id/settle_cycle/cycle_key/period_start/period_end/quantity/price/amount/ledger_id/now/operator；状态固定 #1# 已生成、paid=0；ledger_id 缺失按 null） */
    @Insert("INSERT INTO " + T_BILL + " " +
            "(\"id\", \"bill_no\", \"water_user_id\", \"settle_cycle\", \"cycle_key\", \"period_start\", " +
            "\"period_end\", \"quantity\", \"price\", \"amount\", \"paid\", \"status\", \"ledger_id\", \"corp_code\", " +
            "\"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.bill_no}, #{row.water_user_id}, #{row.settle_cycle}, #{row.cycle_key}, " +
            "CAST(#{row.period_start} AS date), CAST(#{row.period_end} AS date), #{row.quantity}, #{row.price}, " +
            "#{row.amount}, 0, '#1#', #{row.ledger_id}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertBill(@Param("row") Map<String, Object> row);

    /** 确认收款（仅已生成 #1#：已缴清 #2#、实收 = 应收、记录收款时间） */
    @Update("UPDATE " + T_BILL + " SET \"status\" = '#2#', \"paid\" = \"amount\", \"pay_time\" = #{now}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"status\" = '#1#' AND \"corp_code\" = 'hltgq'")
    int payBill(@Param("id") String id,
                @Param("now") LocalDateTime now,
                @Param("operator") String operator);

    /** 物理删除（任意状态可删；方案挂接清空由 clearBillRefs 先执行，方案恢复未计费可重新出账） */
    @Delete("DELETE FROM " + T_BILL + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteBill(@Param("id") String id);

    /** 计量窗口起（与 WaterDispatchMapper.WINDOW_START 同口径：执行时间优先，旧数据回落原执行时段起） */
    String WINDOW_START = "COALESCE(d.exec_time, d.start_time)";
    /** 计量窗口止（完成时间 → 旧执行时段止 → 当前时间） */
    String WINDOW_END = "COALESCE(d.finish_time, d.end_time, CURRENT_TIMESTAMP)";

    /**
     * 账单指令明细（详情 plans / 按指令出账后回显共用）：
     * amount 取 billed_qty 计费快照、sign 由快照符号推导；cycleKey/cycleLabel 由 Service 按账单期填充。
     * startTime/endTime 为计量窗口（执行时间 ~ 完成时间），旧数据回落原执行时段。
     */
    @Select("SELECT d.id AS dispatchId, d.dispatch_no AS dispatchNo, d.plan_id AS planId, " +
            "p.plan_name AS planName, d.gate_id AS gateId, s.zzkaec AS gateName, " +
            "CASE WHEN d.billed_qty < 0 THEN -1 ELSE 1 END AS sign, " +
            "to_char(" + WINDOW_START + ", 'YYYY-MM-DD HH24:MI') AS startTime, " +
            "to_char(" + WINDOW_END + ", 'YYYY-MM-DD HH24:MI') AS endTime, " +
            "d.plan_amount AS planAmount, d.billed_qty AS amount, b.bill_no AS billNo, TRUE AS billed, " +
            "d.status AS status, " +
            "(SELECT COUNT(*) FROM " + T_RECORD + " r WHERE r.dispatch_id = d.id) AS trail " +
            "FROM " + T_DISPATCH + " d " +
            "LEFT JOIN " + T_STATION + " s ON s.id = d.gate_id " +
            "LEFT JOIN " + T_PLAN + " p ON p.id = d.plan_id " +
            "LEFT JOIN " + T_BILL + " b ON b.id = d.bill_id " +
            "WHERE d.bill_id = #{billId} AND d.corp_code = 'hltgq' " +
            "ORDER BY " + WINDOW_START + ", d.dispatch_no")
    List<BillPlanRowVO> selectBillPlanRows(@Param("billId") String billId);

    /**
     * 单闸口区间流量取数（计入水量计量核心）。
     * <p>站点标识 skey = COALESCE(stcd, site)：入参闸口 id 对 f.stcd / f.site 双列匹配（任一命中）。
     * 窗口内无有效行不返回；有行时 ttf 为窗口内**末条有效累计流量行**、prevTtf 为起点前最近一条有效累计流量行
     * （可为 null 按 0）。两端同口径过滤 ttf 缺失（NULL / 哨兵 -999 设备不存在 / -9991 设备异常），
     * 否则末行缺报会被判为计量不可得、起点前命中哨兵会算出虚高水量。
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.skey) t.skey AS site, t.stnm, t.tm, t.ttf, fq_prev.prev_ttf " +
            "FROM ( " +
            "  SELECT COALESCE(f.stcd, f.site) AS skey, COALESCE(s.zzkaec, f.stcd, f.site) AS stnm, f.tm, f.ttf " +
            "  FROM " + T_FLOW + " f " +
            "  LEFT JOIN " + T_STATION + " s ON f.site = s.id " +
            "  WHERE (f.stcd = #{gateId} OR f.site = #{gateId}) " +
            "  AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991) " +
            "  AND f.tm &gt;= #{startTime} AND f.tm &lt;= #{endTime} " +
            ") t " +
            "LEFT JOIN ( " +
            "  SELECT DISTINCT ON (t2.skey) t2.skey, t2.ttf AS prev_ttf " +
            "  FROM ( " +
            "    SELECT COALESCE(f.stcd, f.site) AS skey, f.tm, f.ttf " +
            "    FROM " + T_FLOW + " f " +
            "    WHERE f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991) AND f.tm &lt; #{startTime} " +
            "    AND (f.stcd = #{gateId} OR f.site = #{gateId}) " +
            "  ) t2 " +
            "  ORDER BY t2.skey, t2.tm DESC " +
            ") fq_prev ON fq_prev.skey = t.skey " +
            "ORDER BY t.skey, t.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "site", property = "site"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "ttf", property = "ttf"),
            @Result(column = "prev_ttf", property = "prevTtf")
    })
    List<GateIntervalVO> selectGateInterval(@Param("gateId") String gateId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 账单聚合（台账/汇总视图按户）：uid 用水户 ID、cnt 账单数、amt Σ应收（元）、paid Σ已收（元）。
     * <p>列别名全小写（KES 未加引号别名折叠小写，Map key 即此），Service 按小写读取。
     */
    @Select("SELECT b.water_user_id AS uid, COUNT(*) AS cnt, " +
            "COALESCE(SUM(b.amount), 0) AS amt, COALESCE(SUM(b.paid), 0) AS paid " +
            "FROM " + T_BILL + " b WHERE b.corp_code = 'hltgq' GROUP BY b.water_user_id")
    List<Map<String, Object>> selectBillAggAll();

    /**
     * 账单全量汇总（列表统计条，无筛选参数）：cnt 账单总数、qty Σ计费水量（m³）、
     * amt Σ应收（元）、paid Σ已收（元）。
     * <p>列别名全小写（同 selectBillAggAll），Service 按小写读取并折算万m³、计算收缴率。
     */
    @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(b.quantity), 0) AS qty, " +
            "COALESCE(SUM(b.amount), 0) AS amt, COALESCE(SUM(b.paid), 0) AS paid " +
            "FROM " + T_BILL + " b WHERE b.corp_code = 'hltgq'")
    Map<String, Object> selectBillStats();
}
