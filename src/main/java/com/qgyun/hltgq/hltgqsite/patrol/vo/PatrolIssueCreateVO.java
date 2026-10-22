package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 新增巡检问题响应：问题 id、自动生成的问题编号、绑定照片数。
 */
@Data
public class PatrolIssueCreateVO {

    /** 问题记录主键 */
    private String id;

    /** 问题编号（QST-毫秒时间戳，与存量数据同形态） */
    private String code;

    /** 已绑定的现场照片数 */
    private int photoCount;
}
