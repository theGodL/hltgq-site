package com.qgyun.hltgq.hltgqsite.decision.controller;

import com.qgyun.hltgq.hltgqsite.decision.service.NetworkDeviceService;
import com.qgyun.hltgq.hltgqsite.decision.vo.NetworkDeviceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网络设备监控接口（/network-device）。
 */
@RestController
@RequestMapping("/network-device")
public class NetworkDeviceController {

    @Autowired
    private NetworkDeviceService networkDeviceService;

    /**
     * 设备监控总览：一次请求渲染整页（顶部汇总卡 + 固定 7 类设备列），无参数。
     */
    @GetMapping("/summary")
    public NetworkDeviceVO summary() {
        return networkDeviceService.summary();
    }
}
