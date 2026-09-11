package com.qgyun.hltgq.hltgqsite.wateruse.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用水总结 · 灌溉水利用系数（实测近似值）口径站点配置（业主 2026-09-11 确认）。
 *
 * <p>近似公式：系数 = Σ(北干/南干/太宿/太怀 四条干渠进水闸区间累计) ÷ 渠首进水闸区间累计
 * （同窗口 ttf 区间累计口径，与流量监测/闸门监测/灌溉用水一致）。
 * 未做田间量测校核，属近似值；渠首缺失/为 0 或四干渠全部无数据时返回 null，不近似回补。
 *
 * <p>站点标识：流量表 skey = COALESCE(stcd, site)（老站用编号，MQTT 站用 site UUID），
 * 每个物理站可配置多个候选标识（任一命中即取数）。
 */
public final class WaterUseCoeffStations {

    /** 站点（codes 为候选标识，stcd / site UUID 任一命中即取数） */
    public static final class Station {
        /** 业务名称（用于日志核对） */
        public final String label;
        /** 候选站点标识 */
        public final List<String> codes;

        private Station(String label, String... codes) {
            this.label = label;
            this.codes = Collections.unmodifiableList(Arrays.asList(codes));
        }
    }

    /** 北干渠进水闸（望江） */
    public static final Station BEIGAN_INTAKE = new Station("北干渠进水闸", "4Ieu32Stnz8dTD8S8GD", "9000000001");

    /** 南干渠进水闸（望江） */
    public static final Station NANGAN_INTAKE = new Station("南干渠进水闸", "cOGko9nLfFz6rRAvKvY", "9000000002");

    /** 太宿干渠进水闸 */
    public static final Station TAISU_INTAKE = new Station("太宿干渠进水闸", "k6V2otO7e4ZmEba7HMD");

    /** 太怀干渠进水闸 */
    public static final Station TAIHUAI_INTAKE = new Station("太怀干渠进水闸", "zxgbgrx0lUXS44Jklo8");

    /** 渠首进水闸（分母） */
    public static final Station QUSHOU_INTAKE = new Station("渠首进水闸", "CAYQ739MiBWMg9gQvyi");

    /** 分子站点（四条干渠进水闸） */
    public static final List<Station> GATES =
            Collections.unmodifiableList(Arrays.asList(BEIGAN_INTAKE, NANGAN_INTAKE, TAISU_INTAKE, TAIHUAI_INTAKE));

    /** 全部候选标识（一次查询覆盖全部站点） */
    private static final List<String> ALL_CODES;

    static {
        Set<String> codes = new LinkedHashSet<>();
        for (Station station : GATES) {
            codes.addAll(station.codes);
        }
        codes.addAll(QUSHOU_INTAKE.codes);
        ALL_CODES = Collections.unmodifiableList(new ArrayList<>(codes));
    }

    /** 全部候选站点标识（去重、保持定义顺序） */
    public static List<String> allCodes() {
        return ALL_CODES;
    }

    private WaterUseCoeffStations() {
    }
}
