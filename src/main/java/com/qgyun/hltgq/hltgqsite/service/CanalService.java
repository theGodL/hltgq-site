package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.CanalSortGroupVO;
import com.qgyun.hltgq.hltgqsite.vo.CanalTreeNodeVO;

import java.util.List;

/**
 * 渠系服务：渠系树收集（供各监测接口按渠系过滤站点）+ 渠系排序（渠系排序页面）
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

    /**
     * 渠系树：同级按平台展示顺序（created_at 倒序）排列，供「渠系排序」页面生成分组清单。
     * <p>上级渠系不存在的脏数据挂到顶级，避免整条记录在页面上丢失。
     */
    List<CanalTreeNodeVO> tree();

    /**
     * 保存渠系排序（事务）：按分组提交同级渠系的展示顺序。
     * <p>平台层级树接口同级按 created_at 倒序展示，因此保存即把该组现存创建时间从大到小
     * 依次分配给目标顺序（时间值集合不变，只换渠系，不引入新时间；同毫秒重复值后移 1ms 保证严格递减）。
     * <p>组内未提交的渠系按当前展示顺序追加在后；不属于该组的 id 忽略。
     *
     * @return 实际改写的记录数（顺序未变化的组不落库）
     */
    int saveOrder(List<CanalSortGroupVO> groups);
}
