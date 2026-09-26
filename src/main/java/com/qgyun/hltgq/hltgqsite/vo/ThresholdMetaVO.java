package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 阈值设置字典视图：阈值类型（含各类型的监测指标清单）+ 告警方向，由后端统一下发，前端不写死。
 * <p>类型与站点的对应关系由站点档案维护（档案监测类型 epjutj 含该类型编码即支持），
 * 界面文案 {@link TypeItem#getSiteScopeDesc()} 仅作说明。
 */
@Data
public class ThresholdMetaVO {

    /** 阈值类型清单（顺序即界面下拉顺序） */
    private List<TypeItem> types;

    /** 告警方向清单 */
    private List<OptionItem> alarmDirs;

    /** 阈值类型项 */
    @Data
    public static class TypeItem {

        /** 类型编码：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 开度 / #7# 墒情 / #8# 水质 */
        private String code;

        /** 类型名称（与站点档案监测类型字典一致） */
        private String name;

        /** 警戒值单位（单指标类型即本类型单位；多指标类型为默认值，实际按指标单位） */
        private String unit;

        /** 默认告警方向编码 */
        private String defaultAlarmDir;

        /** 适用站点说明（界面提示用） */
        private String siteScopeDesc;

        /** 监测指标清单（仅多指标类型：#8# 水质、#7# 墒情；单指标类型为空） */
        private List<IndicatorItem> indicators;
    }

    /** 监测指标项（多指标类型下可配置的指标，编码即监测数据表字段名，告警比对接该列取数） */
    @Data
    public static class IndicatorItem {

        /** 指标编码（= 监测数据表字段名，如 nh3n / mten） */
        private String code;

        /** 指标名称（氨氮 / 10cm含水量 …） */
        private String name;

        /** 指标单位（mg/L / % / ℃） */
        private String unit;

        /** 默认告警方向编码（按指标语义：溶解氧与土壤含水量为「低于」，其余「高于」） */
        private String defaultAlarmDir;
    }

    /** 编码项（告警方向等枚举共用） */
    @Data
    public static class OptionItem {

        /** 编码 */
        private String code;

        /** 中文名 */
        private String name;
    }
}
