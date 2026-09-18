package com.qgyun.hltgq.hltgqsite.service;

import java.util.List;

/**
 * 渠系服务：渠系树收集（供各监测接口按渠系过滤站点）
 */
public interface CanalService {

    /**
     * 按渠系树收集过滤范围：canalId 非空时返回该渠系及其所有子孙渠系 id（含自身，BFS 遍历）。
     * <p>渠系管理表 t_auto_hltgq_knc3g_egvnhw 数据量小（全量约几十行），内存构建树即可；
     * 站点表 ywvyds 存渠系管理表 id，各监测 SQL 以 ywvyds IN (...) 过滤。
     *
     * @param canalId 渠系 id（null/空 = 不过滤，返回 null）
     * @return 子孙渠系 id 集合（含自身，保序去重）；canalId 在渠系表中不存在时返回仅含自身的集合
     */
    List<String> collectDescendantCanalIds(String canalId);
}
