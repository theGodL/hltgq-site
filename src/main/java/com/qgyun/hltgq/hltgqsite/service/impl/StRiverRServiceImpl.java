package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.entity.WaterThreshold;
import com.qgyun.hltgq.hltgqsite.mapper.StRiverRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterThresholdMapper;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.RiverRegimeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StRiverRServiceImpl extends ServiceImpl<StRiverRMapper, StRiverR> implements StRiverRService {

    @Autowired
    private StStinfoMapper stStinfoMapper;

    @Autowired
    private WaterThresholdMapper waterThresholdMapper;

    /** 河道水位站名称 */
    private static final Set<String> RIVER_STATION_NAMES = new HashSet<>(Arrays.asList("周家河", "花凉亭坝下"));

    /** 水库水位站名称 */
    private static final Set<String> RESERVOIR_STATION_NAMES = new HashSet<>(Collections.singletonList("花凉亭坝上"));

    /**
     * 旧 STCD → 新 STCD（过渡期兼容：客户端可能仍持有旧页面/旧缓存，收到旧编号时自动映射到新编号）
     */
    private static final Map<String, String> LEGACY_TO_NEW_STCD = new HashMap<>();
    static {
        LEGACY_TO_NEW_STCD.put("00000001", "3206400001");
        LEGACY_TO_NEW_STCD.put("00000004", "320640000A");
        LEGACY_TO_NEW_STCD.put("00000007", "3206400007");
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
     * 查询站点：先按 STCD 精确查询；查不到或名称不在白名单时，
     * 按 STCD 对应的站点名称反查（站点表接入过渡期主键可能未对齐）。
     */
    private StStinfo findStation(String stcd, Set<String> stationNames) {
        StStinfo byId = stStinfoMapper.selectById(stcd);
        if (byId != null && byId.getStnm() != null && stationNames.contains(byId.getStnm())) {
            return byId;
        }
        String stnm = STCD_TO_STNM.get(stcd);
        if (stnm == null) return null;
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.eq("zzkaec", stnm);
        wrapper.last("LIMIT 1");
        return stStinfoMapper.selectOne(wrapper);
    }

    /**
     * 查询水位阈值（警戒水位/保证水位）
     * @param siteId 站点 UUID（station_info.id）
     * @return [警戒水位, 保证水位]，无记录时均为 null
     */
    private BigDecimal[] queryThreshold(String siteId) {
        if (siteId == null) return new BigDecimal[]{null, null};
        QueryWrapper<WaterThreshold> wrapper = new QueryWrapper<>();
        wrapper.eq("site", siteId);
        wrapper.like("type", "#1#");  // 水位类型
        wrapper.last("LIMIT 1");
        WaterThreshold t = waterThresholdMapper.selectOne(wrapper);
        if (t == null) return new BigDecimal[]{null, null};
        return new BigDecimal[]{t.getThreshold(), t.getGuarantee()};
    }

    @Override
    public List<StRiverR> latestPerStation() {
        return baseMapper.selectLatestPerStation();
    }

    @Override
    public boolean saveOrUpdateByKey(StRiverR entity) {
        boolean exists = count(new QueryWrapper<StRiverR>()
                .eq("STCD", entity.getStcd())
                .eq("TM", Timestamp.valueOf(entity.getTm()))) > 0;
        if (exists) {
            return update(entity, new UpdateWrapper<StRiverR>()
                    .eq("STCD", entity.getStcd())
                    .eq("TM", Timestamp.valueOf(entity.getTm())));
        }
        return save(entity);
    }

    @Override
    public Page<RiverRegimeVO> riverRegime(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size) {
        // 1. 查询站点信息（过渡期兼容：旧编号映射 + 站名反查兜底，数据查询用站点表真实主键）
        String resolvedInput = LEGACY_TO_NEW_STCD.getOrDefault(stcd, stcd);
        StStinfo stinfo = findStation(resolvedInput, RIVER_STATION_NAMES);
        if (stinfo == null || stinfo.getStnm() == null || !RIVER_STATION_NAMES.contains(stinfo.getStnm())) {
            throw new IllegalArgumentException("河道水位站仅支持: 周家河、花凉亭坝下（收到 stcd: " + stcd + "）");
        }
        String resolvedStcd = stinfo.getStcd();
        String stnm = stinfo.getStnm();

        // 2. 查询站点阈值（警戒水位/保证水位）
        BigDecimal[] ref = queryThreshold(stinfo.getId());
        BigDecimal warningLevel = ref[0];
        BigDecimal guaranteedLevel = ref[1];

        // 3. 分页查询河道水位记录
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<StRiverR>().orderByAsc("TM");
        wrapper.eq("STCD", resolvedStcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));

        Page<StRiverR> rawPage = (Page<StRiverR>) this.page(
                new Page<StRiverR>(page, size).addOrder(OrderItem.asc("TM")), wrapper);

        // 4. 转换为 RiverRegimeVO
        List<RiverRegimeVO> records = rawPage.getRecords().stream().map(r -> {
            RiverRegimeVO vo = new RiverRegimeVO();
            vo.setStcd(r.getStcd());
            vo.setStnm(stnm);
            vo.setTm(r.getTm());
            vo.setWarningLevel(warningLevel != null ? warningLevel.setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setGuaranteedLevel(guaranteedLevel != null ? guaranteedLevel.setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setZ(r.getZ() != null ? r.getZ().setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setWptn(mapWptn(r.getWptn()));
            return vo;
        }).collect(Collectors.toList());

        Page<RiverRegimeVO> result = new Page<>(page, size);
        result.setTotal(rawPage.getTotal());
        result.setRecords(records);
        return result;
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

    @Override
    public Page<ReservoirRegimeVO> reservoirRegime(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size) {
        // 1. 查询站点信息（过渡期兼容：旧编号映射 + 站名反查兜底，数据查询用站点表真实主键）
        String resolvedInput = LEGACY_TO_NEW_STCD.getOrDefault(stcd, stcd);
        StStinfo stinfo = findStation(resolvedInput, RESERVOIR_STATION_NAMES);
        if (stinfo == null || stinfo.getStnm() == null || !RESERVOIR_STATION_NAMES.contains(stinfo.getStnm())) {
            throw new IllegalArgumentException("水库水位站仅支持: 花凉亭坝上（收到 stcd: " + stcd + "）");
        }
        String resolvedStcd = stinfo.getStcd();
        String stnm = stinfo.getStnm();

        // 2. 查询站点阈值（警戒水位/保证水位）
        BigDecimal[] ref = queryThreshold(stinfo.getId());
        BigDecimal warningLevel = ref[0];
        BigDecimal guaranteedLevel = ref[1];

        // 3. 分页查询水库水位记录
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<StRiverR>().orderByAsc("TM");
        wrapper.eq("STCD", resolvedStcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));

        Page<StRiverR> rawPage = (Page<StRiverR>) this.page(
                new Page<StRiverR>(page, size).addOrder(OrderItem.asc("TM")), wrapper);

        // 4. 转换为 ReservoirRegimeVO
        List<ReservoirRegimeVO> records = rawPage.getRecords().stream().map(r -> {
            ReservoirRegimeVO vo = new ReservoirRegimeVO();
            vo.setStcd(r.getStcd());
            vo.setStnm(stnm);
            vo.setTm(r.getTm());
            vo.setWarningLevel(warningLevel != null ? warningLevel.setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setGuaranteedLevel(guaranteedLevel != null ? guaranteedLevel.setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setZ(r.getZ() != null ? r.getZ().setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setWptn(mapWptn(r.getWptn()));
            // 出入库流量临时写死，待设备报文到位后改回字段映射
            vo.setInq(new BigDecimal("21.80"));
            vo.setOtq(new BigDecimal("28.95"));
            return vo;
        }).collect(Collectors.toList());

        Page<ReservoirRegimeVO> result = new Page<>(page, size);
        result.setTotal(rawPage.getTotal());
        result.setRecords(records);
        return result;
    }
}
