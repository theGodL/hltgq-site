package com.qgyun.hltgq.hltgqsite.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 闸门流量历史分页结果：在分页结构上附带该站最近一条流量数据时间。
 * <p>流量表由平台侧计算后写入，出现过站点（如双庙湖节制闸、南山寺节制闸）流量停更
 * 而闸门数据仍在正常上报的情况；此时按当天查询必然为空，页面据此提示“最近一次流量
 * 数据时间、之后未再更新”，避免被误判为系统故障。
 * <p>仅 type=flow 且查询区间无数据时 latestFlowTm 非空；其余情况为 null（不输出业务含义）。
 */
public class GateFlowHistoryPage extends Page<Map<String, Object>> {

    /** 该站最近一条流量数据时间（yyyy-MM-dd HH:mm:ss）；该站从未有流量数据时为 null */
    private String latestFlowTm;

    public GateFlowHistoryPage(long current, long size) {
        super(current, size);
    }

    public String getLatestFlowTm() {
        return latestFlowTm;
    }

    public GateFlowHistoryPage setLatestFlowTm(String latestFlowTm) {
        this.latestFlowTm = latestFlowTm;
        return this;
    }
}
