package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 开始值班（签到）请求（POST /duty/attendance/start；契约见《值班交接班接口.md》§4.2）。
 * <p>按排班维度签到：每次签到为登录人生成一条自己的值班记录（值班记录 = 一个人的值班记录），
 * 同一排班内其余人各自签到、互不影响；重复点击幂等复用本人已有在途记录。
 */
@Data
public class AttendanceStartRequest {

    /** 排班 id（签到数据源：复制班次时间/单位/领导，归属人 = 登录人） */
    private String scheduleId;
}
