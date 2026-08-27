package com.qgyun.hltgq.hltgqsite.model.util;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 短ID生成器：22位随机字符串（大小写字母+数字，如 aGbc28z7gswNbYi8Yfu）。
 * <p>注册为 MyBatis-Plus 全局 {@link IdentifierGenerator}，
 * {@code IdType.ASSIGN_UUID} 的 String 主键 insert 时自动调用 {@link #nextUUID(Object)}，
 * 无需在各 Service 手动 setId。数字类型主键不走本生成器（返回 null）。
 * <p>规范来源：数据库字段来源与计算逻辑说明 v1.15——
 * “主键及外键统一使用 varchar 短ID（22位随机字符串）”。
 */
@Component
public class ShortIdGenerator implements IdentifierGenerator {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 22;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Number nextId(Object entity) {
        // 模型接入表主键均为 String 类型，数字主键场景返回 null 走默认雪花
        return null;
    }

    @Override
    public String nextUUID(Object entity) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
