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
 * <p><b>关系中间表（表名已实证）</b>：巡查范围 / 巡检人员为多选多对多，主表无列（也无 _d_ 动态列，
 * 已由 INSERT 样例确认）。表名取平台元数据实证：t_lcode_model 中巡检计划模型 fun_code=nlbdju
 * （应用 ZzrirqCf7oZ4cudrUOY = 表前缀 knc3g），两个关系模型 fun_code=nlbdjuTkwovkRel / nlbdjuUserRel，
 * 与库中 _rel 表清单逐一对应：
 * <ul>
 *   <li>巡查范围 → {@code t_auto_hltgq_knc3g_nlbdju_tkwovk_rel}</li>
 *   <li>巡检人员 → {@code t_auto_hltgq_knc3g_nlbdju_user_rel}</li>
 * </ul>
 * 命名规律：t_auto_&lt;corp&gt;_&lt;appCode&gt;_&lt;主表code&gt;_&lt;关系模型fun_code去主表前缀与Rel后缀，驼峰转下划线小写&gt;_rel
 * （同型对照：巡检照片 t_auto_hltgq_knc3g_ychwbx_site_image_rel、值班人员 t_auto_hltgq_yn8cm_igahxz_ahygpx_rel）。
 * 列结构已按库中实际核对（2026-09-13 实测两表均为 9 列，与同型专表一致）：id / corp_code / 审计四列 /
 * biz_id（本表记录id）/ rel_id（关联记录id）/ nature_order（顺序号）；无 field_id 列。
 * 平台自建数据经表单写入，本模块为直写。
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
     * 巡查范围关系写入（多选多对多）：biz_id = 计划 id，rel_id = 站点 id，nature_order = 顺序号（1 起）。
     * <p>列取同型专表实测形态（巡检照片关系表真实 INSERT 样例为 9 列：
     * id/corp_code/created_at/created_by/updated_at/updated_by/biz_id/rel_id/nature_order）。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", \"biz_id\", \"rel_id\", \"nature_order\") " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{userId}, #{now}, #{userId}, #{bizId}, #{relId}, #{natureOrder})")
    int insertSiteRelation(@Param("id") String id,
                           @Param("relId") String relId,
                           @Param("bizId") String bizId,
                           @Param("natureOrder") String natureOrder,
                           @Param("corpCode") String corpCode,
                           @Param("now") LocalDateTime now,
                           @Param("userId") String userId);

    /**
     * 巡检人员关系写入（多选多对多）：biz_id = 计划 id，rel_id = 用户 id，nature_order = 顺序号（1 起）。
     * <p>列结构同巡查范围关系表（同型专表实测 9 列）。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_user_rel\" " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", \"biz_id\", \"rel_id\", \"nature_order\") " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{userId}, #{now}, #{userId}, #{bizId}, #{relId}, #{natureOrder})")
    int insertUserRelation(@Param("id") String id,
                           @Param("relId") String relId,
                           @Param("bizId") String bizId,
                           @Param("natureOrder") String natureOrder,
                           @Param("corpCode") String corpCode,
                           @Param("now") LocalDateTime now,
                           @Param("userId") String userId);
}
