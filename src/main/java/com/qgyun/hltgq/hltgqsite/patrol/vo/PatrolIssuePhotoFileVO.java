package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 现场照片内部读取行（服务端下载 / 绑定 / 移除用，不直接作为接口响应）。
 */
@Data
public class PatrolIssuePhotoFileVO {

    /** 照片 id */
    private String id;

    /** 已绑定的问题 id（上传后未提交时为空） */
    private String issueId;

    /** 原始文件名 */
    private String fileName;

    /** 相对 file-dir 的存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 扩展名（小写，不含点） */
    private String fileExt;

    /** 上传人用户 id（移除/绑定时校验归属） */
    private String createdBy;
}
