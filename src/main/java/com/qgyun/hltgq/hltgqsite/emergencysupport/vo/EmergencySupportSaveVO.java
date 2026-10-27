package com.qgyun.hltgq.hltgqsite.emergencysupport.vo;

import lombok.Data;

import java.util.List;

/**
 * 应急保障新增/编辑提交体（POST /emergency-support/save、/update）。
 * <p>编号由后端生成（YJBZ-年月日-当日序号），前端不传、不可改；
 * 编辑不修改编号，仅更新业务字段与附件归属。
 */
@Data
public class EmergencySupportSaveVO {

    /** 编辑时必填；新增留空 */
    private String id;

    /** 保障类别（可传中文或编码，如 物资装备 / #nkzk#） */
    private String category;

    /** 保障措施/资源名称（物资装备类别由台账带出，其余自由填写） */
    private String name;

    /** 保障内容 */
    private String content;

    /** 数量/能力（物资装备类别自动带出「数量+单位」，可改） */
    private String capacity;

    /** 责任单位/负责人 */
    private String owner;

    /** 适用场景 */
    private String scene;

    /** 当前状态（可用 / 停用） */
    private String status;

    /** 最近检查/有效期（yyyy-MM-dd，可空） */
    private String checkDate;

    /** 响应使用方式 */
    private String usage;

    /** 附件 ID 列表（上传接口返回的 id；编辑时传保留集合，未传清空） */
    private List<String> attachmentIds;
}
