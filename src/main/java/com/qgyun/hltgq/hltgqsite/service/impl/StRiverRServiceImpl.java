package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.StRiverRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
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

    /** 河道水位站名称 */
    private static final Set<String> RIVER_STATION_NAMES = new HashSet<>(Arrays.asList("周家河", "花凉亭坝下"));

    /** 水库水位站名称 */
    private static final Set<String> RESERVOIR_STATION_NAMES = new HashSet<>(Collections.singletonList("花凉亭坝上"));

    /** 旧 STCD → 新 STCD 映射（新表已全部迁移，逐步补充） */
    private static final Map<String, String> OLD_TO_NEW_STCD = new HashMap<>();
    static {
        OLD_TO_NEW_STCD.put("00000004", "320640000A");  // 花凉亭坝下
        OLD_TO_NEW_STCD.put("00000007", "3206400007");  // 花凉亭坝上
        // 00000001 周家河 - 新 STCD 待确认，暂不可查
    }

    /** 站点固定参考值（按名称索引）：{ 警戒水位, 保证水位 } (m)，暂未配置则为 null */
    private static final Map<String, BigDecimal[]> STATION_REF = new HashMap<>();
    static {
        STATION_REF.put("周家河", new BigDecimal[]{null, null});
        STATION_REF.put("花凉亭坝下", new BigDecimal[]{null, null});
        STATION_REF.put("花凉亭坝上", new BigDecimal[]{null, null});
    }

    /** 解析 STCD：旧格式 → 新格式；已是新格式则原样返回 */
    private String resolveStcd(String stcd) {
        return OLD_TO_NEW_STCD.getOrDefault(stcd, stcd);
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
        // 1. 解析新旧 STCD，查询站点信息
        String resolvedStcd = resolveStcd(stcd);
        StStinfo stinfo = stStinfoMapper.selectById(resolvedStcd);
        String stnm = stinfo != null ? stinfo.getStnm() : null;

        if (stnm == null || !RIVER_STATION_NAMES.contains(stnm)) {
            throw new IllegalArgumentException("河道水位站仅支持: 周家河、花凉亭坝下");
        }

        // 2. 站点参考水位（按名称索引）
        BigDecimal[] ref = STATION_REF.get(stnm);
        BigDecimal warningLevel = ref != null ? ref[0] : null;
        BigDecimal guaranteedLevel = ref != null ? ref[1] : null;

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
        // 1. 解析新旧 STCD，查询站点信息
        String resolvedStcd = resolveStcd(stcd);
        StStinfo stinfo = stStinfoMapper.selectById(resolvedStcd);
        String stnm = stinfo != null ? stinfo.getStnm() : null;

        if (stnm == null || !RESERVOIR_STATION_NAMES.contains(stnm)) {
            throw new IllegalArgumentException("水库水位站仅支持: 花凉亭坝上");
        }

        // 2. 站点参考水位（按名称索引）
        BigDecimal[] ref = STATION_REF.get(stnm);
        BigDecimal warningLevel = ref != null ? ref[0] : null;
        BigDecimal guaranteedLevel = ref != null ? ref[1] : null;

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
            // Q 字段暂同时作为入库/出库流量，待设备报文到位后区分字段映射
            vo.setInq(r.getQ() != null ? r.getQ().setScale(3, java.math.RoundingMode.DOWN) : null);
            vo.setOtq(r.getQ() != null ? r.getQ().setScale(3, java.math.RoundingMode.DOWN) : null);
            return vo;
        }).collect(Collectors.toList());

        Page<ReservoirRegimeVO> result = new Page<>(page, size);
        result.setTotal(rawPage.getTotal());
        result.setRecords(records);
        return result;
    }
}
