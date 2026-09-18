package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowStationVO;
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
     * @param canalId   渠系 id（可选，站点表 ywvyds → 渠系管理表 id；传入时按渠系树过滤：
     *                  返回该渠系及其所有子孙渠系下的站点，每条记录携带 canalId/canalName）
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     */
    List<FlowMonitoringVO> monitoring(List<String> stcds, String canalId,
                                      LocalDateTime startTime, LocalDateTime endTime);

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
     * 流量图表-固定八站瞬时流量（按日期时间点查询）
     * <p>返回固定顺序八站（渠首进水闸、双庙湖节制闸、南山寺节制闸、太怀干渠进水闸、
     * 毕岭节制闸、汪元渡槽、南干渠进水闸、北干渠进水闸），每站取距选中时间点最近
     * （±30 分钟内）的流量入库数据；半小时内无入库数据则该站流量为 null（该时间点无报文）。
     *
     * @param time 选中时间点（半小时粒度）
     */
    List<FlowStationVO> stationFlow(LocalDateTime time);

    /**
     * 日时段水情表（水位站点多选，按日期+时段生成时间槽位，匹配实测数据）
     *
     * @param date     选中日期
     * @param interval 时段间隔（小时），1/2/3/6/12
     * @param stcds    站点编号列表
     */
    List<PeriodRegimeVO> periodRegime(LocalDate date, int interval, List<String> stcds);
}
