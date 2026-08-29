package com.qgyun.hltgq.hltgqsite.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 三维系统单点登录 token 返回体
 */
@Data
@AllArgsConstructor
public class ThreeDimensionalTokenVO {

    /** 三维系统 token（JWT） */
    private String token;
}
