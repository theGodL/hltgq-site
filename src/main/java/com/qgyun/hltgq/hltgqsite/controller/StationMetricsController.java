package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.service.StStinfoService;
import com.qgyun.hltgq.hltgqsite.vo.StationMetricsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/station-metrics")
public class StationMetricsController {

    @Autowired
    private StStinfoService stStinfoService;

    @Autowired
    private StRiverRService stRiverRService;

    @Autowired
    private StPptnRService stPptnRService;

    @GetMapping
    public List<StationMetricsVO> list() {
        List<StStinfo> stations = stStinfoService.list();

        Map<String, StRiverR> riverMap = stRiverRService.latestPerStation()
                .stream()
                .filter(r -> r.getStcd() != null)
                .collect(Collectors.toMap(r -> r.getStcd().trim(), r -> r));

        // 有雨量数据的站点集合（只要 rain_info 表中有记录即为雨量站）
        Set<String> pptnStcds = stPptnRService.latestPerStation()
                .stream()
                .filter(r -> r.getStcd() != null && r.getDrp() != null)
                .map(r -> r.getStcd().trim()).collect(Collectors.toSet());

        // 当前水文日边界（以结束日命名）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime hydroStart, hydroLabel;
        if (now.getHour() >= 8) {
            hydroStart = now.toLocalDate().atTime(8, 0, 0);
            hydroLabel = now.toLocalDate().plusDays(1).atTime(8, 0, 0);
        } else {
            hydroStart = now.toLocalDate().minusDays(1).atTime(8, 0, 0);
            hydroLabel = now.toLocalDate().atTime(8, 0, 0);
        }
        LocalDateTime hydroEnd = hydroLabel.minusSeconds(1);

        Map<String, StPptnR> todayPptnMap = stPptnRService.todaySumPerStation(hydroStart, hydroEnd)
                .stream()
                .filter(r -> r.getStcd() != null)
                .collect(Collectors.toMap(r -> r.getStcd().trim(), r -> r));

        return stations.stream()
                .filter(s -> s.getStcd() != null)
                .map(s -> {
            String stcd = s.getStcd().trim();
            StationMetricsVO vo = new StationMetricsVO();
            vo.setStcd(stcd);
            vo.setStnm(s.getStnm());

            StRiverR river = riverMap.get(stcd);
            if (river != null) {
                vo.setZ(river.getZ() != null ? river.getZ().setScale(2, RoundingMode.DOWN) : null);
                vo.setRiverTm(river.getTm());
            }

            if (pptnStcds.contains(stcd)) {
                StPptnR todayPptn = todayPptnMap.get(stcd);
                if (todayPptn != null) {
                    vo.setDrp(todayPptn.getDrp() != null ? todayPptn.getDrp().setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(1));
                } else {
                    vo.setDrp(BigDecimal.ZERO.setScale(1));
                }
                vo.setPptnTm(hydroStart.toLocalDate());
            }

            if (river != null && pptnStcds.contains(stcd)) vo.setType("all");
            else if (river != null)                         vo.setType("water");
            else if (pptnStcds.contains(stcd))              vo.setType("rain");

            return vo;
        }).collect(Collectors.toList());
    }
}
