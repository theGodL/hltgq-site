package com.qgyun.hltgq.hltgqsite.model.util;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 短ID生成器：22位随机字符串（大小写字母+数字，如 aGbc28z7gswNbYi8Yfu）。
 * <p>需在 SqlSessionFactory 的 GlobalConfig 中显式注入
 * （{@code globalConfig.setIdentifierGenerator(...)}）：自定义 factory 不走
 * Spring Boot 自动配置，仅声明 {@link Component} 不会被 MyBatis-Plus 拾取。
 * {@code IdType.ASSIGN_UUID} 的 String 主键 insert 时自动调用 {@link #nextUUID(Object)}。
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
        // 数字类型主键保持默认雪花 ID，避免注入后影响非 String 主键实体
        return IdWorker.getId();
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
