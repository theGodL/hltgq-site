package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 工单处理结果附件（填写处理结果弹窗附件区 / 详情附件展示共用）。
 * <p>上传成功即返回（此时 orderId 为空），保存处理结果时按 id 集合回填归属；
 * filePath 仅在下载/删除等内部路径查询时填充（对外清单查询不含该列，字段为 null）。
 */
@Data
public class WorkOrderAttachmentVO {

    private String id;

    /** 所属工单 ID（上传时为空，保存处理结果时回填） */
    private String orderId;

    /** 原始文件名（下载展示用） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 扩展名（小写，下载 Content-Type 判别） */
    private String fileExt;

    /** 相对存储路径（仅内部下载/删除查询填充） */
    private String filePath;
}
