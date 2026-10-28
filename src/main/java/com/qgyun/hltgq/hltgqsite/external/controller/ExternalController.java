package com.qgyun.hltgq.hltgqsite.external.controller;

import com.qgyun.hltgq.hltgqsite.external.service.ExternalService;
import com.qgyun.hltgq.hltgqsite.external.vo.ExternalVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 三维系统对接接口（/external）：面向第三方大屏/数字孪生的外部数据服务。
 * <p>契约见 src/main/resources/三维系统对接接口.md。全部为实时查询（无轮询任务）；
 * 参数越界由全局 IllegalArgumentException 处理返回 400；走现有登录会话鉴权。
 */
@RestController
@RequestMapping("/external")
public class ExternalController {

    @Autowired
    private ExternalService externalService;

    /**
     * 闸门类型数量：节制闸 27 / 分水口 36 / 退水闸 10 / 渡槽 6（三方收集台账实数，非实时统计）。
     */
    @GetMapping("/gate-type-count")
    public ExternalVO.GateTypeCount gateTypeCount() {
        return externalService.gateTypeCount();
    }

    /**
     * 闸站实时数据：按站点编码（stcd=档案 iofhpi）或站点 ID（档案 id）查询任意闸站的
     * 闸前/闸后水位、流量、站名与管理单位；不传 stcd 时返回渠首进水闸（兼容原无参调用）。
     * stcd 对应站点不存在返回 400。
     */
    @GetMapping("/intake-gate")
    public ExternalVO.IntakeGate intakeGate(@RequestParam(required = false) String stcd) {
        return externalService.intakeGate(stcd);
    }

    /**
     * 巡检汇总：累计巡检次数、巡检计划数、完成巡检数、维养预算、维养成本。
     */
    @GetMapping("/patrol-summary")
    public ExternalVO.PatrolSummary patrolSummary() {
        return externalService.patrolSummary();
    }

    /**
     * 巡检/突发事件逐日趋势：日期区间聚合（yyyy-MM-dd），默认近 30 天，上限 366 天。
     * 突发事件 = AI 智能分析告警（告警表 type=#3#）按发生时间聚合。
     */
    @GetMapping("/daily-trend")
    public ExternalVO.DailyTrend dailyTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return externalService.dailyTrend(startDate, endDate);
    }

    /**
     * 问题处理统计 + 应急响应统计：已处理/未整改（问题表）；突发事件/已解除响应/未解除响应（AI 告警 type=#3#）。
     */
    @GetMapping("/issue-stats")
    public ExternalVO.IssueStats issueStats() {
        return externalService.issueStats();
    }

    /**
     * 视频监测按管理所聚合：安装位置首段归组，总数/在线/离线。
     */
    @GetMapping("/video-summary")
    public List<ExternalVO.VideoItem> videoSummary() {
        return externalService.videoSummary();
    }

    /**
     * 视频通道清单（设备级）：每台视频设备一行，含通道号/名称/在线状态/经纬度/管理所/
     * 安装位置/所属站点档案信息，供三方视频接入使用。
     * <p>siteName：可选，按站点名称模糊匹配（如「双庙湖」命中「双庙湖管理所」「双庙湖节制闸」）；
     * 不传或空白表示不筛选（全量返回）。
     */
    @GetMapping("/video-devices")
    public List<ExternalVO.VideoDeviceItem> videoDevices(@RequestParam(required = false) String siteName) {
        return externalService.videoDevices(siteName);
    }

    /**
     * 监测站点列表（按监测类型合并）：站点集合取站点档案（与原 qx-api 列表同口径，含当前无数据的站点），
     * 每行 = 档案字段（原字段名）+ 该站实时监测值（平铺 + data 子对象）；站点顺序按「站点排序」配置输出。
     * <p>type：1 水位 / 2 雨量 / 3 流量 / 4 闸门 / 5 视频 / 7 墒情 / 8 水质（兼容 #N# 编码与语义串）；非法值返回 400。
     * 视频类型（5）为站点档案清单、无实时值，用于替代原 qx-api 视频站点列表
     * （/qx-api/qx-apaas-run/ar/4idWo4GcvCd8fWPQSif/kjximffu?type=5）。
     * name：可选，按站点名称模糊匹配（如「渠首」命中「渠首管理所」）；不传或空白表示不筛选。
     * <p>coreIndicator（核心指标）每行必有，只有水位站与雨量站有：水位类型（1）取档案「核心指标-水位」列 ijzsby、
     * 雨量类型（2）取档案「核心指标-雨量」列 ccnhtm（两列在档案里分列存储），统一输出两位小数文本，
     * 档案未填按 0.00 输出；其余类型无核心指标固定为 null。原始列名 ccnhtm / ijzsby 同时保留。
     * 闸门站（epjutj 含 #4#）恒为 null——闸站普遍同时登记水位类型而出现在 type=1 列表里，不按 ijzsby 输出。
     * 响应信封与原 qx-api 一致（success/status/data/msg/...）。
     */
    @GetMapping("/station-monitor")
    public ExternalVO.StationMonitor stationMonitor(@RequestParam String type,
                                                    @RequestParam(required = false) String name) {
        return externalService.stationMonitor(type, name);
    }

    /**
     * 站点数量统计（大屏「站点数」卡片，纯统计不返回明细，避免为取一个数字拉全量列表）。
     * <p>type：可选（与 station-monitor 的必填约束无关）。不传/空白 = 全量统计；
     * 传则只统计该类型（1..8 / #N# / 语义串），非法值返回 400。
     * <p>data.total = 档案全量；data.typedTotal = 可进入 station-monitor 列表的站点并集
     * （#1#/#2#/#3#/#4#/#5#/#7#/#8# 七类按站去重，与各类型列表按 id 去重结果一致；#6# 模型类型不计入）；
     * data.byType = 按类型计数（键 1..8，含 #6#，多类型站重复计）。
     * <p>响应信封与 /external/* 其它接口一致（success/status/data/...）。
     */
    @GetMapping("/station-count")
    public ExternalVO.StationCount stationCount(@RequestParam(required = false) String type) {
        return externalService.stationCount(type);
    }

    /**
     * 站点下拉选项（轻量：仅 id/名称/编号，供告警筛选等下拉框一次拉取）。
     * <p>type：可选。不传 = 全类型并集（与 station-count 的 typedTotal 同口径，按站去重）；
     * 传则只返回该类型站点（1..8 / #N# / 语义串），非法值 400。
     * <p>响应信封与 /external/* 其它接口一致，data 为站点数组：[{id, name, stcd}]。
     */
    @GetMapping("/station-options")
    public ExternalVO.StationOptions stationOptions(@RequestParam(required = false) String type) {
        return externalService.stationOptions(type);
    }
}
