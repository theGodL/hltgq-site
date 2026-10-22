package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 通用下拉选项（站点 / 设备 / 用户共用）。
 */
@Data
public class PatrolOptionVO {

    /** 提交值（站点 id / 设备 id / 用户 id） */
    private String value;

    /** 显示名（站点名称 / 设备名称 / 用户姓名） */
    private String label;

    /** 右侧辅助文字（站点编号 / 设备编号 / 用户登录名） */
    private String sub;
}
