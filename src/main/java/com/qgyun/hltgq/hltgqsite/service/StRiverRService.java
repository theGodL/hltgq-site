package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.RiverRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.YearsRegimeVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface StRiverRService extends IService<StRiverR> {

    boolean saveOrUpdateByKey(StRiverR entity);

    List<StRiverR> latestPerStation();

    /**
     * 河道水情数据（多站合并分页，时间倒序）
     *
     * @param stcds     站点编号列表（仅限 3206400001 周家河 / 320640000A 花凉亭坝下）
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param page      页码
     * @param size      每页条数
     * @return 分页结果
     */
    Page<RiverRegimeVO> riverRegime(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime, long page, long size);

    /**
     * 河道水情数据（导出全量，时间倒序，无分页）
     */
    List<RiverRegimeVO> riverRegimeExport(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 水库水情数据（多站合并分页，时间倒序）
     *
     * @param stcds     站点编号列表（目前仅支持 3206400007 花凉亭坝上）
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param page      页码
     * @param size      每页条数
     * @return 分页结果
     */
    Page<ReservoirRegimeVO> reservoirRegime(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime, long page, long size);

    /**
     * 水库水情数据（导出全量，时间倒序，无分页）
     */
    List<ReservoirRegimeVO> reservoirRegimeExport(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 水库水情-水情简报：指定日期三个水情站的 8 点/20 点水位、水势、流量、当年最高水位等
     *
     * @param date 查询日期，默认当天
     * @return 每站一条简报记录
     */
    List<WaterBriefVO> waterBrief(LocalDate date);

    /**
     * 水库水情-多年同期水情：年份区间 + 月份，各站该月平均水位
     *
     * @param startYear 起始年份（含）
     * @param endYear   结束年份（含）
     * @param month     月份（1-12）
     * @return 每年一行，各站月平均水位与 stations 对齐
     */
    YearsRegimeVO yearsRegime(int startYear, int endYear, int month);
}
