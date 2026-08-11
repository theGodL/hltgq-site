package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 登录鉴权请求 VO
 */
@Data
public class AuthRequestVO {

    /** 32 位鉴权 key */
    private String key;

    /** 32 位鉴权 secret */
    private String secret;
}
