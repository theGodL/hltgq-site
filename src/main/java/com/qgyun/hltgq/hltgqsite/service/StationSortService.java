package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.StationSortVO;

import java.util.List;
import java.util.function.Function;

/**
 * 站点排序服务：监测类型 × 站点的展示顺序配置（站点标识 = 站点管理主键）。
 * <p>顺序对同一监测类型的所有接口与页面生效（站点下拉、监测列表等）；
 * 未配置的站点保持各接口原有默认顺序，并排在已配置站点之后（新接入站点自动排在末尾）。
 */
public interface StationSortService {

    /**
     * 排序窗口数据：该类型全部站点 + 当前展示顺序
     * （sortNo 为合成后的当前位次，configured 标记该站是否已保存过配置，
     * sortable=false 表示站点档案中无该站点、无法落库排序）
     */
    List<StationSortVO> list(String metricType);

    /**
     * 保存该类型排序（整表覆盖，事务）：siteIds 按展示顺序提交；
     * 不属于该类型或不可排序的标识忽略，未提交的站点按默认顺序追加在后，保证配置覆盖该类型全量站点。
     *
     * @return 实际落库的站点数
     */
    int save(String metricType, List<String> siteIds);

    /**
     * 按配置顺序重排（通用适配，siteIdGetter 取每行的站点管理主键，随行数据缺失时该行保持默认位置）
     * <p>该类型未配置过排序时原样返回，不改变既有默认顺序。
     */
    <T> List<T> applyOrder(String metricType, List<T> rows, Function<T, String> siteIdGetter);

    /**
     * 该类型已配置的站点管理主键（按配置序号升序）；未配置时返回空列表。
     * <p>供「顺序需进入 SQL 排序」的接口使用（如多站合并分页：内存重排无法保证分页正确），
     * 空列表表示保持该接口自身默认顺序，调用方据此走快速路径。
     */
    List<String> configuredSiteIds(String metricType);
}
