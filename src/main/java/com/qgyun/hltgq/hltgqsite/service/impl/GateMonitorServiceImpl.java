package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.service.GateMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.GateHistoryVO;
import com.qgyun.hltgq.hltgqsite.vo.GateHoleData;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GateMonitorServiceImpl implements GateMonitorService {

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    @Override
    public List<GateMonitoringVO> monitoring(LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 查询各闸孔最新一条数据
        List<GateMonitor> rows = gateMonitorMapper.selectLatestPerHole(startTime, endTime);

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 按站点 (site) 分组
        Map<String, List<GateMonitor>> siteGroup = rows.stream()
                .collect(Collectors.groupingBy(GateMonitor::getSite, LinkedHashMap::new, Collectors.toList()));

        // 3. 每个站点构建一个 GateMonitoringVO
        List<GateMonitoringVO> result = new ArrayList<>();
        for (Map.Entry<String, List<GateMonitor>> entry : siteGroup.entrySet()) {
            String siteId = entry.getKey();
            List<GateMonitor> holes = entry.getValue();

            // 站点名称（取第一条记录的 siteName，各孔相同）
            String siteName = holes.get(0).getSiteName();

            // 监测时间：取各闸孔中最新者
            LocalDateTime latestTm = holes.stream()
                    .map(GateMonitor::getTm)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // 闸前/闸后水位：取最新一条有值记录
            GateMonitor latestWithZ = holes.stream()
                    .filter(h -> h.getUpZ() != null || h.getDownZ() != null)
                    .max(Comparator.comparing(GateMonitor::getTm, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .orElse(null);

            // 各闸孔开度与状态（按闸孔号排序）
            List<GateHoleData> holeDataList = holes.stream()
                    .sorted(Comparator.comparing(GateMonitor::getGateNo))
                    .map(h -> {
                        GateHoleData d = new GateHoleData();
                        d.setGateNo(h.getGateNo());
                        d.setOpenDegree(h.getOpenDegree());
                        d.setStatus(h.getStatus());
                        return d;
                    })
                    .collect(Collectors.toList());

            GateMonitoringVO vo = new GateMonitoringVO();
            vo.setSiteId(siteId);
            vo.setSiteName(siteName);
            vo.setTm(latestTm);
            vo.setUpZ(latestWithZ != null ? latestWithZ.getUpZ() : null);
            vo.setDownZ(latestWithZ != null ? latestWithZ.getDownZ() : null);
            vo.setHoles(holeDataList);
            result.add(vo);
        }

        // 按站点名称排序
        result.sort(Comparator.comparing(GateMonitoringVO::getSiteName, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    @Override
    public Page<GateHistoryVO> history(String siteId, LocalDateTime startTime, LocalDateTime endTime,
                                        long page, long size) {
        long total = gateMonitorMapper.selectHistoryCount(siteId, startTime, endTime);

        Page<GateHistoryVO> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<GateHistoryVO> records = gateMonitorMapper.selectHistory(siteId, startTime, endTime, limit, offset);
        result.setRecords(records);

        return result;
    }
}
