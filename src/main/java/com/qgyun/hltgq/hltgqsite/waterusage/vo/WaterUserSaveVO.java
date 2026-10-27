package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水户新增/编辑提交体（POST/PUT /water-user）。
 * <p>编号由服务端自动生成、不可填写（新增取 YH+3 位序号最大值 +1，编辑保持原编号）；
 * 联系人从平台用户下拉选择——传 contactUserId 由后端回查固化姓名/手机号快照（不信任前端传入），
 * 编辑未换人（联系人快照改名/离职等场景）可不传 contactUserId 而回传原 contactName/contactPhone 保持快照；
 * 闸口结构由后端校验并以站档名称固化快照。
 */
@Data
public class WaterUserSaveVO {

    /** 名称（必填，≤100 字，全局唯一） */
    private String name;

    /** 用水类型：#1# 农业 / #2# 工业 / #3# 生活 / #4# 生态 */
    private String userType;

    /** 状态：#1# 正常 / #2# 停用（不传默认 #1# 正常） */
    private String status;

    /** 联系人用户 id（平台用户；新增必传——后端按 id 回查固化姓名与手机号） */
    private String contactUserId;

    /** 联系人姓名（快照回传；仅编辑未换人时生效，其余场景被后端回查值覆盖） */
    private String contactName;

    /** 联系电话（快照回传；仅编辑未换人时生效，其余场景由后端按联系人回查手机号覆盖） */
    private String contactPhone;

    /** 灌溉面积（亩，2 位小数；农业/生态必填） */
    private BigDecimal irrigationArea;

    /** 位置经度（可选，-180 ~ 180，≤6 位小数；灌溉保证率点位地图） */
    private BigDecimal lng;

    /** 位置纬度（可选，-90 ~ 90，≤6 位小数） */
    private BigDecimal lat;

    /** 年度计划水量（万m³ 录入，≤2 位小数；存储 m³。可空 = 未设定；0 视为未设定） */
    private BigDecimal annualPlanWater;

    /** 水价（元/m³，>0，3 位小数） */
    private BigDecimal waterPrice;

    /** 计算策略：#1# 指定闸口累加 / #2# 闸口差额计量 */
    private String calcMode;

    /**
     * 关联闸口：
     * 累加 {"mode":"SUM","gates":[{"id":"…","name":"…"},…]}
     * 差额 {"mode":"DIFF","mains":[{…},…],"deduct":[{…},…]}（主闸口可多选；兼容旧版 main 单对象）
     * （name 不信任前端，后端按 id 回查站档固化）
     */
    private JsonNode gateConfig;

    /** 统计周期：#1# 月度结算 / #2# 季度结算 / #3# 年度结算 */
    private String settleCycle;

    /** 备注（可空，≤200 字） */
    private String remark;
}
