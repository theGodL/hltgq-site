package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡查考核记录（自建表 t_auto_hltgq_water_patrol_assess）。
 *
 * <p>一计划一条（schedule_id 唯一）：提交时按计划下巡检记录与问题重算自动项并落库形成<b>快照</b>，
 * 「查看考核」读快照——考核之后问题再流转不会改写已考核数据。
 * <p>口径：作废问题（#5#）不计入任何一项；恒等式 发现问题 = 已整改 + 整改中 + 未整改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_patrol_assess\"")
public class PatrolAssess extends BaseWaterEntity {

    /** 巡检计划 ID（patrol_schedule.id，唯一） */
    @TableField("\"schedule_id\"")
    private String scheduleId;

    /** 计划编号快照 */
    @TableField("\"plan_code\"")
    private String planCode;

    /** 计划名称快照 */
    @TableField("\"plan_title\"")
    private String planTitle;

    /** 计划巡查站点（个） */
    @TableField("\"plan_site\"")
    private Integer planSite;

    /** 已巡查站点（个） */
    @TableField("\"patrolled_site\"")
    private Integer patrolledSite;

    /** 巡查记录（条） */
    @TableField("\"record_count\"")
    private Integer recordCount;

    /** 发现问题（项） */
    @TableField("\"issue_total\"")
    private Integer issueTotal;

    /** 已整改（项）：问题 #4# 已关闭 */
    @TableField("\"issue_fixed\"")
    private Integer issueFixed;

    /** 整改中（项）：问题 #2# 处理中 + #3# 已转工单 */
    @TableField("\"issue_fixing\"")
    private Integer issueFixing;

    /** 未整改（项）：问题 #1# 待处理 */
    @TableField("\"issue_open\"")
    private Integer issueOpen;

    /** 考核得分（0~100，1 位小数，手工填写） */
    @TableField("\"score\"")
    private Double score;

    /** 考核等级编码：#1# 不及格 / #2# 及格 / #3# 良好 / #4# 优秀 */
    @TableField("\"grade\"")
    private String grade;

    /** 考核结论（手工填写，可空） */
    @TableField("\"conclusion\"")
    private String conclusion;

    /** 考核人用户 id（提交时登录人，自动留痕） */
    @TableField("\"assessor_id\"")
    private String assessorId;

    /** 考核人姓名快照 */
    @TableField("\"assessor_name\"")
    private String assessorName;
}
