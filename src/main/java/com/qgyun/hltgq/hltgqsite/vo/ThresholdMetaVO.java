package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 阈值设置字典视图：阈值类型 + 告警方向，由后端统一下发，前端不写死。
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

        /** 类型编码：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 开度 / #7# 墒情 */
        private String code;

        /** 类型名称（与站点档案监测类型字典一致） */
        private String name;

        /** 警戒值单位 */
        private String unit;

        /** 默认告警方向编码 */
        private String defaultAlarmDir;

        /** 适用站点说明（界面提示用） */
        private String siteScopeDesc;
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
