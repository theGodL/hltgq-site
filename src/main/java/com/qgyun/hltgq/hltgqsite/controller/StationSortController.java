package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
import com.qgyun.hltgq.hltgqsite.vo.StationSortSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSortVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点排序：监测页面「站点排序」抽屉（顺序 + 站点名称列表，拖拽或序号调整）。
 * <p>顺序按监测类型维度存储，对同一类型的所有接口与页面生效（站点下拉、监测列表、图表站点轴等）。
 */
@RestController
@RequestMapping("/station-sort")
public class StationSortController {

    private static final Logger log = LoggerFactory.getLogger(StationSortController.class);

    @Autowired
    private StationSortService stationSortService;

    /**
     * 排序窗口数据：该监测类型下站点 + 当前展示顺序
     *
     * @param type  监测类型：waterLevel(水位) / rainfall(雨量，含水库站点) /
     *              flow(流量) / gate(闸门监测页、水位监测页灌区面板) / moisture(墒情)
     * @param scope 站点范围（可选）：reservoir 只列花凉亭水库站点，gq 只列灌区站点；
     *              空值或 all 列出该类型全部站点（页面按主 Tab 传入，抽屉只展示本范围站点）
     */
    @GetMapping
    public List<StationSortVO> list(@RequestParam String type,
                                    @RequestParam(required = false) String scope) {
        return stationSortService.list(type, scope);
    }

    /**
     * 保存站点排序（仅系统管理员，与页面「站点排序」入口显隐同口径）：siteIds 按拖拽/序号调整后的展示顺序提交
     * <p>不属于该类型的站点标识忽略；未提交的站点按当前顺序追加在后（新接入站点自动排末尾）。
     * <p>scope 指定范围时只重排该范围所在分块，另一范围保持原顺序，整表恒为「水库块在前、灌区块在后」。
     *
     * @return success + count（落库站点数）
     */
    @RequireAdmin
    @PostMapping
    public Map<String, Object> save(@RequestBody StationSortSaveVO body) {
        log.info("收到站点排序保存请求：type={}，scope={}，提交站点数={}",
                body.getType(), body.getScope(),
                body.getSiteIds() == null ? 0 : body.getSiteIds().size());
        int count = stationSortService.save(body.getType(), body.getSiteIds(), body.getScope());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", count);
        return result;
    }
}
