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

    /** 站点范围：花凉亭水库站点（水库水位站、水库雨量站）；灌区范围 = 其余站点 */
    String SCOPE_RESERVOIR = "reservoir";

    /** 站点范围：灌区站点（非水库站点） */
    String SCOPE_GQ = "gq";

    /** 支持的监测类型（与 /station-metrics/sites?type= 值域一致） */
    List<String> supportedTypes();

    /**
     * 按监测类型取该类型全量站点（code + name），顺序为各类型的默认顺序（未应用自定义排序）
     *
     * @param metricType rainfall / gq-rainfall / waterLevel / gate / flow / moisture
     */
    List<StationSiteVO> sitesOfType(String metricType);

    /**
     * 按站点范围过滤清单（保持原顺序）：{@link #SCOPE_RESERVOIR} 只留花凉亭水库站点，
     * {@link #SCOPE_GQ} 只留灌区站点；空值或 all 不限制范围（原样返回）。
     * <p>供「站点排序」抽屉按页面主 Tab（花凉亭灌区 / 花凉亭水库）拆分展示范围。
     */
    List<StationSiteVO> filterByScope(List<StationSiteVO> sites, String scope);

    /**
     * 站点范围归一：空值与 all 表示不限制范围（返回 null）；未知范围抛错，
     * 避免 scope 拼写错误时静默按全量处理。
     */
    default String normalizeScope(String scope) {
        if (scope == null) return null;
        String value = scope.trim();
        if (value.isEmpty() || "all".equals(value)) return null;
        if (SCOPE_RESERVOIR.equals(value) || SCOPE_GQ.equals(value)) return value;
        throw new IllegalArgumentException("无效的 scope 值: " + scope
                + "，可选: reservoir(水库站点) / gq(灌区站点)");
    }

    /** 全量分类站点（不传 type 时返回，按 JSON key 分组），顺序为各类型默认顺序 */
    StationSitesVO allSites();
}
