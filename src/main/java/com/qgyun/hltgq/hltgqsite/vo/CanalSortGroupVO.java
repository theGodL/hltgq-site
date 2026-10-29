package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 渠系排序分组（一个上级渠系下的同级子渠系顺序；parentId 为 null 表示顶级渠系组）
 */
@Data
public class CanalSortGroupVO {

    /** 上级渠系 id（null = 顶级渠系） */
    private String parentId;

    /** 该上级下渠系 id 列表（按保存后的展示顺序，从上到下） */
    private List<String> canalIds;
}
