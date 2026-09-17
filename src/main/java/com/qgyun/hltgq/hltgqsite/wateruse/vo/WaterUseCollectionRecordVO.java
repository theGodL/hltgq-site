package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 征收/收缴统计 · 水费表单原始记录（内部使用，不出 JSON）。
 * <p>来源表单：t_auto_hltgq_yn8cm_gfiidm（水费计算）；区域经用水户外键 xqaoxx 关联
 * 用水户表 t_auto_hltgq_yn8cm_kooivg 取名称 iiatzj（如望江县）。
 * <p>各数值直取表单现成值、不做推算；金额单位为表单实际录入口径（2026-09-17 线上数据复核）：
 * 应收/已收水费 元（表单标签即「应收水费（元）」，出参由服务层 ÷10^4 换算为万元）。
 */
@Data
public class WaterUseCollectionRecordVO {

    /** 水费编号 ztmhwx（日志核对用） */
    private String feeNo;

    /** 区域（用水户名称 iiatzj，如望江县）；用水户缺失或名称为空时为 null */
    private String regionName;

    /** 归桶锚点 = 统计周期区间终点 xxmefs_max（缺失回退起点 xxmefs_min；两者皆空不参与统计） */
    private LocalDateTime anchorTime;

    /** 应收水费 hsfvdh（原始值，单位 元；出参由服务层 ÷10^4 换算为万元） */
    private BigDecimal receivableRaw;

    /** 已收水费 vdhlhm（原始值，单位 元；未填写为 null；出参由服务层 ÷10^4 换算为万元） */
    private BigDecimal receivedRaw;
}
