package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逐日观测值（雨量按水文日聚合的结果行）。
 * <p>d 为水文日标签的 00:00:00 时刻：KingbaseES 的 ::date 返回自定义类型（oid 7944），
 * JDBC 驱动无法转换为 LocalDate，故 SQL 用 date_trunc('day', ...) 返回 timestamp，
 * 以项目已注册 TypeHandler 的 LocalDateTime 承接，业务层 toLocalDate() 使用。
 */
@Data
public class ObsDailyVO {

    /** 水文日（自然日标签，00:00:00 时刻） */
    private LocalDateTime d;

    /** 数值 */
    private Double value;
}
