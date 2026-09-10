package com.qgyun.hltgq.hltgqsite.irrigation.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 灌溉用水四县供水量口径与站点配置（业主 2026-09-10 定义）。
 *
 * <p>实际供水量口径：
 * <ul>
 *   <li>宿松县 = 章大计量闸过闸流量（设备未接入，候选标识预置为「章大测流站」，接入后自动生效）</li>
 *   <li>怀宁县 = 太怀干渠长喉槽流量（站点已配流量监测类型，设备离线中）</li>
 *   <li>望江县 = 南干渠进水闸 + 北干渠进水闸</li>
 *   <li>太湖县 = 渠首进水闸总流量 − 宿松/怀宁/望江各站取水 − 三电站（集岭/渠首/双庙湖）过流 − 损耗</li>
 * </ul>
 *
 * <p>需水量侧片区 ↔ 四县对应：太湖=总干渠；望江=南干渠+北干渠；宿松=太宿干渠；怀宁=太怀灌区。
 *
 * <p>站点标识：流量数据表 skey = COALESCE(stcd, site)（老站用编号，MQTT 站用 site UUID），
 * 每个物理站可配置多个候选标识（任一命中即取数）。设备接入后若数据落在候选标识上即自动生效，
 * 若改用新站点标识，仅需调整本类配置。
 */
public final class IrrigationStationConfig {

    /** 站点（codes 为空表示设备未接入，供水量记缺失） */
    public static final class Station {
        /** 业务名称（用于缺失数据说明与日志） */
        public final String label;
        /** 候选站点标识（stcd 或 site UUID，任一命中即取数） */
        public final List<String> codes;

        private Station(String label, String... codes) {
            this.label = label;
            this.codes = Collections.unmodifiableList(Arrays.asList(codes));
        }
    }

    /** 县供水口径定义 */
    public static final class CountySupply {
        /** 灌区（县）名称（列表行名称） */
        public final String district;
        /** 需水量汇总来源片区名（汇总表 summary_type='片区'） */
        public final List<String> demandAreas;
        /** 相加口径站点组（宿松/怀宁/望江） */
        public final List<Station> plusStations;
        /** 总量站（太湖=渠首进水闸；其余为 null） */
        public final Station totalStation;
        /** 扣减站组（太湖：各县取水 + 三电站；其余为空） */
        public final List<Station> minusStations;
        /** 设计保证率配置键（application.properties） */
        public final String designRateProperty;
        /** 设计保证率缺省值（产品稿默认值，业务定稿前使用） */
        public final String designRateDefault;

        private CountySupply(String district, List<String> demandAreas, List<Station> plusStations,
                             Station totalStation, List<Station> minusStations,
                             String designRateProperty, String designRateDefault) {
            this.district = district;
            this.demandAreas = demandAreas;
            this.plusStations = plusStations;
            this.totalStation = totalStation;
            this.minusStations = minusStations;
            this.designRateProperty = designRateProperty;
            this.designRateDefault = designRateDefault;
        }
    }

    // ===== 站点定义 =====

    /** 章大计量闸（太宿干渠）：设备未接入；候选=章大测流站节点，接入后若换新站点标识需同步调整 */
    public static final Station ZHANGDA = new Station("章大计量闸", "1YCfIVHtH49zlrBQJadm");

    /** 太怀干渠长喉槽：站点已配流量监测类型（3408229002，设备离线无数据） */
    public static final Station TAIHUAI_CHANGHOU = new Station("太怀干渠长喉槽", "3408229002", "jqEawVNMpMgzawQKXJb");

    /** 南干渠进水闸（望江） */
    public static final Station NANGAN_INTAKE = new Station("南干渠进水闸", "9000000002");

    /** 北干渠进水闸（望江） */
    public static final Station BEIGAN_INTAKE = new Station("北干渠进水闸", "9000000001");

    /** 渠首进水闸（总干渠渠首，太湖总量取数） */
    public static final Station QUSHOU_INTAKE = new Station("渠首进水闸", "CAYQ739MiBWMg9gQvyi");

    /** 集岭电站（太怀干渠渠首段）：设备未接入；候选=集岭电站进水闸（9000000007） */
    public static final Station JILING_POWER = new Station("集岭电站", "9000000007", "VzoiGl9W3XT7WheoEXV");

    /** 渠首水电站：设备未接入；候选=渠首水电站节点 */
    public static final Station QUSHOU_POWER = new Station("渠首水电站", "Hk46mKEtTlS4pc0MxDR");

    /** 双庙湖水电站：设备未接入；候选=双庙湖水电站节点 */
    public static final Station SHUANGMIAOHU_POWER = new Station("双庙湖水电站", "eHFKq6PXpbPaigoHx6xZ");

    // ===== 四县口径（顺序固定：宿松、怀宁、望江、太湖） =====

    /** 全部县的供水口径，输出行顺序按此列表 */
    public static final List<CountySupply> COUNTIES;

    /** 全部候选站点标识（一次查询覆盖所有部件取数） */
    private static final List<String> ALL_STATION_CODES;

    static {
        CountySupply susong = new CountySupply("宿松县",
                Collections.singletonList("太宿干渠"),
                Collections.singletonList(ZHANGDA), null, Collections.emptyList(),
                "irrigation.water.design-rate.susong", "90");
        CountySupply huaining = new CountySupply("怀宁县",
                Collections.singletonList("太怀灌区"),
                Collections.singletonList(TAIHUAI_CHANGHOU), null, Collections.emptyList(),
                "irrigation.water.design-rate.huaining", "85");
        CountySupply wangjiang = new CountySupply("望江县",
                Arrays.asList("南干渠", "北干渠"),
                Arrays.asList(NANGAN_INTAKE, BEIGAN_INTAKE), null, Collections.emptyList(),
                "irrigation.water.design-rate.wangjiang", "80");
        // 太湖县：渠首总流量 − 宿松/怀宁/望江取水 − 三电站过流 − 损耗
        CountySupply taihu = new CountySupply("太湖县",
                Collections.singletonList("总干渠"),
                Collections.emptyList(), QUSHOU_INTAKE,
                Arrays.asList(ZHANGDA, TAIHUAI_CHANGHOU, NANGAN_INTAKE, BEIGAN_INTAKE,
                        JILING_POWER, QUSHOU_POWER, SHUANGMIAOHU_POWER),
                "irrigation.water.design-rate.taihu", "95");
        COUNTIES = Collections.unmodifiableList(Arrays.asList(susong, huaining, wangjiang, taihu));

        Set<String> codes = new LinkedHashSet<>();
        for (CountySupply county : COUNTIES) {
            for (Station station : county.plusStations) {
                codes.addAll(station.codes);
            }
            if (county.totalStation != null) {
                codes.addAll(county.totalStation.codes);
            }
            for (Station station : county.minusStations) {
                codes.addAll(station.codes);
            }
        }
        ALL_STATION_CODES = Collections.unmodifiableList(new ArrayList<>(codes));
    }

    /** 全部候选站点标识（去重、保持定义顺序） */
    public static List<String> allStationCodes() {
        return ALL_STATION_CODES;
    }

    private IrrigationStationConfig() {
    }
}
