package com.qgyun.hltgq.hltgqsite.inventory.controller;

import com.qgyun.hltgq.hltgqsite.inventory.service.InventoryService;
import com.qgyun.hltgq.hltgqsite.inventory.vo.InventoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备库存查询接口（/inventory）。
 * <p>枚举字段返回中文（物资类别/库存状态）并附编码原文（权威值）；
 * 一条库存含多条明细（编号/物资名称/品牌型号/数量）；
 * 实时查询无缓存；走现有登录会话鉴权（auth.enabled 开关）。
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /**
     * 库存列表（按创建时间倒序，裸 List）。
     *
     * @param departmentId 所属部门 ID（czdwpj = t_apaas_uc_org.id），可选
     * @param stockStatus  库存状态：安全库存/库存预警（或编码 #1#/#2#），可选
     */
    @GetMapping("/list")
    public List<InventoryVO> list(@RequestParam(required = false) String departmentId,
                                  @RequestParam(required = false) String stockStatus) {
        return inventoryService.list(departmentId, stockStatus);
    }
}
