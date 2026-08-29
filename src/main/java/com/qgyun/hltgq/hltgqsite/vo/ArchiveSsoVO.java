package com.qgyun.hltgq.hltgqsite.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 档案系统单点登录凭证返回体
 */
@Data
@AllArgsConstructor
public class ArchiveSsoVO {

    /** 浩微档案系统 token（5 分钟有效，需立即跳转使用） */
    private String token;

    /** 浩微档案系统 userId（人员同步接口返回，已回写 name_spell） */
    private String userId;
}
