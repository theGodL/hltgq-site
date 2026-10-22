package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 现场照片上传响应（POST /patrol-issue/photo）。
 * <p>url 为站点内相对路径（不含网关前缀），前端以 {@code API + url} 作为 &lt;img&gt; 地址，
 * H5 端取图同样复用 GET /patrol-issue/photo/{id}。
 */
@Data
public class PatrolIssuePhotoVO {

    /** 照片 id（提交问题时放入 photoIds） */
    private String id;

    /** 原始文件名 */
    private String name;

    /** 文件大小（字节） */
    private long size;

    /** 扩展名（小写，不含点） */
    private String ext;

    /** 预览地址（/patrol-issue/photo/{id}） */
    private String url;
}
