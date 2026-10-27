package com.qgyun.hltgq.hltgqsite.h5.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.h5.mapper.H5WaterResourceMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.WaterResourceOverviewVO;
import com.qgyun.hltgq.hltgqsite.irrigation.config.IrrigationStationConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport.GateConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterUsageMeter;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H5 水资源概览服务：一个接口聚合页面三块组件（避免 H5 多请求），按年度区间查询。
 * <ul>
 *   <li>列表：水资源分配方案及执行 = 用水户（一县一户）× 当年有效年度计划明细，
 *       分配水量 = 该户计划水量、执行进度 = 该户闸口当年实测放水量 ÷ 分配水量</li>
 *   <li>两卡片：灌溉总面积固定 105.83 万亩；已完成供水 = 四县实际供水量合计（万m³，3 位截断）</li>
 *   <li>四卡片：四县面积（用水户档案「灌溉面积」，万亩）+ 供水（万m³，3 位截断）
 *       + 总需水（该县用水户当年用水申请合计，万m³，2 位截断；固定顺序：宿松/怀宁/望江/太湖）</li>
 * </ul>
 * <p>口径说明（业主 2026-10-27 变更）：
 * <ul>
 *   <li>「水资源量分布」饼图已下线，本接口不再返回 distribution</li>
 *   <li>灌区内一县即一个用水户，行政区名 = 用水户名称；县卡片的总需水按名称包含县名匹配用水户</li>
 *   <li>分配水量取年度计划（已生效 #2# / 已归档 #3#，排除编制中与已作废）的明细计划水量</li>
 *   <li>执行进度复用 {@link WaterUsageMeter} 户级口径（逐闸口按符号合计，任一闸口缺读数即为 null）</li>
 *   <li>总需水取用水申请（待审核 #1# + 已通过 #2#，已驳回不计）</li>
 *   <li>「供水」= 该县用水户当年关联闸口的实际放水量合计（同一户级口径），
 *       与列表执行进度同一数据源；不再取四县站点流量</li>
 * </ul>
 * <p>数值精度：实际供水量及其合计（已完成供水）3 位截断（累计流量口径）；
 * 分配水量/总需水 万m³ 2 位截断、面积 亩→万亩 2 位截断；进度 1 位截断。
 * <p>年度区间：startDate/endDate 格式 yyyy-MM-dd HH:mm:ss（前端 date.min/date.max 直传），
 * 缺省=本年度 [01-01, 次年 01-01)；年份取 startDate 年份，作用于用水户计划/需水选取。
 */
@Service
public class H5WaterResourceService {

    private static final Logger log = LoggerFactory.getLogger(H5WaterResourceService.class);

    /** 灌溉总面积（万亩）：产品定稿固定值 */
    private static final BigDecimal TOTAL_AREA = new BigDecimal("105.83");

    /** 日期入参格式（与前端 date.min/date.max 一致） */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 百分比换算基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private H5WaterResourceMapper mapper;

    @Autowired
    private WaterUserMapper waterUserMapper;

    @Autowired
    private WaterUsageMeter waterUsageMeter;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 水资源概览（聚合一次返回）。
     *
     * @param startDate 年度区间起点（yyyy-MM-dd HH:mm:ss），缺省=本年度 01-01 00:00:00
     * @param endDate   年度区间终点（不含），缺省=startDate 次年 01-01 00:00:00
     */
    public WaterResourceOverviewVO overview(String startDate, String endDate) {
        LocalDateTime start = parseOrDefault(startDate, null);
        if (start == null) {
            start = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        LocalDateTime end = parseOrDefault(endDate, start.plusYears(1));
        if (!end.isAfter(start)) {
            log.warn("[水资源概览] endDate={} 不晚于 startDate={}，已回退为 start+1 年", endDate, startDate);
            end = start.plusYears(1);
        }
        int year = start.getYear();
        log.info("[水资源概览] 年度区间 {} ~ {}（year={}）", DATE_FORMAT.format(start), DATE_FORMAT.format(end), year);

        // 用水户 = 行政区（一县一户）：分配列表、县卡片供水/总需水共用一次取数
        List<WaterUserVO> users = waterUserMapper.selectLedgerUsers(null);
        Map<String, BigDecimal> planByUser = planWaterByUser(year);
        Map<String, BigDecimal> demandByUser = demandByUser(year);
        // 户级闸口放水量（m³ 原值）：分配列表进度与四县供水同一数据源，只取一次
        Map<String, BigDecimal> userSupply = supplyByUser(users, start, end);
        Map<String, BigDecimal> countySupply = countySupplyWan(users, userSupply);

        WaterResourceOverviewVO vo = new WaterResourceOverviewVO();
        vo.setAllocationList(buildAllocationList(users, planByUser, userSupply));
        vo.setCountyDetails(buildCountyDetails(users, demandByUser, countySupply));
        vo.setIrrigationStats(buildStats(countySupply));
        return vo;
    }

    /** 解析日期入参，非法/缺省返回 fallback */
    private LocalDateTime parseOrDefault(String value, LocalDateTime fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_FORMAT);
        } catch (Exception e) {
            log.warn("[水资源概览] 非法日期入参 {}={}，使用缺省值", value == null ? "" : value.trim());
            return fallback;
        }
    }

    /** 当年有效年度计划的计划水量（key = 用水户 ID，value = m³；空 Map = 无计划） */
    private Map<String, BigDecimal> planWaterByUser(int year) {
        Map<String, BigDecimal> result = new HashMap<>();
        List<Map<String, Object>> rows = mapper.selectAnnualPlanWaterByUser(year);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String userId = row.get("userid") == null ? null : String.valueOf(row.get("userid"));
                BigDecimal amount = asBigDecimal(row.get("planwater"));
                if (userId != null && amount != null) {
                    result.put(userId, amount);
                }
            }
        }
        if (result.isEmpty()) {
            log.info("[水资源概览] year={} 无已生效/已归档年度计划明细，分配水量均为 null", year);
        } else {
            log.info("[水资源概览] year={} 年度计划覆盖用水户 {} 户", year, result.size());
        }
        return result;
    }

    /** 当年用水申请合计（key = 用水户 ID，value = m³；待审核 + 已通过） */
    private Map<String, BigDecimal> demandByUser(int year) {
        Map<String, BigDecimal> result = new HashMap<>();
        List<Map<String, Object>> rows = mapper.selectDemandByUser(year);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String userId = row.get("userid") == null ? null : String.valueOf(row.get("userid"));
                BigDecimal amount = asBigDecimal(row.get("amount"));
                if (userId != null && amount != null) {
                    result.put(userId, amount);
                }
            }
        }
        if (result.isEmpty()) {
            log.info("[水资源概览] year={} 无待审核/已通过的用水申请，总需水均为 null", year);
        } else {
            log.info("[水资源概览] year={} 用水申请覆盖用水户 {} 户", year, result.size());
        }
        return result;
    }

    /** 当年各用水户关联闸口实测放水量（key = 用水户 ID，value = m³ 原值；闸口缺读数/未配置不入 Map） */
    private Map<String, BigDecimal> supplyByUser(List<WaterUserVO> users, LocalDateTime start, LocalDateTime end) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (users == null || users.isEmpty()) {
            log.info("[水资源概览] 无正常状态用水户，闸口放水量为空");
            return result;
        }
        String startText = DATE_FORMAT.format(start);
        // 计量窗口为闭区间：终点取所在秒（前端缺省传次年 01-01 00:00:00，回退 1 秒避免跨年取值）
        LocalDateTime meterEnd = end.toLocalTime().equals(LocalTime.MIDNIGHT) ? end.minusSeconds(1) : end;
        String endText = DATE_FORMAT.format(meterEnd);
        for (WaterUserVO user : users) {
            GateConfig config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
            BigDecimal supply = waterUsageMeter.userInterval(config, startText, endText);
            if (supply != null) {
                result.put(user.getId(), supply);
            }
            log.info("[水资源概览] 用水户={}({}) 闸口放水量={}m³ 窗口={} ~ {}",
                    user.getName(), user.getUserNo(), supply, startText, endText);
        }
        return result;
    }

    /** 列表：分配方案及执行（行 = 用水户；分配水量 = 年度计划，执行进度 = 户闸口放水量 ÷ 计划） */
    private List<WaterResourceOverviewVO.AllocationRow> buildAllocationList(
            List<WaterUserVO> users, Map<String, BigDecimal> planByUser, Map<String, BigDecimal> supplyByUser) {
        if (users == null || users.isEmpty()) {
            log.info("[水资源概览] 列表 无正常状态用水户，返回空列表");
            return Collections.emptyList();
        }
        List<WaterResourceOverviewVO.AllocationRow> result = new ArrayList<>(users.size());
        for (WaterUserVO user : users) {
            BigDecimal planned = planByUser.get(user.getId());
            BigDecimal supply = supplyByUser.get(user.getId());

            WaterResourceOverviewVO.AllocationRow item = new WaterResourceOverviewVO.AllocationRow();
            item.setCode(user.getId());
            item.setUserNo(user.getUserNo());
            item.setDistrict(user.getName());
            item.setAllocatedVolume(planned == null ? null : wanOf(planned));
            item.setProgress(supply != null && planned != null && planned.compareTo(BigDecimal.ZERO) > 0
                    ? supply.multiply(HUNDRED).divide(planned, 1, RoundingMode.DOWN) : null);
            result.add(item);
            log.info("[水资源概览] 列表行 用水户={}({}) 分配水量={}万m³ 执行进度={}%",
                    user.getName(), user.getUserNo(), item.getAllocatedVolume(), item.getProgress());
        }
        return result;
    }

    /**
     * 四县供水（万m³，3 位截断）= 归属该县用水户的当年闸口放水量合计。
     * <p>业务口径（业主 2026-10-27 补充）：四个县实际用水直接取该年该县用水户关联闸口的实际放水量，
     * 与分配列表执行进度同源；不再取四县站点流量。
     * <p>县归属按用水户名称包含县名匹配（兼容「太湖县XX用水户」命名），命中多个全部计入；
     * 无匹配户或该县全部用水户闸口无读数时不放入 Map（出参为 null，不补 0）。
     */
    private Map<String, BigDecimal> countySupplyWan(List<WaterUserVO> users, Map<String, BigDecimal> supplyByUser) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (users == null || users.isEmpty()) {
            log.info("[水资源概览] 四卡片 无正常状态用水户，供水均为 null");
            return result;
        }
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            BigDecimal cubic = null;
            String matched = null;
            for (WaterUserVO user : users) {
                if (user.getName() == null || !user.getName().contains(county.district)) {
                    continue;
                }
                matched = (matched == null) ? user.getName() : matched + "、" + user.getName();
                BigDecimal supply = supplyByUser.get(user.getId());
                if (supply != null) {
                    cubic = (cubic == null) ? supply : cubic.add(supply);
                }
            }
            if (cubic == null) {
                log.info("[水资源概览] 四卡片 行政区={} 供水=null（关联用水户={}，闸口无放水量）",
                        county.district, matched == null ? "无" : matched);
                continue;
            }
            BigDecimal wan = scale3(cubic.movePointLeft(4));
            result.put(county.district, wan);
            log.info("[水资源概览] 四卡片 行政区={} 供水={}万m³（闸口放水量合计={}m³，关联用水户={}）",
                    county.district, wan, cubic, matched);
        }
        return result;
    }

    /** 四卡片：固定四县顺序（该县面积取用水户档案 + 供水 + 总需水；无数据时对应字段为 null，卡片仍可渲染） */
    private List<WaterResourceOverviewVO.CountyDetail> buildCountyDetails(
            List<WaterUserVO> users, Map<String, BigDecimal> demandByUser, Map<String, BigDecimal> countySupply) {
        List<WaterResourceOverviewVO.CountyDetail> result = new ArrayList<>();
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            WaterResourceOverviewVO.CountyDetail item = new WaterResourceOverviewVO.CountyDetail();
            item.setCode(planKey(county.district));
            item.setDistrict(county.district);
            item.setIrrigationArea(areaOfCounty(county.district, users));
            item.setFinishedVolume(countySupply.get(county.district));
            BigDecimal demand = demandOfCounty(county.district, users, demandByUser);
            item.setDemandVolume(demand == null ? null : wanOf(demand));
            result.add(item);
            log.info("[水资源概览] 四卡片 行政区={} 面积={}万亩 供水={}万m³ 总需水={}万m³",
                    item.getDistrict(), item.getIrrigationArea(), item.getFinishedVolume(), item.getDemandVolume());
        }
        return result;
    }

    /**
     * 该县总需水（m³）= 归属该县的用水户当年用水申请合计。
     * <p>业务口径：一县即一个用水户，行政区名即用水户名称；为兼容「太湖县XX用水户」这类命名，
     * 按名称包含县名匹配，命中多个时全部计入。无匹配户或无申请返回 null。
     */
    private BigDecimal demandOfCounty(String district, List<WaterUserVO> users, Map<String, BigDecimal> demandByUser) {
        if (users == null || users.isEmpty() || demandByUser.isEmpty()) {
            return null;
        }
        BigDecimal sum = null;
        for (WaterUserVO user : users) {
            if (user.getName() == null || !user.getName().contains(district)) {
                continue;
            }
            BigDecimal demand = demandByUser.get(user.getId());
            if (demand != null) {
                sum = sum == null ? demand : sum.add(demand);
            }
        }
        return sum;
    }

    /**
     * 该县面积（万亩，2 位截断）= 归属该县的用水户档案「灌溉面积」合计（亩 → 万亩）。
     * <p>面积在「用水户」页面手填维护（字段 irrigation_area，单位亩），一县一户即县面积；
     * 改档案即生效，无需改代码。未维护返回 null（不补 0）。
     */
    private BigDecimal areaOfCounty(String district, List<WaterUserVO> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        BigDecimal mu = null;
        for (WaterUserVO user : users) {
            if (user.getName() == null || !user.getName().contains(district)) {
                continue;
            }
            BigDecimal area = user.getIrrigationArea();
            if (area != null) {
                mu = mu == null ? area : mu.add(area);
            }
        }
        return mu == null ? null : mu.movePointLeft(4).setScale(2, RoundingMode.DOWN);
    }

    /** 县名 → 稳定编码（前端按 code 精确映射，不依赖数组顺序） */
    private String planKey(String district) {
        switch (district) {
            case "宿松县":
                return "susong";
            case "怀宁县":
                return "huaining";
            case "望江县":
                return "wangjiang";
            case "太湖县":
                return "taihu";
            default:
                return null;
        }
    }

    /** 两卡片：总面积固定值 + 已完成供水（四县供水合计，全缺失为 null；合计 = 四卡片展示值之和） */
    private WaterResourceOverviewVO.IrrigationStats buildStats(Map<String, BigDecimal> countySupply) {
        WaterResourceOverviewVO.IrrigationStats stats = new WaterResourceOverviewVO.IrrigationStats();
        stats.setTotalArea(TOTAL_AREA);
        BigDecimal sum = null;
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            BigDecimal wan = countySupply.get(county.district);
            if (wan != null) {
                sum = (sum == null) ? wan : sum.add(wan);
            }
        }
        stats.setFinishedVolume(sum);
        log.info("[水资源概览] 灌溉统计 总面积={}万亩 已完成供水合计={}万m³", TOTAL_AREA, stats.getFinishedVolume());
        return stats;
    }

    /** Object → BigDecimal（PG numeric/Integer/Double 通用） */
    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** m³ → 万m³（2 位截断：分配水量/总需水口径） */
    private BigDecimal wanOf(BigDecimal cubicMeters) {
        return cubicMeters.movePointLeft(4).setScale(2, RoundingMode.DOWN);
    }

    /** 3 位截断（累计流量口径水量：实际供水量及其合计） */
    private BigDecimal scale3(BigDecimal value) {
        return value.setScale(3, RoundingMode.DOWN);
    }
}
