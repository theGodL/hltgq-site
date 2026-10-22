package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 填写工单处理结果请求（/work-order/result，仅处理中 #2# 可填写）。
 * <p>填写结果不改状态，并可重复保存；处理时间 pyxcen 取保存时刻。
 */
@Data
public class WorkOrderResultRequest {

    /** 工单 id（必填） */
    private String id;

    /** 处理结果（必填，≤1000 字） */
    private String result;
}
