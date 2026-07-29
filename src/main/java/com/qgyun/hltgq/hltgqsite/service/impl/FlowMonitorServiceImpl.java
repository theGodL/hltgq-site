package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.FlowMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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

    @Override
    public List<FlowMonitoringVO> monitoring(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime) {
        return waterFlowMapper.selectLatestPerStation(stcds, startTime, endTime);
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
            Object tmObj = row.get("TM");
            Object qObj = row.get("Q");
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

        // 分页查询
        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<FlowMonitoringVO> records = waterFlowMapper.selectHistoryPage(stcd, startTime, endTime, limit, offset);
        result.setRecords(records);

        return result;
    }
}
