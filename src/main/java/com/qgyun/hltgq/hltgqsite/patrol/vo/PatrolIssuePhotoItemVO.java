package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 问题现场照片列表行（GET /patrol-issue/photos?issueId=，供 H5 问题详情回显）。
 * <p>url 为站点内相对路径（不含网关前缀），前端以 {@code API + url} 作为 &lt;img&gt; 地址。
 */
@Data
public class PatrolIssuePhotoItemVO {

    /** 照片 id */
    private String id;

    /** 原始文件名 */
    private String name;

    /** 文件大小（字节） */
    private Long size;

    /** 扩展名（小写，不含点） */
    private String ext;

    /** 展示顺序（1 起，提交时的上传顺序） */
    private Integer natureOrder;

    /** 读取地址（/patrol-issue/photo/{id}，不含 /hltgq-site 前缀） */
    private String url;
}
