package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 用水计划附件（详情附件区 / 编辑附件管理共用）。
 * <p>上传成功即返回（此时 planId 为空），保存计划时回填归属；
 * filePath 仅在下载/删除等内部路径查询时填充（对外清单查询不含该列，字段为 null）。
 */
@Data
public class WaterPlanAttachmentVO {

    private String id;

    /** 所属用水计划 ID（上传时为空，保存/编辑回填） */
    private String planId;

    /** 原始文件名（下载展示用） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 扩展名（小写，下载 Content-Type 判别） */
    private String fileExt;

    /** 相对存储路径（仅内部下载/删除查询填充） */
    private String filePath;
}
