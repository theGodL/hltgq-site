package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.SiteScopeFilter;

/**
 * 灌区归属范围服务：把页面的 zone（hlt / mth）解析成站点档案渠系过滤条件，
 * 供各监测接口按灌区 Tab 拆分站点（花凉亭排除麻塘湖站点，麻塘湖只含麻塘湖站点）。
 * <p>归属口径：站点档案渠系所属顶级灌区为准，麻塘湖灌区子树归麻塘湖，其余与空渠系归花凉亭；
 * 麻塘湖水库、麻塘湖水库自计井等未挂渠系的站点由配置 {@code hltgq.mth-extra-stcds} 兜底归麻塘湖。
 */
public interface IrrigationScopeService {

    /** 页面 Tab：花凉亭灌区 */
    String ZONE_HLT = "hlt";

    /** 页面 Tab：麻塘湖灌区 */
    String ZONE_MTH = "mth";

    /**
     * 解析灌区范围过滤条件。
     *
     * @param zone hlt / mth；空值或 all 返回 null（不过滤，兼容大屏等既有调用）
     * @return 过滤条件；zone 为空时返回 null
     * @throws IllegalArgumentException zone 值非法
     */
    SiteScopeFilter resolve(String zone);

    /**
     * 判断站点是否属于指定范围（内存过滤用：分页类接口走 SQL 条件，其余列表接口逐行判定）。
     * <p>匹配顺序：显式包含 → 显式排除 → 渠系命中 → 空渠系归属。
     *
     * @param filter      范围条件（null = 不过滤，返回 true）
     * @param canalId     站点档案渠系 id（可为空）
     * @param stationKeys 站点标识（站点编号 / 站点管理主键，任一命中显式包含或排除清单即生效）
     */
    boolean matches(SiteScopeFilter filter, String canalId, String... stationKeys);

    /**
     * zone 归一：空值与 all 返回 null（不过滤）；hlt / mth 原样返回；其余值抛错，
     * 避免 zone 拼写错误时静默按全量返回。
     */
    String normalize(String zone);
}
