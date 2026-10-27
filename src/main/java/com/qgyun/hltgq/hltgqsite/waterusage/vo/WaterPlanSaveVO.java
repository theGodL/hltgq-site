package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用水计划新增/编辑提交体（POST/PUT /water-plan）。
 * <p>计划周期由 planType + year(+month+xun) 服务端推导（与前端纯函数同公式），不接受前端直接传日期；
 * 计划水量按 m³ 提交（前端以万m³ 录入、×10000 换算后提交，2 位小数）。
 * <p>编制中编辑 = 全量替换明细；已生效编辑 = 仅传 id + planWater + overrideReason（面积/定额/用户锁定），
 * 备注在已生效计划中可单独修改。
 */
@Data
public class WaterPlanSaveVO {

    /** 计划名称（必填，≤200 字，全局唯一；不再按周期自动生成） */
    private String planName;

    /** 类型：#1# 年度 / #2# 月度 / #3# 旬度 */
    private String planType;

    /** 年度（如 2026） */
    private Integer year;

    /** 月度（1-12；年度类型忽略） */
    private Integer month;

    /** 旬（1 上旬 / 2 中旬 / 3 下旬；仅旬度类型使用） */
    private Integer xun;

    /** 备注（可空，≤200 字；已生效计划也可修改） */
    private String remark;

    /** 明细（至少 1 行；同一用水户仅可出现一次） */
    private List<Item> items;

    /**
     * 附件 id 集合（上传接口返回的 id）：null = 不调整附件（已生效受限编辑可省略）；
     * 非 null = 以本集合为准（新增绑定 + 移除未保留附件）。
     */
    private List<String> attachmentIds;

    /** 明细行 */
    @Data
    public static class Item {

        /** 明细 ID（已生效编辑时必传，用于锁定行集合） */
        private String id;

        /** 用水户 ID（water_user.id；同一计划中仅可出现一次） */
        private String waterUserId;

        /** 灌溉面积（亩，2 位小数；选用水户带出可改） */
        private BigDecimal irrigationArea;

        /** 适用定额 ID（water_quota.id） */
        private String quotaId;

        /** 计划水量（m³，>0，2 位小数） */
        private BigDecimal planWater;

        /** 超测算说明（计划水量 > 测算水量 时必填，≤200 字） */
        private String overrideReason;

        /** 阶段标签（可空，≤50 字） */
        private String stageTag;
    }
}
