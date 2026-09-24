package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.ThresholdMetaVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdVO;

import java.util.List;
import java.util.Map;

/**
 * 阈值设置：对监测站点按「站点 + 阈值类型」维护单级警戒值。
 * <p>类型与站点的对应关系由站点档案维护（档案监测类型 epjutj 含该类型编码即支持），
 * 类型字典（编码/名称/单位/默认方向/适用站点说明）由后端统一提供，前端不写死。
 * <p>保存即时生效：告警引擎每次比对实时读库，无需刷新缓存或重启（写入不做本地缓存）。
 */
public interface ThresholdService {

    /** 字典：阈值类型 + 告警方向 */
    ThresholdMetaVO meta();

    /**
     * 阈值类型下的站点候选（站点档案中监测类型含该编码的站点，并标记是否已配置）。
     * <p>站点顺序与监测页站点下拉同源：按该阈值类型对应序列的站点排序配置排列，
     * 未配置过顺序时保持接口默认顺序（按站名）。
     *
     * @param type    阈值类型编码（必填，如 #1#）
     * @param keyword 站点名称/编号关键字（可选）
     */
    List<ThresholdSiteVO> sites(String type, String keyword);

    /**
     * 已配置阈值的站点清单（列表筛选下拉用，按站名排序，不区分类型）
     */
    List<ThresholdSiteVO> configuredSites();

    /**
     * 阈值列表分页
     *
     * @param page    页码（从 1 起）
     * @param size    每页条数
     * @param siteId  站点档案主键过滤（可选）
     * @param type    阈值类型编码过滤（可选）
     * @param keyword 站点名称/编号关键字过滤（可选）
     * @return total / page / size / pages / records
     */
    Map<String, Object> page(int page, int size, String siteId, String type, String keyword);

    /** 阈值详情（列表结构，编辑回显用） */
    ThresholdVO detail(String id);

    /**
     * 新增阈值：站点必须支持该类型；同站点同类型唯一（含多值行，如 #1#|#3#）
     *
     * @return 保存后的记录
     */
    ThresholdVO create(ThresholdSaveVO req);

    /**
     * 编辑阈值：仅可改告警方向、警戒值、描述；站点与类型以库中行为准（换类型/换站点 = 删除后新增）。
     * <p>若库中类型仍在旧列 type（zvieyb 为空），保存时一并规范化到 zvieyb，保证告警引擎单一读取口径。
     *
     * @return 保存后的记录
     */
    ThresholdVO update(String id, ThresholdSaveVO req);

    /** 删除阈值（物理删除；删除后该站点该类型不再告警） */
    void delete(String id);
}
