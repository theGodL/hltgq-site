package com.qgyun.hltgq.hltgqsite.emergencysupport.vo;

import lombok.Data;

/**
 * 应急保障附件（详情附件区 / 编辑附件管理 / 上传回执共用）。
 * <p>上传成功后即返回（此时 supportId 为空），保存/编辑成功回填归属；
 * filePath 仅在下载/删除等内部路径查询时填充（对外查询不含该列，字段为 null）。
 */
@Data
public class EmergencyAttachmentVO {

    private String id;

    /** 所属应急保障记录 ID（上传时为空，保存/编辑回填） */
    private String supportId;

    /** 原始文件名（下载展示用） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 扩展名（小写，下载 Content-Type 判别） */
    private String fileExt;

    /** 相对 file-dir 的存储路径（内部使用，前端不需要） */
    private String filePath;
}
