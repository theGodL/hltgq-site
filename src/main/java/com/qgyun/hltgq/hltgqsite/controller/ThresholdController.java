package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.service.ThresholdService;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdMetaVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阈值设置：对监测站点按「站点 + 阈值类型（+ 监测指标）」维护单级警戒值（表 t_auto_hltgq_water_threshold）。
 * <p>字段口径：阈值类型写 zvieyb（历史列 type 仅读取兜底）、监测指标写 zb（多指标类型：水质/墒情）、
 * 告警方向写 alarmdir、警戒值写 threshold、描述写 remark；保证值/设计值本期预留，接口不返回也不改写。
 * <p>权限：查询与增删改对所有登录用户开放（本期不做权限细分，删除不再限管理员）。
 * <p>保存即时生效：告警引擎每次比对实时读库，无需刷新缓存或重启服务。
 */
@RestController
@RequestMapping("/threshold")
public class ThresholdController {

    private static final Logger log = LoggerFactory.getLogger(ThresholdController.class);

    @Autowired
    private ThresholdService thresholdService;

    /** 字典：阈值类型（编码/名称/单位/默认方向/适用站点/监测指标清单）+ 告警方向 */
    @GetMapping("/meta")
    public ThresholdMetaVO meta() {
        return thresholdService.meta();
    }

    /**
     * 阈值类型下的站点候选
     *
     * @param type      阈值类型编码（必填）：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 开度 / #7# 墒情 / #8# 水质
     * @param keyword   站点名称/编号关键字（可选）
     * @param indicator 监测指标编码（可选）：传值时「已配置」标记按「类型 + 指标」判定
     */
    @GetMapping("/sites")
    public List<ThresholdSiteVO> sites(@RequestParam String type,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String indicator) {
        return thresholdService.sites(type, keyword, indicator);
    }

    /**
     * 已配置阈值的站点清单（列表筛选下拉用，按站名排序）
     * <p>只列阈值表中已有配置的站点：筛选项与本页数据同源，选中后必有结果。
     */
    @GetMapping("/configured-sites")
    public List<ThresholdSiteVO> configuredSites() {
        return thresholdService.configuredSites();
    }

    /**
     * 阈值列表分页（按更新时间倒序）
     *
     * @param page    页码（默认 1）
     * @param size    每页条数（默认 20，上限 200）
     * @param siteId  站点档案主键过滤（可选）
     * @param type    阈值类型编码过滤（可选）
     * @param keyword 站点名称/编号关键字过滤（可选）
     * @return total / page / size / pages / records
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String siteId,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) String keyword) {
        return thresholdService.page(page, size, siteId, type, keyword);
    }

    /** 阈值详情（编辑回显用） */
    @GetMapping("/{id}")
    public ThresholdVO detail(@PathVariable String id) {
        return thresholdService.detail(id);
    }

    /**
     * 新增阈值：站点必须支持该类型；多指标类型必选指标；同站点同类型同指标唯一（重复时提示去编辑）。
     *
     * @return 保存后的记录（字典翻译后的完整字段）
     */
    @PostMapping
    public ThresholdVO create(@RequestBody ThresholdSaveVO body) {
        log.info("收到阈值新增请求：site={}，type={}，indicator={}，alarmDir={}，threshold={}",
                body == null ? null : body.getSite(), body == null ? null : body.getType(),
                body == null ? null : body.getIndicator(), body == null ? null : body.getAlarmDir(),
                body == null ? null : body.getThreshold());
        return thresholdService.create(body);
    }

    /**
     * 编辑阈值：仅可改告警方向 / 警戒值 / 描述（站点、类型与指标锁定，换指标=删除后新增）。
     *
     * @return 保存后的记录
     */
    @PutMapping("/{id}")
    public ThresholdVO update(@PathVariable String id, @RequestBody ThresholdSaveVO body) {
        log.info("收到阈值编辑请求：id={}，alarmDir={}，threshold={}",
                id, body == null ? null : body.getAlarmDir(), body == null ? null : body.getThreshold());
        return thresholdService.update(id, body);
    }

    /**
     * 删除阈值：删除后该站点该类型（该指标）不再告警。
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        log.info("收到阈值删除请求：id={}", id);
        thresholdService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return result;
    }
}
