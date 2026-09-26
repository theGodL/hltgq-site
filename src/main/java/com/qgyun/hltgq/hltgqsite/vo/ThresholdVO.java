package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 阈值设置列表/详情视图（新增、编辑保存后同样返回本结构）。
 * <p>阈值类型、监测指标与告警方向除编码外一并给出中文名与单位，前端不写死字典。
 */
@Data
public class ThresholdVO {

    /** 阈值行主键 */
    private String id;

    /** 站点档案主键（编辑、删除用） */
    private String site;

    /** 站点名称（站点档案 zzkaec，档案缺失时回退站点主键） */
    private String siteName;

    /** 站点编号（站点档案 iofhpi） */
    private String siteCode;

    /** 阈值类型编码：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 开度 / #7# 墒情 / #8# 水质 */
    private String thresholdType;

    /** 阈值类型名称（水位 / 雨量 / ...） */
    private String typeName;

    /** 监测指标编码（多指标类型才有值，如 nh3n / mten；单指标类型为空） */
    private String indicator;

    /** 监测指标名称（氨氮 / 10cm含水量 …；单指标类型或字典外编码为空） */
    private String indicatorName;

    /** 警戒值单位（多指标类型按指标单位，其余按类型单位） */
    private String unit;

    /** 告警方向编码：#1# 高于警戒值触发 / #2# 低于警戒值触发 */
    private String alarmDir;

    /** 告警方向名称 */
    private String alarmDirName;

    /** 告警方向是否为类型默认值（存量行未落方向时后端按类型补默认值，界面加「默认」标记） */
    private Boolean alarmDirDefault;

    /** 警戒值 */
    private BigDecimal threshold;

    /** 描述 */
    private String description;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
