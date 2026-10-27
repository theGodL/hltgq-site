package com.qgyun.hltgq.hltgqsite.emergencysupport.vo;

import lombok.Data;

/**
 * 应急保障「物资装备」联动候选（来源＝应急物资台账行，只含类别为应急物资的物资）。
 * <p>选择后前端回填：保障措施/资源名称 = name，数量/能力 = quantity + unit（如「5000 条」）。
 * <p>同一物资在不同仓库各有台账行，按「物资 + 仓库」逐行返回，保证带出数量与所选仓库一致。
 */
@Data
public class EmergencyMaterialOptionVO {

    /** 台账行 ID（tzkioe.id） */
    private String ledgerId;

    /** 物资 ID（slaifi.id） */
    private String materialId;

    /** 物资编号（slaifi.higsdd） */
    private String code;

    /** 物资名称（台账快照） */
    private String name;

    /** 规格 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 可用数量（台账 ejgxlq） */
    private Integer quantity;

    /** 仓库 ID */
    private String warehouseId;

    /** 仓库名称 */
    private String warehouseName;
}
