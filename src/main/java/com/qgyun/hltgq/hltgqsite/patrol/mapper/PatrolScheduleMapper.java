package com.qgyun.hltgq.hltgqsite.patrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.PatrolSchedule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 巡查计划导入 Mapper（t_auto_hltgq_water_patrol_schedule + 多选关系中间表）。
 * <p>主表新增走 MyBatis-Plus 内建（insert，主键 ASSIGN_UUID 短ID）；
 * 其余为手写 SQL：编号查重、站点/用户按名称（或站点编号）精确匹配、关系中间表插入。
 *
 * <p><b>关系中间表（联调核对项）</b>：巡查范围 / 巡检人员为多选多对多，主表无列（也无 _d_ 动态列，
 * 已由 INSERT 样例确认），按平台关系表命名约定推导：
 * <ul>
 *   <li>巡查范围 → {@code t_auto_hltgq_water_patrol_schedule_inspection_scope_site_rel}</li>
 *   <li>巡检人员 → {@code t_auto_hltgq_water_patrol_schedule_user_rel}</li>
 * </ul>
 * 列结构按平台通用形态（与既有关系表 cols=9 吻合）：id / rel_id（关联记录id）/ biz_id（本表记录id）/
 * field_id（所属多选字段key）/ corp_code / 审计四列；field_id 取字段 key（inspection_scope_site / user）。
 * 平台自建数据经表单写入，本模块为直写；若表名或列语义与库中实际不一致，部署联调时按服务日志
 * （[巡查导入] 关系表写入）核对修正——只需改本类中对应 @Insert。
 */
@Mapper
public interface PatrolScheduleMapper extends BaseMapper<PatrolSchedule> {

    /**
     * 计划编号是否已存在（corp_code 限定 hltgq）。
     * <p>导入时用于编号查重（填写编号须全局唯一；自动生成编号防毫秒级碰撞）。
     */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE \"code\" = #{code} AND \"corp_code\" = 'hltgq'")
    long countByCode(@Param("code") String code);

    /**
     * 站点匹配（巡查范围逐项）：按站点名称或站点编号精确匹配，返回全部命中行。
     * <p>0 行 = 未匹配；1 行 = 命中；多行（重名 / 名称与编号交叉命中）= 歧义项，调用方忽略并提示。
     */
    @Select("SELECT s.id, s.zzkaec AS name, s.iofhpi AS code " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "WHERE s.corp_code = 'hltgq' AND (s.zzkaec = #{key} OR s.iofhpi = #{key})")
    List<Map<String, Object>> selectSiteMatches(@Param("key") String key);

    /**
     * 用户匹配（巡检人员 / 创建人）：按姓名精确匹配，返回全部命中行。
     * <p>0 行 = 未匹配；1 行 = 命中；多行 = 重名，调用方忽略（多选项）或判行失败（创建人）。
     */
    @Select("SELECT id, name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE corp_code = 'hltgq' AND name = #{name}")
    List<Map<String, Object>> selectUserMatches(@Param("name") String name);

    /**
     * 巡查范围关系写入（多选多对多）：biz_id = 计划 id，rel_id = 站点 id。
     * <p>列取平台关系表通用 9 列（不含 nature_order 等可选列），顺序与展示顺序无关。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule_inspection_scope_site_rel\" " +
            "(\"id\", \"rel_id\", \"biz_id\", \"field_id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{id}, #{relId}, #{bizId}, #{fieldId}, #{corpCode}, #{now}, #{userId}, #{now}, #{userId})")
    int insertSiteRelation(@Param("id") String id,
                           @Param("relId") String relId,
                           @Param("bizId") String bizId,
                           @Param("fieldId") String fieldId,
                           @Param("corpCode") String corpCode,
                           @Param("now") LocalDateTime now,
                           @Param("userId") String userId);

    /**
     * 巡检人员关系写入（多选多对多）：biz_id = 计划 id，rel_id = 用户 id。
     * <p>列结构同巡查范围关系表。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule_user_rel\" " +
            "(\"id\", \"rel_id\", \"biz_id\", \"field_id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{id}, #{relId}, #{bizId}, #{fieldId}, #{corpCode}, #{now}, #{userId}, #{now}, #{userId})")
    int insertUserRelation(@Param("id") String id,
                           @Param("relId") String relId,
                           @Param("bizId") String bizId,
                           @Param("fieldId") String fieldId,
                           @Param("corpCode") String corpCode,
                           @Param("now") LocalDateTime now,
                           @Param("userId") String userId);
}
