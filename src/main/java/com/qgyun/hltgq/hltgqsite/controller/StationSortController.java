package com.qgyun.hltgq.hltgqsite.controller;

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
     * 排序窗口数据：该监测类型下全部站点 + 当前展示顺序
     *
     * @param type 监测类型：waterLevel(水位) / rainfall(雨量，含水库站点) /
     *             flow(流量) / gate(闸门监测页、水位监测页灌区面板) / moisture(墒情)
     */
    @GetMapping
    public List<StationSortVO> list(@RequestParam String type) {
        return stationSortService.list(type);
    }

    /**
     * 保存站点排序（整表覆盖）：siteIds 按拖拽/序号调整后的展示顺序提交
     * <p>不属于该类型的站点标识忽略；未提交的站点按默认顺序追加在后（新接入站点自动排末尾）。
     *
     * @return success + count（落库站点数）
     */
    @PostMapping
    public Map<String, Object> save(@RequestBody StationSortSaveVO body) {
        log.info("收到站点排序保存请求：type={}，提交站点数={}",
                body.getType(), body.getSiteIds() == null ? 0 : body.getSiteIds().size());
        int count = stationSortService.save(body.getType(), body.getSiteIds());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", count);
        return result;
    }
}
