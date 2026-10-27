package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用水计划（列表行 / 详情共用）。
 * <p>userCount（用水户数，明细 DISTINCT 用水户）由列表 / 详情同一表达式计算；
 * doneQty / missingCnt 为服务层按未取消调度方案的闸口实测计量装配（{@code WaterUsageMeter}）：
 * 灌溉进度 = doneQty ÷ totalWater（前端动态计算）；缺读数时不能认定为零供水（前端展示「待补计量数据」）。
 */
@Data
public class WaterPlanVO {

    private String id;

    /** 编号：YJ + 3 位序号 */
    private String planNo;

    /** 计划名称（必填，全局唯一） */
    private String planName;

    /** 类型：#1# 年度 / #2# 月度 / #3# 旬度 */
    private String planType;

    private String planTypeName;

    /** 计划周期起（yyyy-MM-dd，按类型自动推导不可改） */
    private String periodStart;

    /** 计划周期止 */
    private String periodEnd;

    /** 制定时间（yyyy-MM-dd HH:mm，列表名称辅助行） */
    private String createdAtText;

    /** 计划总水量（m³，明细汇总，只读） */
    private BigDecimal totalWater;

    /** 已调度水量（m³，默认 0；调度模块后续回写） */
    private BigDecimal scheduledQty;

    /**
     * 已完成供水量（m³，实测）：关联本计划且未取消的调度方案实际放水量之和
     * （逐方案按方案闸口区间累计计量、差额扣减取负；户合计为负的户按待补不计入）。
     */
    private BigDecimal doneQty;

    /** 缺读数计数：计量不可得的方案数 + 合计异常（为负）的户数；&gt; 0 时前端展示「待补计量数据」 */
    private Integer missingCnt;

    /** 用水户数（明细 DISTINCT 用水户） */
    private Integer userCount;

    /** 状态：#1# 编制中 / #2# 已生效 / #3# 已归档 / #4# 已作废 */
    private String status;

    private String statusName;

    /** 备注（可空；已生效计划受限编辑中仍可修改） */
    private String remark;

    /** 作废人（平台用户 id，审计追溯用；展示请读 {@link #voidByName}） */
    private String voidBy;

    /** 作废人姓名（关联平台用户表解析；id 解析不到（已删用户 / 会话兜底账号）时为 null，前端退回展示 voidBy） */
    private String voidByName;

    /** 作废时间（yyyy-MM-dd HH:mm） */
    private String voidAt;

    /** 作废原因 */
    private String voidReason;

    /** 明细（详情返回；列表为空） */
    private List<WaterPlanItemVO> items;

    /** 附件清单（详情返回；列表为空） */
    private List<WaterPlanAttachmentVO> attachments;
}
