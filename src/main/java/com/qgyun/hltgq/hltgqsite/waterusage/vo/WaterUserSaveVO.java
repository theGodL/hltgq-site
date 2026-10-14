package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水户新增/编辑提交体（POST/PUT /water-user）。
 * <p>编号 user_no 由后端生成（YH+3 位序号）；闸口结构由后端校验并以站档名称固化快照。
 */
@Data
public class WaterUserSaveVO {

    /** 名称（必填，≤100 字，全局唯一） */
    private String name;

    /** 用水类型：#1# 农业 / #2# 工业 / #3# 生活 / #4# 生态 */
    private String userType;

    /** 联系人（必填，≤50 字） */
    private String contactName;

    /** 联系电话（可空；填写须 11 位数字） */
    private String contactPhone;

    /** 灌溉面积（亩，2 位小数；农业/生态必填） */
    private BigDecimal irrigationArea;

    /** 水价（元/m³，>0，3 位小数） */
    private BigDecimal waterPrice;

    /** 计算策略：#1# 指定闸口累加 / #2# 闸口差额计量 */
    private String calcMode;

    /**
     * 关联闸口：
     * 累加 {"mode":"SUM","gates":[{"id":"…","name":"…"},…]}
     * 差额 {"mode":"DIFF","main":{"id":"…","name":"…"},"deduct":[{…},…]}
     * （name 不信任前端，后端按 id 回查站档固化）
     */
    private JsonNode gateConfig;

    /** 统计周期：#1# 月度结算 / #2# 季度结算 / #3# 年度结算 */
    private String settleCycle;
}
