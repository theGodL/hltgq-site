package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 巡检记录照片行（内部：预览 / 归属校验 / 移除 / 编辑保留判断）。
 */
@Data
public class PatrolRecordPhotoFileVO {

    /** 照片 id */
    private String id;

    /** 绑定记录 id（上传时为空，保存记录时回填） */
    private String recordId;

    /** 原始文件名 */
    private String fileName;

    /** 相对 file-dir 的存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 扩展名（小写） */
    private String fileExt;

    /** 展示顺序（1 起） */
    private Integer natureOrder;

    /** 上传人用户 id */
    private String createdBy;
}
