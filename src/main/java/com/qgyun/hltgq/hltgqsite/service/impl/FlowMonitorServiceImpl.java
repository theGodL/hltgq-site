package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.FlowMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.PeriodRegimeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流量监测服务实现
 */
@Service
public class FlowMonitorServiceImpl implements FlowMonitorService {

    @Autowired
    private WaterFlowMapper waterFlowMapper;

    @Autowired
    private StStinfoMapper stStinfoMapper;

    /**
     * 旧 STCD → 新 STCD（过渡期兼容：客户端可能仍持有旧页面/旧缓存，收到旧编号时自动映射到新编号）
     */
    private static final Map<String, String> LEGACY_TO_NEW_STCD = new HashMap<>();
    static {
        LEGACY_TO_NEW_STCD.put("00000001", "3206400001");
        LEGACY_TO_NEW_STCD.put("00000004", "320640000A");
        LEGACY_TO_NEW_STCD.put("00000007", "3206400007");
    }

    /** -999 = 设备不存在：视为缺失转 null 返回（-9991 设备异常保留，透传由前端展示 '--'） */
    private static final BigDecimal DEVICE_MISSING = new BigDecimal("-999");

    private static BigDecimal nullIfMissing(BigDecimal v) {
        return (v != null && v.compareTo(DEVICE_MISSING) == 0) ? null : v;
    }

    /**
     * 新 STCD → 站点名称（站点表接入过渡期，STCD 查不到时按名称反查）
     */
    private static final Map<String, String> STCD_TO_STNM = new HashMap<>();
    static {
        STCD_TO_STNM.put("3206400001", "周家河");
        STCD_TO_STNM.put("320640000A", "花凉亭坝下");
        STCD_TO_STNM.put("3206400007", "花凉亭坝上");
    }

    /**
     * 解析站点：先按 STCD 精确查询；查不到时按名称反查（过渡期主键可能未对齐）。
     * 返回站点表记录，查不到返回 null。
     */
    private StStinfo resolveStation(String stcd) {
        String resolved = LEGACY_TO_NEW_STCD.getOrDefault(stcd, stcd);
        StStinfo byId = stStinfoMapper.selectById(resolved);
        if (byId != null && byId.getStnm() != null) {
            return byId;
        }
        String stnm = STCD_TO_STNM.get(resolved);
        if (stnm == null) return null;
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.eq("zzkaec", stnm);
        wrapper.last("LIMIT 1");
        return stStinfoMapper.selectOne(wrapper);
    }

    /**
     * 通过 stcd 查找站点名称（站点表查不到时返回 stcd 自身）
     */
    private String resolveStnm(String stcd) {
        StStinfo stinfo = resolveStation(stcd);
        return stinfo != null ? stinfo.getStnm() : stcd;
    }

    @Override
    public List<FlowMonitoringVO> monitoring(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime) {
        List<FlowMonitoringVO> rows = waterFlowMapper.selectLatestPerStation(stcds, startTime, endTime);
        // -999 = 设备不存在：转 null 返回（-9991 设备异常保留，透传由前端展示 '--'）
        rows.forEach(r -> {
            r.setQ(nullIfMissing(r.getQ()));
            r.setTf(nullIfMissing(r.getTf()));
            // 累计流量（站点级，与闸门监测同口径）：默认（无起始时间）= 末行 ytf（当年 1月1日 0点起至最新数据时间）；
            // 指定起始时间 = 时间框范围累计 = ttf(范围内末行) − ttf(起点前最近一行)，起点前无积分行基准按 0
            BigDecimal ytf = nullIfMissing(r.getYtf());
            BigDecimal ttf = nullIfMissing(r.getTtf());
            BigDecimal prevTtf = nullIfMissing(r.getPrevTtf());
            BigDecimal cumulativeFlow;
            if (startTime == null) {
                cumulativeFlow = ytf;
            } else if (ttf == null) {
                cumulativeFlow = null;
            } else {
                cumulativeFlow = ttf.subtract(prevTtf != null ? prevTtf : BigDecimal.ZERO);
            }
            r.setCumulativeFlow(cumulativeFlow != null
                    ? cumulativeFlow.setScale(2, RoundingMode.DOWN) : null);
        });
        return rows;
    }

    @Override
    public FlowTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 默认时间范围：近 7 天
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        if (endTime == null) {
            endTime = now;
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }

        // 2. 获取站点名称
        String stnm = stcd;
        List<FlowMonitoringVO> stationInfo = waterFlowMapper.selectLatestPerStation(
                Collections.singletonList(stcd), null, null);
        if (!stationInfo.isEmpty()) {
            stnm = stationInfo.get(0).getStnm();
        }

        // 3. 查询原始记录（时间升序）
        List<Map<String, Object>> rawRecords = waterFlowMapper.selectRawByStcd(stcd, startTime, endTime);

        // 4. 按小时聚合：同一小时内取平均流量
        Map<String, List<BigDecimal>> hourFlows = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRecords) {
            Object tmObj = row.get("tm");
            Object qObj = row.get("q");
            if (tmObj == null || qObj == null) continue;

            LocalDateTime tm;
            if (tmObj instanceof Timestamp) {
                tm = ((Timestamp) tmObj).toLocalDateTime();
            } else if (tmObj instanceof LocalDateTime) {
                tm = (LocalDateTime) tmObj;
            } else {
                continue;
            }

            BigDecimal q;
            if (qObj instanceof BigDecimal) {
                q = (BigDecimal) qObj;
            } else {
                q = new BigDecimal(qObj.toString());
            }

            // -9991 设备异常、-999 设备不存在，均视为缺失不参与聚合
            if (q.compareTo(new BigDecimal("-9991")) == 0
                    || q.compareTo(new BigDecimal("-999")) == 0) continue;

            String hourKey = tm.truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
            hourFlows.computeIfAbsent(hourKey, k -> new ArrayList<>()).add(q);
        }

        // 5. 计算每小时平均流量
        Map<String, BigDecimal> hourAvgFlow = new LinkedHashMap<>();
        for (Map.Entry<String, List<BigDecimal>> entry : hourFlows.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal v : entry.getValue()) {
                sum = sum.add(v);
            }
            BigDecimal avg = sum.divide(new BigDecimal(entry.getValue().size()), 3, RoundingMode.HALF_UP);
            hourAvgFlow.put(entry.getKey(), avg);
        }

        // 6. 生成完整小时序列
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<FlowTrendVO.HourPoint> hours = new ArrayList<>();
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            FlowTrendVO.HourPoint point = new FlowTrendVO.HourPoint();
            point.setHour(key);
            point.setFlow(hourAvgFlow.get(key));
            hours.add(point);
            hour = hour.plusHours(1);
        }

        // 7. 组装结果
        FlowTrendVO vo = new FlowTrendVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(hours);
        return vo;
    }

    @Override
    public Page<FlowMonitoringVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime,
                                           long page, long size) {
        // 查询总数
        long total = waterFlowMapper.selectHistoryCount(stcd, startTime, endTime);

        Page<FlowMonitoringVO> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        // 分页查询（-999 = 设备不存在：转 null 返回；-9991 设备异常保留透传）
        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<FlowMonitoringVO> records = waterFlowMapper.selectHistoryPage(stcd, startTime, endTime, limit, offset);
        records.forEach(r -> {
            r.setQ(nullIfMissing(r.getQ()));
            r.setTf(nullIfMissing(r.getTf()));
        });
        result.setRecords(records);

        return result;
    }

    @Override
    public List<PeriodRegimeVO> periodRegime(LocalDate date, int interval, List<String> stcds) {
        // 防御：interval 非法（<=0）会导致槽位生成死循环
        if (interval <= 0) {
            throw new IllegalArgumentException("时段间隔必须为正整数（1/2/3/6/12）");
        }
        // 1. 解析站点（过渡期按站名反查），建立真实 stcd → stnm 映射
        Map<String, String> stcdToName = new LinkedHashMap<>();
        for (String raw : stcds) {
            String stcd = raw.trim();
            if (stcd == null || stcd.isEmpty()) continue;
            StStinfo info = resolveStation(stcd);
            String realStcd = info != null && info.getStcd() != null ? info.getStcd() : stcd;
            String stnm = info != null && info.getStnm() != null ? info.getStnm() : stcd;
            stcdToName.putIfAbsent(realStcd, stnm);
        }

        if (stcdToName.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resolvedStcds = new ArrayList<>(stcdToName.keySet());

        // 2. 生成时间槽位：选中日期 08:00 起，按 interval 小时递增，到次日 07:00 止
        LocalDateTime slotStart = date.atTime(LocalTime.of(8, 0));
        LocalDateTime slotEnd = date.plusDays(1).atTime(LocalTime.of(7, 0));
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime t = slotStart;
        while (!t.isAfter(slotEnd)) {
            slots.add(t);
            t = t.plusHours(interval);
        }

        // 3. 批量查询所有选中站点在时间窗口内的原始水位记录（按 stcd, tm 升序）
        // 业主口径：整点水位取「整点之前的最后一条采集」（如 11 点值 = 10:00~11:00 间最新一条），
        // 整点之后的采集归下一整点，一旦进入下一时段，前一时段数值固定不再变动。
        // 因此窗口从首个槽位的前一时刻 slotStart-interval 开始，到 slotEnd 止（多取无害，
        // 恰好等于首槽位前一整点的记录会被槽位匹配的左开右闭规则忽略）
        List<Map<String, Object>> rawRecords = waterFlowMapper.selectPeriodRawRecords(
                resolvedStcds, slotStart.minusHours(interval), slotEnd);

        // 4. 构建结果：N站 × M槽 = 完整 VO 列表
        // 槽位匹配策略（业主口径）：记录 tm ∈ (prevSlot, slot] 左开右闭 → 归属 slot；
        // 同槽位多条取 tm 最大的（最新）。整点整点的记录归该整点槽位（如 11:00:00 归 11 点），
        // 整点之后归下一整点，保证前一时段数值在进入下一时段后固定不动
        List<PeriodRegimeVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : stcdToName.entrySet()) {
            String stcd = entry.getKey();
            String stnm = entry.getValue();

            // 为该站点构建：槽位 → 最新有效记录（z/wptn/q）
            Map<LocalDateTime, SlotVal> slotBest = new LinkedHashMap<>();
            for (Map<String, Object> row : rawRecords) {
                if (!stcd.equals(String.valueOf(row.get("stcd")))) continue;

                Object tmObj = row.get("tm");
                if (tmObj == null) continue;

                LocalDateTime recordTm;
                if (tmObj instanceof Timestamp) {
                    recordTm = ((Timestamp) tmObj).toLocalDateTime();
                } else if (tmObj instanceof LocalDateTime) {
                    recordTm = (LocalDateTime) tmObj;
                } else {
                    continue;
                }

                // 找到该记录归属的槽位：(prevSlot, slot] 左开右闭
                for (int i = 0; i < slots.size(); i++) {
                    LocalDateTime slot = slots.get(i);
                    LocalDateTime prevSlot = slot.minusHours(interval);
                    if (recordTm.isAfter(prevSlot) && !recordTm.isAfter(slot)) {
                        SlotVal v = new SlotVal();
                        // -999 = 设备不存在：转 null 返回（-9991 设备异常保留透传由前端展示 '--'）
                        v.z = nullIfMissing(toBigDecimal(row.get("z")));
                        v.wptn = row.get("wptn") != null ? String.valueOf(row.get("wptn")) : null;
                        v.q = nullIfMissing(toBigDecimal(row.get("q")));
                        // 同槽位多条时，后扫描的（tm 更大）覆盖前者
                        slotBest.put(slot, v);
                        break;
                    }
                }
            }

            // 为该站点的每个槽位生成 VO
            for (LocalDateTime slot : slots) {
                PeriodRegimeVO vo = new PeriodRegimeVO();
                vo.setStcd(stcd);
                vo.setStnm(stnm);
                vo.setTm(slot);
                SlotVal v = slotBest.get(slot);
                vo.setZ(v != null ? v.z : null);
                vo.setWptn(v != null ? mapWptn(v.wptn) : null);
                vo.setQ(v != null ? v.q : null);
                // msqmt/msamt/msvmt 无数据源，留空
                result.add(vo);
            }
        }

        return result;
    }

    /** 槽位内最新有效记录（z/wptn/q） */
    private static class SlotVal {
        BigDecimal z;
        String wptn;
        BigDecimal q;
    }

    /** Map 值 → BigDecimal（null 安全） */
    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 水势代码 → 中文 */
    private String mapWptn(String wptn) {
        if (wptn == null || wptn.isEmpty()) return "无涨落信息";
        switch (wptn.trim()) {
            case "4":
            case "涨": return "涨";
            case "5":
            case "落": return "落";
            case "6":
            case "平": return "平";
            default:  return "无涨落信息";
        }
    }

}
