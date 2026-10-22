package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 巡检记录现场照片项（上传返回 / 详情回显）。
 * <p>照片落自建表 t_auto_hltgq_water_patrol_record_photo；url 为站点内相对路径 /patrol-record/photo/{id}。
 */
@Data
public class PatrolRecordPhotoItemVO {

    /** 照片 id */
    private String id;

    /** 原始文件名 */
    private String name;

    /** 文件大小（字节） */
    private Long size;

    /** 扩展名（小写） */
    private String ext;

    /** 展示顺序（1 起；上传时为空） */
    private Integer natureOrder;

    /** 预览地址（站点内相对路径） */
    private String url;
}
