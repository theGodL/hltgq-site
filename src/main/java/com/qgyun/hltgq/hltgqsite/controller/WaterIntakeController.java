package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.mapper.WaterIntakeMapper;
import com.qgyun.hltgq.hltgqsite.vo.WaterIntakeRecordVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterIntakeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/water-intake")
public class WaterIntakeController {

    @Autowired
    private WaterIntakeMapper waterIntakeMapper;

    /**
     * 取水量查询
     *
     * @param wiustCd   取水单位编码
     * @param dimension 时间维度：hour / day / month / year
     * @param startTime 开始时间，格式 yyyy-MM-dd
     * @param endTime   结束时间，格式 yyyy-MM-dd
     */
    @GetMapping
    public WaterIntakeVO query(
            @RequestParam String wiustCd,
            @RequestParam(defaultValue = "day") String dimension,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String mpCd) {

        String start = startTime + " 00:00:00";
        String end   = endTime   + " 23:59:59";

        List<WaterIntakeRecordVO> records;
        switch (dimension) {
            case "hour":
                records = waterIntakeMapper.selectHour(wiustCd, mpCd, start, end);
                break;
            case "month":
                records = waterIntakeMapper.selectMonth(wiustCd, mpCd, start, end);
                break;
            case "year":
                records = waterIntakeMapper.selectYear(wiustCd, mpCd, start, end);
                break;
            default:
                records = waterIntakeMapper.selectDay(wiustCd, mpCd, start, end);
        }

        WaterIntakeVO vo = new WaterIntakeVO();
        vo.setWiustNm(waterIntakeMapper.selectWiustNm(wiustCd));
        vo.setRecords(records);
        return vo;
    }

    @GetMapping("/points")
    public List<WaterIntakeRecordVO> points(@RequestParam String wiustCd) {
        return waterIntakeMapper.selectPoints(wiustCd);
    }

    @GetMapping("/units")
    public List<WaterIntakeRecordVO> units() {
        return waterIntakeMapper.selectAllUnits();
    }
}
