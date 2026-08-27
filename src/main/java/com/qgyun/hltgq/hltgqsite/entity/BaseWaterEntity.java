package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模型接入表实体基类：平台标准字段（短ID + 审计字段）。
 */
@Getter
@Setter
public class BaseWaterEntity {

    @TableId(value = "\"id\"", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("\"corp_code\"")
    private String corpCode;

    @TableField("\"created_at\"")
    private LocalDateTime createdAt;

    @TableField("\"created_by\"")
    private String createdBy;

    @TableField("\"updated_at\"")
    private LocalDateTime updatedAt;

    @TableField("\"updated_by\"")
    private String updatedBy;
}
