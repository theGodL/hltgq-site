# 数据统计页站点口径调整需求（给 hltgq-mq）

> 提出日期：2026-09-23；数据依据：当日全库只读实测（探针 SQL 见第 6 章）
> 涉及改动：**仅 hltgq-mq**（`/api/report/arrival-stats`、`/api/report/arrival-detail`、`/api/report/miss-detail`、日汇总表 `t_auto_hltgq_water_stats_daily`）
> hltgq-site 零改动（`/data-statistics/*` 网关继续 1:1 透传 mq 的 `data` 段）；前端 `data-statistics.html` 零改动（KPI 取 `stationTotal`、列表取 `noReportSites/missedSites.length`，`stcd` 已有 `|| '-'` 容错）

---

## 1. 背景与目标

业主核对时发现：数据统计页「监测站点总数」与站点/设备台账对不上（页面约 137，站点档案 368、设备台账 351），需要解释"数去哪了"。

业务定稿口径（2026-09-23）：

1. **统计站点全集改为"站点档案中有设备档案关联的站"（实测 313）**，不再只统计有测站编码且有报文流的遥测站；
2. **无采集数据的站（视频站等）按设备在线/离线判定是否报到**：设备在线 = 已报到，全部设备离线 = 未报到；缺测同规则；
3. **无设备、无采集数据的站（实测 55 个）全链路不展示**（既不进总数，也不进未到报/缺测列表），避免拉低到报率；
4. **采集站（有采集数据的遥测站）的到报/缺测判定算法一律不变**，保证采集数据的统计结果准确性不受影响。

---

## 2. 现状实测（2026-09-23，全库只读）

站点档案全量 368，按「是否在现有采集统计口径内 × 是否有设备档案」四象限，数字闭合：

| 类别 | 站数 | 现有接口是否统计 | 说明 |
|---|:--:|:--:|---|
| ① 采集统计口径内（有 stcd 且有设备/本月流水，或 MQTT 闸站） | **137** | ✅（现有 `stationTotal`） | 全部有设备档案（口径内无设备站 = 0） |
| ② 口径外但有设备档案 | **176** | ❌ 本次要新增纳入 | 175 视频站 + 1 其他；176 站共 176 台设备（1 站 1 台）：**139 在线 / 37 离线** |
| ③ 口径外且无设备档案 | **55** | ❌ **全链路不展示** | 10 站无类型 + 45 站有其他类型标签；无采集数据、无法判定 |
| 合计 | **368** | — | 137 + 176 + 55 = 368 |

**新 `stationTotal` = 137 + 176 = 313**（= 站点档案中有设备档案关联的站；与"设备台账 351 台分布在 313 个站"口径自洽）。

设备在线判定沿用既有统一表达式（与 `/dashboard/overview`、`/network-device/summary` 同源）：

```sql
COALESCE(NULLIF(d.status, ''), s.zebpsu) IN ('#1#', '#1', '1')
-- d = "qixiao-apaas"."t_auto_hltgq_water_device"（设备台账，mq 入库时维护 status）
-- s = "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"（站点档案，zebpsu 为站点状态；by s.id = d.site）
```

---

## 3. 目标口径

### 3.1 统计站点全集（313）

```
新全集 = 现有统计口径站（保留，不减少）
       ∪ 站点档案中存在设备档案关联（EXISTS device WHERE device.site = site.id）的站
排除：测试站（站名含"测试"或 stcd 以 9999 开头）、stats.exclude-stcd-prefix 配置的站
```

- 站点数由台账驱动，`stationTotal` 必须**始终等于** `arrival-detail` 行数（现有自洽约束不变）；
- 第 2 章 ③ 类（无设备站）在任何接口中**都不出现**；
- 判定站类型时不看 `epjutj`（视频站只是"无采集数据站"的主要构成，实测 175/176），只看"是否在采集口径内" + "是否有设备档案"。

### 3.2 报到判定

| 站类型 | 判定方式 | 备注 |
|---|---|---|
| **采集站**（原口径内，137） | **完全沿用现有算法**：雨量站 4h 窗 / RabbitMQ 站 1h 窗 / MQTT 闸站 10min 窗；只统计当日已结束完整窗 | 一个窗都不许变 |
| **无采集数据站**（② 类，176） | 该站设备**任一在线 = 已报到；全部设备离线 = 未报到**（1 站 1 台时即按其状态） | 当日判定 1 次，记 1 个"判定单位" |

- 两类的**应报单位数**：采集站 = 当日已结束完整应报窗数；无采集站 = 1（每站 1 个单位）；
- 已在线状态读取时刻：接口实时查询时取**设备表当前状态**（与设备监控页同源，同一时刻取数一致）。

### 3.3 缺测判定

| 站类型 | 判定方式 |
|---|---|
| **采集站** | **完全沿用现有算法**（雨量 4h / RabbitMQ 1h / MQTT 30min 窗；有报文但数据无效入库也算缺测） |
| **无采集数据站** | 该站设备**全部离线 = 缺测**（记 1 个缺测单位）；全部在线 = 不产生缺测 |

- 无采集数据站不产生"连续缺测时段"以外的数据层判断，`miss-detail` 为其输出**每站 1 行**（保证 KPI 与明细自洽，见 3.5）：

```json
{ "siteId": "...", "siteName": "某视频站", "stcd": null,
  "startTm": "2026-09-23 00:00:00", "endTm": "2026-09-23 <当前时刻>",
  "missMinutes": <当日已过分钟数>, "dataTypes": ["设备状态"], "status": "缺测中" }
```

- `dataTypes` 新增取值 `"设备状态"`（前端已按数组拼接展示，无需改动）；设备恢复在线后状态按"已恢复"或行消失（建议：与采集站口径统一，离线中=「缺测中」）。

### 3.4 率的计算（新增"判定单位"概念）

```
今日到报率 todayArrivalRate =
  (Σ采集站到报窗 + Σ无采集站在线站数) ÷ (Σ采集站应报窗 + Σ无采集站数) × 100

今日缺测率 todayMissRate =
  (Σ采集站缺测窗 + Σ无采集站离线站数) ÷ (Σ采集站应测窗 + Σ无采集站数) × 100

月均/区间平均 monthAvgArrivalRate = 逐日到报率的算术平均（每日按上式，见第 5 章）
```

- 两个分母仍互相独立（到报用应报窗、缺测用应测窗），保持现有"两者不互为补数"的说明；
- **采集站部分的分子分母数字必须与改造前逐位相同**（本次只做加法，不动采集站窗数）；
- 结果保留 2 位小数（与现有一致）；分母为 0 时按现有约定（0 或 null，维持现状即可）。

### 3.5 自洽约束（业主核对会逐条对）

1. `stationTotal` == `arrival-detail` 行数 == （现有口径站 + 有设备站）；
2. `noReportSites` ∪ `missedSites` 覆盖全部"设备全离线"的无采集站；`missedSites` 中每站都能在 `miss-detail` 找到对应行；
3. 无设备站的 `siteId` 在四个接口中均不出现；
4. KPI 四项（stationTotal / 两个到报率 / 缺测率 / 两个列表长度）可用返回数据复算，误差仅在 ±0.01 舍入范围。

---

## 4. 接口改动明细

### 4.1 `GET /api/report/arrival-stats`

| 字段 | 改动 |
|---|---|
| `stationTotal` | 改为新全集站数（实测 313）；= `arrival-detail` 行数 |
| `todayArrivalRate` / `monthAvgArrivalRate` | 按 3.4 新公式（含无采集站判定单位） |
| `todayMissRate` | 按 3.4 新公式 |
| `noReportSites[]` | 追加"无采集站且设备全离线"的站；元素结构不变 `{siteId, siteName, stcd}`，**`stcd` 允许为 `null`**（视频站无测站编码，前端显示 `-`） |
| `missedSites[]` | 同上追加；元素结构不变 `{siteId, siteName, stcd, lastValidTm, missedWindows, missRate}`，无采集站建议：`lastValidTm=null`、`missedWindows=1`、`missRate=100.0` |
| `statStartDate` | 不变 |

**可选增强（向后兼容，建议加）**：新增 `videoSiteTotal` / `videoSiteOnline` / `videoSiteOffline`（无采集站口径单列），便于业主分别核对"采集到报"与"设备在线"，也便于本次改造做回归对比。

### 4.2 `GET /api/report/arrival-detail`

- 行数 = `stationTotal`（实测 313）；新增无采集站行：

```json
{ "siteId": "...", "siteName": "某视频站", "stcd": null,
  "msgType": "设备状态",     // 新增取值：区别于「分钟报」「整点报」
  "expected": 1, "arrived": 1, "missed": 0, "arrivalRate": 100.0 }
```

（设备离线时 `arrived=0`、`missed=1`、`arrivalRate=0`）

- 采集站行字段**完全不变**。

### 4.3 `GET /api/report/miss-detail`

- 追加无采集站离线行（结构见 3.3），`dataTypes=["设备状态"]`；
- 采集站缺测段**完全不变**（`dataTypes` 仍为 水位/雨量/流量/闸门开度/墒情）；
- 31 天区间上限不变。

### 4.4 日汇总表 `t_auto_hltgq_water_stats_daily`

- 每日 00:05 生成昨日快照时，**同时写入无采集站的判定行**：复用"站点行"结构（`stcd = 站点 id`），按单位语义写入 `expected=1 / arrived=0|1 / missed=1|0`；
- 聚合时与采集站行直接 Sum（口径已统一为"单位数"，无需区分行类型）；
- 快照未建/缺列时保持现有降级行为（历史日按无数据 + WARN 日志）。

### 4.5 区间模式（2026-09-10 已上线的 startDate/endDate）

- 区间到报率/缺测率 = 区间累加分子分母（同 3.4），无采集站逐日按当日快照累加；
- 字段名与自洽约束不变；`miss-detail` 的 31 天限制不变。

---

## 5. 历史日与月均的处理（重要实现约束）

设备表 `status` 只有**当前值**，历史日的无采集站在线状态**无法直接回溯**。建议：

1. **每日快照（推荐）**：日汇总表写入"昨日 00:05 读到的设备状态"作为昨日判定值（`daily 00:05` 那一刻读到的是昨日最后已知状态，物理上是昨日结束时的状态，可接受且是唯一可行方式）；
2. 上线前历史日：无快照 → 按 0 单位处理（或按当日仅采集站口径，并在接口返回中保持不变，由前端展示）。**建议统一按"该日无无采集站数据"处理，不做推算**，避免业主看到假数据；
3. 若业务后续要求严格精确：可加设备状态变更历史表（本次不做，先按 1 落地）。

> 说明：本条只影响"月均到报率/区间到报率"里无采集站的那部分；采集站部分不受影响。

---

## 6. 数据来源与参考 SQL（2026-09-23 实测，仅 SELECT）

```sql
-- ① 新统计全集：站点档案中有设备档案关联的站（实测 313）
SELECT count(*) FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
WHERE EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_device" d WHERE d.site = s.id);

-- ② 需新增纳入的站（现有口径外、有设备）实测 176，含设备在线分布
SELECT count(DISTINCT d.site) AS sites,
       count(DISTINCT d.site) FILTER (WHERE COALESCE(NULLIF(d.status,''), s.zebpsu) IN ('#1#','#1','1')) AS sites_any_online
FROM "qixiao-apaas"."t_auto_hltgq_water_device" d
LEFT JOIN "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s ON s.id = d.site
WHERE d.site NOT IN ( /* 现有统计口径的站点 id 集合 */ );
-- 实测：sites=176、sites_any_online=139（其余 37 站设备全离线）

-- ③ 四象限核对（③ 类 55 站必须四接口均不出现）
--    现有口径站 137 / 口径外有设备 176 / 口径外无设备 55 / 合计 368
```

字段字典（改动涉及）：

| 表 | 列 | 说明 |
|---|---|---|
| `t_auto_hltgq_5nw74_vnqqef`（站点档案） | `id` / `zzkaec` 站名 / `iofhpi` stcd / `epjutj` 类型 / `zebpsu` 站点状态 | 站点全集与回退状态来源 |
| `t_auto_hltgq_water_device`（设备台账） | `id` / `site` 站点 id / `name` / `type` / `status` 设备状态 | 是否纳入 + 在线判定来源 |

---

## 7. 验收清单（mq 自测 + 联调回归）

1. `stationTotal` == `arrival-detail` 行数 == 有设备档案的站数（当日实测 313，SQL 第 6 章 ① 可复算）；
2. **采集站回归（最关键）**：改造前后同一时刻，采集站（137 站）的 Σ应报窗 / Σ到报窗 / Σ应测窗 / Σ缺测窗 四个数字**逐位相同**；
3. 无设备站（55 个 `siteId`）在 `arrival-stats` / `arrival-detail` / `miss-detail` / `collect-stats` 中均不出现；
4. 设备全离线的无采集站同时出现在 `noReportSites` 与 `missedSites`，且在 `miss-detail` 有对应行（实测 37 站）；
5. 到报率可复算：(Σ采集站到报窗 + 无采集站在线数) ÷ (Σ采集站应报窗 + 无采集站数) == `todayArrivalRate`（±0.01）；
6. 区间查询（昨日~今日）走日汇总表时，无采集站行已写入且聚合结果与逐日相加一致；
7. site 网关 `GET /hltgq-site/data-statistics/arrival-stats` 返回新值（site 无改动，透传即可）；前端页面「监测站点总数」显示 313、未到报/缺测列表含视频站（`stcd` 列显示 `-`）；
8. 跨日边界：0 点后今日系列重置、`noReportSites` 为当日全量未报列表（含无采集站当日判定），与现有跨日行为一致。

---

## 8. 本次明确不改的部分

- 采集站窗数算法（4h / 1h / 10min 到报窗，4h / 1h / 30min 缺测窗）与"只算已结束完整窗"规则；
- `collect-stats`（仍 5 行，视频行由 `hltgq-device` 的 `video-collect` 提供，不由 mq 统计）；
- `service-status`、`publish-stats`（site 本地）语义；
- 测试站剔除、"参与站点可选排除"（`stats.exclude-stcd-prefix`）、水质不参与缺测判定；
- `arrival-detail` / `miss-detail` 的采集站行字段结构与前端映射。

---

## 9. 文档与前端同步项

| 项 | 说明 |
|---|---|
| 《数据统计.md》（4.1 / 4.2 / 4.3 样例、3 章口径、8 章验收数据） | mq 实施后同步更新：stationTotal 样例、无采集站行示例、两个率的公式说明 |
| 前端 `data-statistics.html` | **无需改动**（KPI 取 `stationTotal`；`r.stcd \|\| '-'` 已容错空 stcd） |
| 《灌区接口文档》《智能决策接口》 | 无需改动（chapter 24 / 第 1 章只涉及大屏与网络设备监控，本轮已同步） |

---

## 10. 实施状态（2026-09-23 回填）

| 项 | 状态 |
|---|---|
| mq 实现 | ✅ 已改 `ArrivalStatsService.java`（未提交、待部署）：新全集 + `inCollect` 标志、无采集站判定单位（今日/区间/月均三路径）、`arrival-detail` 加 `msgType="设备状态"` 行、`miss-detail` 加 `["设备状态"]` 段、新增 `videoSiteTotal/Online/Offline` 字段 |
| 日汇总表 | ✅ 复用站点行结构写入判定单位；`StatsDailyTask` 共用 `computeDaySnapshot` 无需改动。库上核对（2026-09-23）：09-09~09-22 历史日汇总行已存在（09-22 站点行 137），`hasDaily` 会跳过回填 → 历史日按 0 单位，符合第 5 章 |
| 实测差异 | stationTotal=314（本文档正文测算 313）：139 采集站 + 175 无采集站；差异=+2 当日新接入渠道站、-1 测试站剔除（每日波动正常） |
| site 侧 | ✅ 零改动（网关纯透传，`videoSite*` 自动带出）；《数据统计.md》3/4.1/4.2/4.3/4.4/8 章已同步 |
| 待办 | mq 侧改动已由 mq 确认为完成（2026-09-23）；**部署后**按第 7 章 8 条验收清单回归（重点：采集站四窗数逐位相同、stationTotal==arrival-detail 行数）。site 侧无需改动，但页面取值经 `StatsCacheService` 缓存：**今日口径 TTL 20 分钟、纯历史区间 60 分钟**（或清 Redis key 立即刷新） |
