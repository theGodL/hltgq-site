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
     * **不传或空白 = 全部点位**（大屏「全部」页签）：站点集合为档案 epjutj 非空的全部站点
     * （含仅 #6# 模型 / 编码为空的档案行，不含管理所等 epjutj 为空的记录），并逐站带该站各监测类型实时值——
     * 与分别调各 type 拿到的实时值一致，只是合并为一行：data 为实时值并集（tm 取各类型最新）、
     * dataByType 为按类型的实时值（键 1/2/3/4/7/8，只含该站有数据的类型，每项同单独 type 的 data）、
     * hasData = 任一类型有数据，isOnline = 任一类型在线（全无实时值时按档案状态兜底）；
     * 全部模式不使用站点排序配置，保持档案默认顺序 + 在线优先（6 类实时值并发装载）。
     * 水位类型（1）只返回通用水位站：档案含 #1# 但排除闸站（#4#）——闸站水位是闸前 / 闸后实测值，
     * 只由 type=4 提供，同一个站不在两个列表重复展示；其余类型不受影响。
     * 视频类型（5）为站点档案清单、无实时值，用于替代原 qx-api 视频站点列表
     * （/qx-api/qx-apaas-run/ar/4idWo4GcvCd8fWPQSif/kjximffu?type=5）。
     * name：可选，按站点名称模糊匹配（如「渠首」命中「渠首管理所」）；不传或空白表示不筛选。
     * <p>coreIndicator（核心指标）业务上是站点的「通用水位 / 雨量」指标：水位类型（1）取档案实时列 ccnhtm1、
     * 雨量类型（2）取档案实时列 ijzsby1（mq 实时写入的两位小数字符串），输出两位小数文本。
     * **没有该指标时返回 null**（前端不展示）：*1 列的 '0.00' 表示当前无新数据（超过上报周期 + 5 分钟、
     * 24h 断连或从未上报），不是 0 读数，按无指标处理；其余类型同样为 null。
     * 闸门站（epjutj 含 #4#）恒为 null——闸门水位是闸前 / 闸后实测值（type=4 的 upZ / downZ），
     * 不属于通用水位指标（type=1 列表已在档案层排除闸站，此判断为防御）。
     * 对外字段名仍为 ccnhtm / ijzsby，值取实时列原样（同表的 double 列为 2026-09 停更的历史快照，不读）。
     * <p>totalCount / onlineCount：本次返回结果集（按 type + name 过滤后）的站点总数与在线站点数——
     * totalCount = data 行数，onlineCount = 其中 isOnline=true 的行数（空结果为 0/0，全部模式同口径）。
     * <p>typeCounts（仅全部模式）：按类型分组的总站点数 / 在线站点数，键 1..8（与 station-count.byType 对齐），
     * 供大屏左侧图例一次拿全；1/2/3/4/5/7/8 每项与该类型单独请求时信封上的两个字段完全相等
     * （6 为模型类型，本接口不接受 type=6，该项只能在此取）；单类型模式为 null。
     * 响应信封与原 qx-api 一致（success/status/data/msg/...）。
     */
    @GetMapping("/station-monitor")
    public ExternalVO.StationMonitor stationMonitor(@RequestParam(required = false) String type,
                                                    @RequestParam(required = false) String name) {
        return externalService.stationMonitor(type, name);
    }

    /**
     * 站点数量统计（大屏「站点数」卡片，纯统计不返回明细，避免为取一个数字拉全量列表）。
     * <p>type：可选（与 station-monitor 的 type 同为可选）。不传/空白 = 全量统计；
     * 传则只统计该类型（1..8 / #N# / 语义串），非法值返回 400。
     * <p>data.total = 档案表全部行数（含 epjutj 为空的管理所/单位记录，比 station-monitor 全部模式行数多；
     * 「全部」页签点位数请取 station-monitor 的 totalCount）；data.typedTotal = 可进入 station-monitor 列表的站点并集
     * （#1#/#2#/#3#/#4#/#5#/#7#/#8# 七类按站去重，与各类型列表按 id 去重结果一致；#6# 模型类型不计入）；
     * data.byType = 按类型计数（键 1..8，含 #6#，多类型站重复计）；其中 type_1 与 station-monitor?type=1
     * 同口径（不含闸站 #4#），闸站单计于 type_4。
     * <p>响应信封与 /external/* 其它接口一致（success/status/data/...）。
     */
    @GetMapping("/station-count")
    public ExternalVO.StationCount stationCount(@RequestParam(required = false) String type) {
        return externalService.stationCount(type);
    }

    /**
     * 站点下拉选项（轻量：仅 id/名称/编号，供告警筛选等下拉框一次拉取）。
     * <p>type：可选。不传 = 全类型并集（与 station-count 的 typedTotal 同口径，按站去重）；
     * 传则只返回该类型站点（1..8 / #N# / 语义串，type=1 与列表同口径排除闸站 #4#），非法值 400。
     * <p>响应信封与 /external/* 其它接口一致，data 为站点数组：[{id, name, stcd}]。
     */
    @GetMapping("/station-options")
    public ExternalVO.StationOptions stationOptions(@RequestParam(required = false) String type) {
        return externalService.stationOptions(type);
    }
}
