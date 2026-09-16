package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSitesVO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 监测类型站点集合：/station-metrics/sites 的类型分派与「站点排序」抽屉的站点清单共用同一来源，
 * 保证排序窗口列出的站点与该类型页面能看到的站点完全一致。
 */
public interface StationSiteService {

    /**
     * 闸门类型默认顺序中置前展示的站点（该类型未配置排序时生效）。
     * <p>闸门站点清单（站点排序抽屉 / 站点下拉）与闸门监测列表（/gate-monitor/monitoring）
     * 共用本清单：置前站点按本顺序在前，其余站点按名称排序，两处首次展示顺序因此一致。
     */
    List<String> GATE_PRIORITY_STATIONS = Collections.unmodifiableList(Arrays.asList(
            "渠首电站防洪闸", "渠首进水闸", "双庙湖节制闸", "南山寺节制闸"));

    /** 支持的监测类型（与 /station-metrics/sites?type= 值域一致） */
    List<String> supportedTypes();

    /**
     * 按监测类型取该类型全量站点（code + name），顺序为各类型的默认顺序（未应用自定义排序）
     *
     * @param metricType rainfall / gq-rainfall / waterLevel / gate / flow / moisture
     */
    List<StationSiteVO> sitesOfType(String metricType);

    /** 全量分类站点（不传 type 时返回，按 JSON key 分组），顺序为各类型默认顺序 */
    StationSitesVO allSites();
}
