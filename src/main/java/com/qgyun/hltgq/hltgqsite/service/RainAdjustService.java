package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 雨量补偿配置维护（监测数据删除方案 §5.1）：
 * 维护入口＝站点+设备联动（选站后联动该站设备下拉），保存 site/device/stcd 三 id；
 * 保存后 ≤5 分钟（mq 缓存 TTL）自动生效，无需重启 mq。
 * <p>操作先例（方案 §6.8/§6.9）：设备侧复位/再次调整前，先在表单停用或更新补偿值，避免过渡窗口错补。
 */
public interface RainAdjustService {

    /** 列表分页（站点/设备名翻译；siteId/keyword 可选过滤） */
    IPage<RainAdjustVO> page(long page, long size, String siteId, String keyword);

    /** 站点候选（全部站点档案，按站名排序） */
    List<RainAdjustVO.SiteOption> sites();

    /** 某站设备候选（设备类型翻译中文名） */
    List<RainAdjustVO.DeviceOption> devices(String siteId);

    /** 新增配置（site/device 必填、device 全局唯一；stcd 由档案 iofhpi 自动填充） */
    RainAdjustVO create(RainAdjustSaveVO body);

    /** 编辑配置（仅 offsetValue/enabled/remark；site/device 锁定） */
    RainAdjustVO update(String id, RainAdjustSaveVO body);

    /** 删除配置（停用优先；删除出厂默认站配置将触发 mq 预置自检 WARN，前端确认已提示） */
    void delete(String id);

    /**
     * 数据维护删除弹窗的「设备补偿」上下文（只读，仅 SELECT）：
     * 由删除行 stcd 反查站点档案 → 已有补偿配置（优先）或该站雨量设备；
     * 返回 deviceFound/configured/deviceId/deviceName/offsetValue/enabled，供弹窗展示现值与必填输入。
     */
    Map<String, Object> adjustContext(String stcd);

    /**
     * 随数据删除配置补偿（数据维护删除弹窗，与软删同事务）：
     * adjustValue=设备当前偏差（正=多灌、负=少灌）→ 写入补偿值 -adjustValue（upsert + 启用）；
     * adjustValue=0 → 设备已复位：停用现有启用中的配置（不删除行，保留审计）；
     * 返回 {action, offsetValue, enabled, deviceName, message} 供前端提示。
     */
    Map<String, Object> applyByDataDelete(String stcd, String tmText, BigDecimal adjustValue);
}
