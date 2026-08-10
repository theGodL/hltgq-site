package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.PeriodRegimeVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流量监测服务
 */
public interface FlowMonitorService {

    /**
     * 流量监测-最新数据（每个站点一条）
     *
     * @param stcds     站点编号列表（可选，多选）
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     */
    List<FlowMonitoringVO> monitoring(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 流量趋势图表（小时级，默认近 7 天）
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间（可选，默认 7 天前整点）
     * @param endTime   截止时间（可选，默认当前整点）
     */
    FlowTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 流量历史数据（分页，按监测时间倒序）
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     * @param page      页码
     * @param size      每页条数
     */
    Page<FlowMonitoringVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size);

    /**
     * 日时段水情表（水位站点多选，按日期+时段生成时间槽位，匹配实测数据）
     *
     * @param date     选中日期
     * @param interval 时段间隔（小时），1/2/3/6/12
     * @param stcds    站点编号列表
     */
    List<PeriodRegimeVO> periodRegime(LocalDate date, int interval, List<String> stcds);
}
