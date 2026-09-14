package com.qgyun.hltgq.hltgqsite.stationdata.controller;

import com.qgyun.hltgq.hltgqsite.stationdata.service.StationDataService;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationCoordsVO;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationDataVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点数据查询接口（/station-data）。
 * <p>枚举字段返回中文（监测类型/站点状态/监测方法）并附编码原文（权威值）；
 * 多选字段译文以「、」拼接；实时查询无缓存；走现有登录会话鉴权（auth.enabled 开关）。
 */
@RestController
@RequestMapping("/station-data")
public class StationDataController {

    @Autowired
    private StationDataService stationDataService;

    /**
     * 站点列表（按编号升序，裸 List）。
     *
     * @param name   站点名称模糊，可选
     * @param code   站点编号模糊，可选
     * @param status 站点状态：在线/离线（或编码 #1#/#2#），可选
     */
    @GetMapping("/list")
    public List<StationDataVO> list(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(required = false) String status) {
        return stationDataService.list(name, code, status);
    }

    /**
     * 批量站点经纬度（按站点档案主键查询，裸 List）。
     *
     * @param ids 站点 id 列表，逗号分隔（如 ids=CAYQ...,abc...），必填；
     *            未命中/非本企业的 id 不出现在结果中，坐标未填的站点 lon/lat 为 null
     */
    @GetMapping("/coords")
    public List<StationCoordsVO> coords(@RequestParam String ids) {
        return stationDataService.coords(splitIds(ids));
    }

    /** 逗号分隔的站点 id → 去空列表（容忍空白项） */
    private List<String> splitIds(String ids) {
        List<String> list = new ArrayList<>();
        if (ids != null) {
            for (String s : ids.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
        }
        return list;
    }
}
