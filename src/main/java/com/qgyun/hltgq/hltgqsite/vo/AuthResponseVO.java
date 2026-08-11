package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 登录鉴权响应 VO
 */
@Data
public class AuthResponseVO {

    /** 是否成功 */
    private boolean success;

    /** 返回消息 */
    private String message;

    /** 会话 ID（登录成功时返回） */
    private String sessionId;
}
