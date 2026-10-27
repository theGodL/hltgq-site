package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterLedgerUserVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterLedgerVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 手工水费台账（t_auto_hltgq_water_ledger）。表结构见 init.sql 灌区用水管理 2.5 / water-bill-ledger-ddl.sql。
 * <p>计费关系：1 台账 : 1 账单——台账 bill_id 正向挂接、账单 ledger_id 反向留痕（方案出账为空）。
 * 出账后台账禁改禁删（UPDATE / DELETE 均带 {@code bill_id IS NULL} 条件），账单删除时按 bill_id
 * 清空挂接、台账恢复未计费可重新出账。
 * <p>编号 TZ + 期间码 + '-' + 户号 + 4 位全局序号（如 TZ202610-YH0010001）：应用层
 * MAX(right(ledger_no,4))+1 取号、唯一索引 uk_water_ledger_no 兜底并发（Controller 重试）。
 */
public interface WaterLedgerMapper {

    String T_LEDGER = "\"qixiao-apaas\".\"t_auto_hltgq_water_ledger\"";
    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_BILL = "\"qixiao-apaas\".\"t_auto_hltgq_water_bill\"";

    /**
     * 列表条件（作用于别名 l / u；keyword 匹配台账编号、户名、户号、结算周期 key）：
     * unbilled=1 仅未出账、0 仅已出账、null 全部。
     */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (l.ledger_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.name LIKE CONCAT('%', #{keyword}, '%') OR u.user_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR l.cycle_key LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='unbilled != null and unbilled == 1'>AND l.bill_id IS NULL </if>" +
            "<if test='unbilled != null and unbilled == 0'>AND l.bill_id IS NOT NULL </if>";

    /** 基础列（列表 / 按 ID 查共用；volume 库内 m³，price 元/m³，amount 元） */
    String BASE_COLS = "l.id AS id, l.ledger_no AS ledgerNo, l.water_user_id AS waterUserId, " +
            "u.user_no AS waterUserNo, u.name AS waterUserName, " +
            "l.settle_cycle AS settleCycle, l.cycle_key AS cycleKey, " +
            "l.volume AS volume, l.price AS price, l.amount AS amount, " +
            "l.bill_id AS billId, b.bill_no AS billNo, b.status AS billStatus, " +
            "l.remark AS remark, " +
            "to_char(l.created_at, 'YYYY-MM-DD HH24:MI') AS createTime";

    /** 基础联表（户 + 已出账账单；别名 l / u / b） */
    String BASE_JOINS = " FROM " + T_LEDGER + " l " +
            "LEFT JOIN " + T_USER + " u ON u.id = l.water_user_id " +
            "LEFT JOIN " + T_BILL + " b ON b.id = l.bill_id ";

    /** 列表（无分页，最新在前；筛选条件与出账状态见 {@link #LIST_CONDITIONS}） */
    @Select("<script>SELECT " + BASE_COLS + BASE_JOINS + "WHERE l.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY l.created_at DESC, l.ledger_no DESC</script>")
    List<WaterLedgerVO> selectLedgerList(@Param("keyword") String keyword,
                                         @Param("unbilled") Integer unbilled);

    /** 按 ID 查（编辑 / 删除 / 出账前置校验共用） */
    @Select("SELECT " + BASE_COLS + BASE_JOINS + "WHERE l.id = #{id} AND l.corp_code = 'hltgq'")
    WaterLedgerVO selectLedgerById(@Param("id") String id);

    /** 当前最大编号尾序号（right(ledger_no,4)；表空返回 0）——台账号全局序号 MAX+1 取号 */
    @Select("SELECT COALESCE(MAX(CAST(right(ledger_no, 4) AS INTEGER)), 0) FROM " + T_LEDGER +
            " WHERE corp_code = 'hltgq' AND ledger_no LIKE 'TZ%' AND right(ledger_no, 4) ~ '^[0-9]+$'")
    int selectMaxLedgerSeq();

    /** 新增（row key：id/ledger_no/water_user_id/settle_cycle/cycle_key/volume/price/amount/remark/now/operator） */
    @Insert("INSERT INTO " + T_LEDGER + " " +
            "(\"id\", \"ledger_no\", \"water_user_id\", \"settle_cycle\", \"cycle_key\", \"volume\", " +
            "\"price\", \"amount\", \"remark\", \"corp_code\", " +
            "\"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.ledger_no}, #{row.water_user_id}, #{row.settle_cycle}, #{row.cycle_key}, " +
            "#{row.volume}, #{row.price}, #{row.amount}, #{row.remark}, 'hltgq', " +
            "#{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertLedger(@Param("row") Map<String, Object> row);

    /** 编辑（仅未出账：bill_id IS NULL 条件防并发；编号保持原值不重编） */
    @Update("UPDATE " + T_LEDGER + " SET \"water_user_id\" = #{row.water_user_id}, " +
            "\"settle_cycle\" = #{row.settle_cycle}, \"cycle_key\" = #{row.cycle_key}, " +
            "\"volume\" = #{row.volume}, \"price\" = #{row.price}, \"amount\" = #{row.amount}, " +
            "\"remark\" = #{row.remark}, \"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq' AND \"bill_id\" IS NULL")
    int updateLedger(@Param("row") Map<String, Object> row);

    /** 物理删除（仅未出账：bill_id IS NULL 条件防并发） */
    @Delete("DELETE FROM " + T_LEDGER +
            " WHERE id = #{id} AND corp_code = 'hltgq' AND bill_id IS NULL")
    int deleteLedger(@Param("id") String id);

    /** 出账挂接账单（仅未出账：bill_id IS NULL 条件防并发抢标） */
    @Update("UPDATE " + T_LEDGER + " SET \"bill_id\" = #{billId}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"corp_code\" = 'hltgq' AND \"bill_id\" IS NULL")
    int markLedgerBilled(@Param("id") String id,
                         @Param("billId") String billId,
                         @Param("now") LocalDateTime now,
                         @Param("operator") String operator);

    /** 账单删除恢复：按账单 ID 清空台账挂接（台账回到未出账，可重新出账） */
    @Update("UPDATE " + T_LEDGER + " SET \"bill_id\" = NULL, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"bill_id\" = #{billId} AND \"corp_code\" = 'hltgq'")
    int clearBillRefByBillId(@Param("billId") String billId,
                             @Param("now") LocalDateTime now,
                             @Param("operator") String operator);

    /**
     * 台账全量统计（统计条用，无筛选参数）：cnt 台账总数、pendingcnt 未出账条数、
     * pendingvol 未出账用水量合计（m³）、pendingamt 未出账应收合计（元）、billedamt 已出账应收合计（元）。
     * <p>列别名全小写（KES 未加引号别名折叠小写，Map key 即此），Service 按小写读取。
     */
    @Select("SELECT COUNT(*) AS cnt, " +
            "COALESCE(SUM(CASE WHEN l.bill_id IS NULL THEN 1 ELSE 0 END), 0) AS pendingcnt, " +
            "COALESCE(SUM(CASE WHEN l.bill_id IS NULL THEN l.volume ELSE 0 END), 0) AS pendingvol, " +
            "COALESCE(SUM(CASE WHEN l.bill_id IS NULL THEN l.amount ELSE 0 END), 0) AS pendingamt, " +
            "COALESCE(SUM(CASE WHEN l.bill_id IS NOT NULL THEN l.amount ELSE 0 END), 0) AS billedamt " +
            "FROM " + T_LEDGER + " l WHERE l.corp_code = 'hltgq'")
    Map<String, Object> selectLedgerStat();

    /**
     * 用水户下拉（手工台账新增弹窗；仅正常状态 #1#，含档案单价与统计周期）。
     * <p>keyword 匹配户名 / 户号；按编号升序。
     */
    @Select("<script>SELECT u.id AS id, u.name AS name, u.user_no AS userNo, " +
            "u.water_price AS price, u.settle_cycle AS settleCycle FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' AND u.status = '#1#' " +
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.user_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.user_no</script>")
    List<WaterLedgerUserVO> selectLedgerUserOptions(@Param("keyword") String keyword);
}
