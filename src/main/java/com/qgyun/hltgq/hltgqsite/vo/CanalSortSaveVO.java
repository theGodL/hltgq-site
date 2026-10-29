package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 渠系排序保存入参：按分组提交同级渠系顺序（整页一次保存）
 */
@Data
public class CanalSortSaveVO {

    /** 排序分组清单（每组 = 一个上级渠系下的同级子渠系，按展示顺序从上到下） */
    private List<CanalSortGroupVO> groups;
}
