package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

import java.util.List;

/**
 * 填写工单处理结果请求（/work-order/result，仅处理中 #2# 可填写）。
 * <p>填写结果不改状态，并可重复保存；处理时间 pyxcen 取保存时刻。
 * <p>附件：attachmentIds 为 null 表示不调整（兼容不带附件的旧调用）；
 * 非 null 时以集合为准——新上传的绑定本工单，已绑定本工单但不在集合内的行与文件一并清理。
 */
@Data
public class WorkOrderResultRequest {

    /** 工单 id（必填） */
    private String id;

    /** 处理结果（必填，≤1000 字） */
    private String result;

    /** 处理结果附件 id 集合（可选；null = 不调整） */
    private List<String> attachmentIds;
}
