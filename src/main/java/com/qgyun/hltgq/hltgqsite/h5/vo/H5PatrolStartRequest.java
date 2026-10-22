package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

/**
 * H5 开始巡检请求（POST /h5/patrol-schedule/start）。
 * <p>巡检人不由前端传入：服务端强制取当前登录人，避免代他人开始。
 */
@Data
public class H5PatrolStartRequest {

    /** 巡检计划 id（必填） */
    private String scheduleId;
}
