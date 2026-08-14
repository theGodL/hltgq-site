package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.RiverRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.YearsRegimeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@RestController
@RequestMapping("/st-river-r")
public class StRiverRController {

    @Autowired
    private StRiverRService stRiverRService;

    @GetMapping("/latest")
    public List<StRiverR> latest() {
        return stRiverRService.latestPerStation();
    }

    @GetMapping("/list")
    public List<StRiverR> list(@RequestParam(required = false) String stcd) {
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<StRiverR>().orderByAsc("TM");
        if (stcd != null) wrapper.eq("STCD", stcd);
        return stRiverRService.list(wrapper);
    }

    @GetMapping("/page")
    public Page<StRiverR> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<>();
        if (stcd != null) wrapper.eq("STCD", stcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        return (Page<StRiverR>) stRiverRService.page(new Page<StRiverR>(page, size).addOrder(OrderItem.asc("TM")), wrapper);
    }

    @GetMapping("/export")
    public List<StRiverR> export(
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<StRiverR>().orderByAsc("TM");
        if (stcd != null) wrapper.eq("STCD", stcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        return stRiverRService.list(wrapper);
    }

    @GetMapping
    public StRiverR getOne(
            @RequestParam String stcd,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tm) {
        return stRiverRService.getOne(new QueryWrapper<StRiverR>()
                .eq("STCD", stcd)
                .eq("TM", Timestamp.valueOf(tm)));
    }

    @PostMapping
    public boolean save(@RequestBody StRiverR stRiverR) {
        return stRiverRService.saveOrUpdateByKey(stRiverR);
    }

    @PutMapping
    public boolean update(@RequestBody StRiverR stRiverR) {
        return stRiverRService.update(stRiverR, new UpdateWrapper<StRiverR>()
                .eq("STCD", stRiverR.getStcd())
                .eq("TM", Timestamp.valueOf(stRiverR.getTm())));
    }

    @DeleteMapping
    public boolean delete(
            @RequestParam String stcd,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tm) {
        return stRiverRService.remove(new QueryWrapper<StRiverR>()
                .eq("STCD", stcd)
                .eq("TM", Timestamp.valueOf(tm)));
    }

    /**
     * 水库水位-河道水情数据（多站合并分页，时间倒序）
     * stcd 支持逗号分隔多站，仅限周家河(3206400001)和花凉亭坝下(320640000A)
     */
    @GetMapping("/regime")
    public Page<RiverRegimeVO> regime(
            @RequestParam String stcd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return stRiverRService.riverRegime(splitStcds(stcd), startTime, endTime, page, size);
    }

    /**
     * 水库水位-河道水情导出（全量，无分页，时间倒序）
     */
    @GetMapping("/regime/export")
    public List<RiverRegimeVO> regimeExport(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return stRiverRService.riverRegimeExport(splitStcds(stcd), startTime, endTime);
    }

    /**
     * 水库水位-水库水情数据（多站合并分页，时间倒序）
     * stcd 支持逗号分隔多站，目前仅支持花凉亭坝上(3206400007)
     */
    @GetMapping("/reservoir-regime")
    public Page<ReservoirRegimeVO> reservoirRegime(
            @RequestParam String stcd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return stRiverRService.reservoirRegime(splitStcds(stcd), startTime, endTime, page, size);
    }

    /**
     * 水库水位-水库水情导出（全量，无分页，时间倒序）
     */
    @GetMapping("/reservoir-regime/export")
    public List<ReservoirRegimeVO> reservoirRegimeExport(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return stRiverRService.reservoirRegimeExport(splitStcds(stcd), startTime, endTime);
    }

    /** 逗号分隔的站点编号 → 去空列表 */
    private List<String> splitStcds(String stcd) {
        List<String> list = new ArrayList<>();
        if (stcd != null) {
            for (String s : stcd.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) list.add(t);
            }
        }
        return list;
    }

    /**
     * 水库水情-水情简报：指定日期三个水情站（周家河、花凉亭坝下、花凉亭坝上）的
     * 昨日 8 点/20 点、今日 8 点水位、水势、流量、当年最高水位等。
     * date 非必填，默认当天。
     */
    @GetMapping("/water-brief")
    public List<WaterBriefVO> waterBrief(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        if (date == null) date = LocalDate.now();
        return stRiverRService.waterBrief(date);
    }

    /**
     * 水库水情-多年同期水情：年份区间 + 月份，各站该月平均水位
     *
     * @param startYear 起始年份（含）
     * @param endYear   结束年份（含）
     * @param month     月份（1-12）
     */
    @GetMapping("/years-regime")
    public YearsRegimeVO yearsRegime(
            @RequestParam int startYear,
            @RequestParam int endYear,
            @RequestParam int month) {
        return stRiverRService.yearsRegime(startYear, endYear, month);
    }
}
