package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.entity.SluiceDischarge;
import com.qgyun.hltgq.hltgqsite.mapper.SluiceDischargeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 闸站流量计算参数接口（t_auto_hltgq_water_sluice_discharge）
 * <p>每次修改新增一条数据（version 递增），回显与使用始终取该站点最新一条。
 * <p>写保护：新增/修改仅系统管理员可操作（@RequireAdmin，拦截器校验角色），
 * 非管理员返回 403；查询不限权限。
 */
@RestController
@RequestMapping("/sluice-discharge")
public class SluiceDischargeController {

    @Autowired
    private SluiceDischargeMapper sluiceDischargeMapper;

    /**
     * 查询指定站点最新一条流量计算参数
     *
     * @param siteId 站点 UUID
     * @return 最新一条配置，无数据时返回 null
     */
    @GetMapping("/latest")
    public SluiceDischarge latest(@RequestParam String siteId) {
        return sluiceDischargeMapper.selectLatestBySite(siteId);
    }

    /**
     * 新增一条流量计算参数（每次修改都新增，version 自动 +1）
     * <p>权限：仅系统管理员可操作（非管理员返回 403，拦截器校验）。
     *
     * @param req 请求体：site + 4 个流量系数 + 孔宽 + 孔高 + 闸底高程
     * @return 保存后的完整记录（含新 version）
     */
    @RequireAdmin
    @PostMapping
    public SluiceDischarge save(@RequestBody SluiceDischarge req) {
        if (req == null || req.getSite() == null || req.getSite().trim().isEmpty()) {
            throw new IllegalArgumentException("站点不能为空");
        }
        SluiceDischarge entity = new SluiceDischarge();
        entity.setId(UUID.randomUUID().toString());
        entity.setSite(req.getSite());
        entity.setFullOpenFreeCoeff(req.getFullOpenFreeCoeff());
        entity.setSubmergedFlowCoeff(req.getSubmergedFlowCoeff());
        entity.setControlledFreeCoeff(req.getControlledFreeCoeff());
        entity.setOrificeSubmergedCoeff(req.getOrificeSubmergedCoeff());
        entity.setWidth(req.getWidth());
        entity.setHeight(req.getHeight());
        entity.setBottomElevation(req.getBottomElevation());
        entity.setVersion(nextVersion(req.getSite()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        sluiceDischargeMapper.insert(entity);
        return entity;
    }

    private Integer nextVersion(String siteId) {
        Integer max = sluiceDischargeMapper.selectMaxVersionBySite(siteId);
        return (max == null ? 0 : max) + 1;
    }
}
