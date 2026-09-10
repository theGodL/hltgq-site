package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.util.List;

/**
 * 站点详情-筛选下拉选项（一次请求返回三个下拉的数据源，供页面初始化）。
 */
@Data
public class StationOptionsVO {

    /** 巡检人员姓名去重列表（该站点巡检记录关联用户，升序） */
    private List<String> patrolPersons;

    /** 问题发现人姓名去重列表（该站点问题记录关联用户，升序） */
    private List<String> issueFinders;

    /** 设备名称去重列表（该站点设备表，升序） */
    private List<String> deviceNames;
}
