package com.qgyun.hltgq.hltgqsite.inventorystocktake.vo;

import lombok.Data;

import java.util.List;

/**
 * 编辑盘点单提交体（POST /inventory-stocktake/update）。
 * <p>编辑仅限盘点人/备注/附件（方案 1.1、4.3）：不触碰台账、明细与流水；
 * attachmentIds 为编辑后最终保留的附件 ID 列表（未在列表中的本单附件将被物理移除）。
 */
@Data
public class StocktakeUpdateVO {

    /** 盘点单 ID（必填） */
    private String id;

    /** 盘点人 ID（可改，姓名快照同步更新；历史流水经手人不变） */
    private String operatorId;

    /** 备注（≤50 字） */
    private String remark;

    /** 编辑后保留的附件 ID 列表（可空 = 全部移除） */
    private List<String> attachmentIds;
}
