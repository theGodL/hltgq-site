package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.util.List;

/**
 * H5 巡检计划分页结果（/h5/patrol-schedule/list）：
 * total/current/size/pages 与消息分页结构一致，records 为巡检计划行。
 */
@Data
public class H5PatrolSchedulePageVO {

    /** 符合条件的计划总数 */
    private long total;

    /** 当前页码（生效值） */
    private long current;

    /** 每页条数（生效值，上限 100） */
    private long size;

    /** 总页数 */
    private long pages;

    /** 当前页计划行 */
    private List<H5PatrolScheduleVO> records;
}
