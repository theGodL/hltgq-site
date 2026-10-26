package com.qgyun.hltgq.hltgqsite.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 监测页通用报文服务：历史数据行 → 库中该时刻入库的原始报文
 */
public interface MonitorMessageService {

    /**
     * 按站点标识 + 监测时间查询原始报文（监测接口共用的「查看报文」）
     * <p>窗口按页面口径传入（默认前后各 2 分钟）：水文/流量/墒情/雨量/闸门数据的采集时刻与报文入库时刻
     * 基本对齐；水质数据的整点补齐行对应的是该小时内实际上报的报文，窗口需放宽到监测时间之后约 1 小时。
     *
     * @param key    站点标识（测站编码或站点 UUID，任一命中即可）
     * @param tm     监测时间（历史数据行的时间）
     * @param before 窗口起点 = tm − before 分钟（0~720）
     * @param after  窗口终点 = tm + after 分钟（0~720）
     * @param limit  最大返回条数（1~200）
     * @return 报文列表（type=frame 原始帧 / json 解析报文，含 tag/frame/tm/sendtm/afn/msgText）
     */
    List<Map<String, Object>> messages(String key, LocalDateTime tm, int before, int after, int limit);
}
