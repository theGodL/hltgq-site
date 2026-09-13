# MQ 项目性能提升（数据统计接口 8s+ 问题）

> 提出日期：2026-09-13 ｜ 提出方：hltgq-site（数据统计页面 data-statistics.html）
> 对接方：hltgq-mq（统计计算侧）
> 现象：页面初始化即并行加载 7 个接口，其中 5 个转 mq，实测均 ≥ 8s，业主反馈"未达平台标准"

## 一、问题现象

页面：`http://220.179.1.110:8081/hltgq-site/data-statistics.html`
现场日期：2026-09-13（今日区间，`startDate=endDate=2026-09-13`）

| # | site 接口（页面调用） | 转发目标（mq） | 用途 |
|---|----------------------|----------------|------|
| 1 | /data-statistics/arrival-stats | /api/report/arrival-stats | KPI 卡片 |
| 2 | /data-statistics/arrival-detail | /api/report/arrival-detail | 站点到报明细 |
| 3 | /data-statistics/miss-detail | /api/report/miss-detail | 缺测明细 |
| 4 | /data-statistics/service-status | /api/report/service-status | 服务状态 |
| 5 | /data-statistics/collect-stats | /api/report/collect-stats | 采集状态统计 |

5 个接口实测均 ≥ 8s。

## 二、链路与定位（site 侧已核实）

```
浏览器 → openresty(220.179.1.110:8081) → hltgq-site(纯透传) → hltgq-mq(/api/report/*) → KingbaseES(10.68.18.5)
```

- site 侧 `DataStatisticsController` 对这 5 个接口**零计算、零本地查询**，原样转发 mq，只取响应 `data` 段；
- site 转发客户端 `MqStatsClient` 超时为 connect 5s / read 15s、不重试，**8s+ 是 mq 真实响应时间**；
- 页面用 `Promise.allSettled` 并行 7 个请求，整页耗时 = **最慢接口**（site 侧已改为"先到先渲染"缓解白屏，见第七节）。

**结论：瓶颈在 mq 侧统计查询，不在 site 转发层。**

## 三、mq 侧代码现状复核

### 2026-09-11 已提交

| 提交 | 时间 | 内容 |
|------|------|------|
| 9c50745 | 2026-09-11 13:54 | 数据统计-性能优化：SWR 缓存 + 分桶锁 + 每分钟预热 + 日汇总表 |
| dc90596 | 2026-09-11 14:03 | 性能优化-索引（sql/cleanup_abnormal_data.sql 后半段） |

已具备的机制（`ArrivalStatsService` / `StatsDailyTask`）：

1. **三层 SWR 内存缓存**：ctxEntry（60s）、daySnapCache（今日 60s / 历史 10min）、rangeCache（60s）；过期先返回旧值、后台线程池异步重建，前台请求不再同步打库；
2. **1024 桶分锁**（DAY_LOCKS / RANGE_LOCKS），替代全局锁，并发请求互不阻塞；
3. **每分钟定时预热**：`stats.warmup-enabled=true`、`stats.warmup-cron=0 * * * * ?`，预热内容 = 今日快照 + 今日区间（`forceRebuildRange(today,today)`）+ 统计上下文。**本次现场测的"今日区间"恰好在预热覆盖范围内**；
4. **日汇总表** `t_auto_hltgq_water_stats_daily`：区间查询的历史日直接读汇总表，与天数解耦（每日 00:05 生成昨日 + 启动自动回填）；
5. **站点查询月缓存**（querySites，60s TTL）。

### 2026-09-13 复核修订（mq 侧工作区改动，编译通过）

现场复盘后 mq 侧复核发现两处"请求线程内同步打库"盲区，已修复：

6. **querySites 补 SWR**：原普通 60s 缓存过期即同步执行含 3 处 EXISTS（device / msg_info / gate）的查询——它是 4 个区间接口与 miss-detail 的公共前置依赖，索引缺失或 DB 慢时撞上过期窗口直接放大为接口 8s。现改为"过期返回旧值 + rebuildPool 后台异步重建"（SITES_LOCKS 1024 桶锁双检 + sitesRebuilding 防重 + CacheEntry holder，与其它三个缓存对齐）；
7. **service-status 无参版去掉请求内 8 表 COUNT**：`getServiceStatus()` 复用 `computeDaySnapshot(today).storedRows`（今日 0 点 ~ 次日 0 点，口径一致；快照自带 SWR + 每分钟预热），请求线程不再打库。

> 由此，**今日口径**（现场 `startDate=endDate=2026-09-13`）下请求线程已无同步打库路径——即使索引缺失，请求也只会拿到旧值 + 后台重建。剩余同步构建仅存在于：冷启动首个请求（预热启动前）、历史日快照过期（设计如此，供 00:05 汇总任务精确构建，依赖索引）。

## 四、关键判断

如果线上运行的是最新版本（含 2026-09-11 优化与 09-13 复核修订）、索引已建、预热正常，那么"今日区间"请求应命中热缓存，**毫秒~1s 级返回，不应 8s+**。

> 因此优先核查"**部署状态 + 索引 + 预热**"三件事，而不是再改代码。

## 五、行动清单（按序执行）

### 第 1 步：确认线上跑的是新包（日志指纹）

```bash
docker ps | grep -i mq
docker logs <mq容器名> 2>&1 | grep -E "统计参与站点|日汇总回填|统计起始日|日汇总已生成|统计启动预热失败|统计上下文后台重建失败" | tail -n 30
```

命中任一 → 新包在跑；**无任何输出 → 线上仍是旧包，先部署最新版本（含 2026-09-11 优化与 09-13 复核修复；注意构建产物须包含 mq 工作区改动，最高优先级）**。

### 第 2 步：确认索引已建

```sql
SELECT indexname FROM pg_indexes
WHERE schemaname = 'qixiao-apaas'
  AND indexname IN ('idx_hltgq_nmisp_tm','idx_hltgq_pcp_spt','idx_hltgq_device_site',
                    'idx_hltgq_river_tm_site','idx_hltgq_rain_tm_site','idx_hltgq_soil_tm_site')
ORDER BY indexname;
```

不足 6 条 → 执行 `sql/cleanup_abnormal_data.sql` 中「① 必须建 / ② 建议建 / ③ ANALYZE」三段（含 ANALYZE 7 张表，统计信息直接影响执行计划）。

### 第 3 步：内网直连实测（绕过 site，冷/热各一轮）

在内网机器执行（PowerShell）：

```powershell
$base = 'http://10.68.18.4:8081/api/report'
$q = '?startDate=2026-09-13&endDate=2026-09-13'
$paths = @('arrival-stats','arrival-detail','miss-detail','collect-stats','service-status')

Write-Host '===== 第一轮（冷）====='
$paths | ForEach-Object {
  $u = "$base/$($_)$q"
  $t = Measure-Command { Invoke-WebRequest -UseBasicParsing $u | Out-Null }
  '{0,7:N2}s  {1}' -f $t.TotalSeconds, $_
}

Write-Host '===== 第二轮（热，紧接重跑）====='
$paths | ForEach-Object {
  $u = "$base/$($_)$q"
  $t = Measure-Command { Invoke-WebRequest -UseBasicParsing $u | Out-Null }
  '{0,7:N2}s  {1}' -f $t.TotalSeconds, $_
}
```

**建议验收目标**：单接口冷态 ≤ 3s、热态（1 分钟内重复）≤ 1s；页面整页（7 接口并行）≤ 3s。

## 六、判读表

| 第 1 步日志 | 第 3 步现象 | 结论 | 处理 |
|------------|------------|------|------|
| 无指纹 | - | 线上是旧包 | **部署最新版本（含 09-13 复核修复）**，再复测 |
| 有指纹 | 热态仍 ≥ 5s | 缓存/预热未生效，或索引缺失 | 查日志"统计启动预热失败 / 统计上下文后台重建失败 / 区间查询日汇总表失败"；执行第 2 步 |
| 有指纹 | 冷 8s、热快 | 预热未跑或未覆盖当前时点 | 核查 `stats.warmup-enabled`、调度线程（`spring.task.scheduling.pool.size`）是否被占用 |
| 有指纹 | 冷热都慢 | DB 实例/链路层 | 与 site 侧另一条线（gq-rainfall 查询慢）合并排查：KingbaseES 实例负载、连接数、磁盘 |

## 七、site 侧配套改动（2026-09-13 已实施）

1. **MqStatsClient 耗时日志**：每次转发输出一条 info，格式
   `mq stats /api/report/arrival-stats range=2026-09-13~2026-09-13 cost=8123ms`
   —— 联调期可直接从 site 日志核对 mq 各接口耗时（含失败场景打 error 并带耗时）；
2. **data-statistics.html 先到先渲染**：各接口独立返回即渲染对应区块，页面不再等最慢接口，最慢 8s 时其余区块已先行展示（治标，配合 mq 侧治本）。

## 八、其他已知慢点（核查时留意）

> 说明：以下查询已不在日常请求线程的直连路径上（由预热 / SWR 后台异步重建执行），索引缺失时主要表现为"后台重建变慢"而不阻塞请求；仅冷启动首请求与多天历史区间（历史日快照过期）仍可能同步触发（见第三节末段）。

- 今日区间 `miss-detail`：逐日构造缺测段 + 5 表有效窗扫描，依赖 ② 建议建索引；
- `querySites`：档案表上 `EXISTS(device …)` 子查询，依赖 `idx_hltgq_device_site`（① 必须建）；过期窗口由 SWR 后台重建兜住，仅首次冷启动同步；
- `countStoredRows`：8 张表 COUNT，仅在快照后台构建时执行；索引缺失时拖慢后台重建，不阻塞请求；
- 若 mq 与 gq-rainfall 共用同一 DB 实例：实例整体负载高时会放大所有统计耗时，需一并观察。
