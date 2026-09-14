# 数据统计接口性能优化改造清单（MQ 侧交付）

> 交付对象：hltgq-mq 团队
> 背景：数据统计大屏 5 个接口在冷缓存/并发加载场景下全部约 8s 返回，体验差，目标压到 3s 以内（理想 <1s）。
> 约束：hltgq-site 侧不修改 mq 代码，以下改动项由 mq 团队各自实施；site 侧已完成项见 §7。

## 1. 现象

页面加载时并行请求 5 个接口（今日区间，例 `startDate=2026-09-11&endDate=2026-09-11`，走区间版路径）：

| 接口 | mq 实现 |
| --- | --- |
| /api/report/arrival-stats | getStats(start,end) → loadRangeContext |
| /api/report/arrival-detail | getArrivalDetail(start,end) → loadRangeContext |
| /api/report/service-status | getServiceStatus(start,end) → loadRangeContext |
| /api/report/collect-stats | getCollectStats(start,end) → loadRangeContext |
| /api/report/miss-detail | getMissDetail(start,end) → 逐日 computeDaySnapshot |

实测：5 个请求全部约 8s；缓存热时（60s 内重复查询）约 15~50ms。即"慢"发生在**冷缓存 + 并发**的首屏加载场景。

## 2. 根因（基于 ArrivalStatsService 当前代码）

### 2.1 全局锁导致并发串行（"全部 8s"的直接原因）

- `loadRangeContext` 缓存未命中后使用 `synchronized (this)` 双检锁（全局单锁）。4 个接口共用同一缓存 key：首个线程构建约 8s，其余线程全程阻塞等锁，拿锁后命中缓存返回——从客户端看**4 个请求都耗时约 8s**。
- `getContext()`（今日无参路径）用的是**同一把** `synchronized (this)`：区间请求与今日请求互相阻塞。
- `computeDaySnapshot` 无防并发构建（无锁双检）：miss-detail 与 buildRangeContext 内的调用会**并行重复构建同一日快照**，数据库 IO 争用、双份成本。

### 2.2 冷路径大查询（8s 的成本根源）

单次冷构建的重查询如下（若相关表缺少 §4 索引，库侧即为全表/大范围扫描；索引现状需先用 §4「索引现状检查」核实）：

- `loadDayContext`：msg_info 单日流水全量拉取 + 5 张业务表 `loadValidWindowsRange` 全量拉取
- `loadRangeLastValid`：5 张业务表 + msg_info 各一条 `WHERE tm 区间 ... GROUP BY site` 聚合
- `countStoredRows`：9 张表 `COUNT(*) WHERE 时间区间`
- `querySites`：EXISTS 关联 device / msg_info(site+tm) / gate
- `buildRangeContext` 历史日段：stats_daily `WHERE stats_date 区间 GROUP BY stcd`

涉及表与建议索引见 §4。

### 2.3 无预热

统计缓存 TTL 为 60s（今日快照同）。页面间隔超过 60s 的首次访问必然触发冷构建，且无任何后台预热。application.properties 中也未配置预热项。

## 3. 改造清单（按优先级）

### A. 建索引（治本，收益最大）

执行 §4 脚本（低峰期，幂等）。预期：冷路径单次构建从约 8s 降至 <1s。

### B. 锁拆分（消除并发等待）

1. `loadRangeContext`：`synchronized (this)` 改为**按缓存 key 分锁**（`ConcurrentHashMap<String, Object>` + 双检），不同区间互不阻塞：

```java
private final Map<String, Object> rangeLocks = new ConcurrentHashMap<>();

private RangeContext loadRangeContext(LocalDate start, LocalDate end) {
    String key = start + "|" + end;
    // ... 锁外双检命中直接返回 ...
    Object lock = rangeLocks.computeIfAbsent(key, k -> new Object());
    synchronized (lock) {
        // ... 锁内二次双检 ...
        RangeContext rc = buildRangeContext(start, end);
        rangeCache.put(key, rc);
        rangeTimes.put(key, new long[]{System.currentTimeMillis()});
        return rc;
    }
}
```

2. `getContext()`：独立锁对象（如 `private final Object ctxLock = new Object();`），与区间缓存互不阻塞。

3. `computeDaySnapshot`：按日期防并发构建（`Map<LocalDate, Object>` 分锁 + 双检），避免 miss-detail 与区间构建重复全量计算。

### C. 预热（保障首访体验）

- 服务启动后（ApplicationReadyEvent 或 @PostConstruct 异步线程）预热一次：`computeDaySnapshot(today)` + `loadRangeContext(today, today)`。
- 定时预热（建议每分钟，TTL 60s 留提前量）：

```java
@Scheduled(cron = "0 * * * * ?") // 每分钟
public void warmup() {
    try {
        LocalDate today = LocalDate.now();
        computeDaySnapshot(today);          // 刷新今日快照缓存
        loadRangeContext(today, today);     // 刷新 "今日|今日" 区间缓存
    } catch (Exception e) {
        log.warn("统计预热失败: {}", e.getMessage());
    }
}
```

- 建议新增开关配置（properties 目前无）：

```properties
# 统计后台预热：定时刷新今日快照与今日区间缓存，保障首访命中热缓存
stats.warmup-enabled=true
stats.warmup-cron=0 * * * * ?
```

### D. 可选（收益次之，非必须）

- `countStoredRows` 9 表 COUNT：索引后可保留；如需进一步，历史段已走 stats_daily，今日实时段可加短缓存。
- `loadDayContext` 中 msg_info 行 Java 侧聚合：单日约 2.6 万行，索引后可暂不改；如要极致可改 SQL 聚合（按 site + 窗桶 GROUP BY）。
- 明细接口后端分页：**不建议**。计算耗时不随分页变化，且 site 端 CSV 导出依赖全量数据；当前 site 已按前端分页每页 10 条展示，无需 mq 改造。

## 4. 索引脚本（KingbaseES / PG 兼容，低峰期执行）

```sql
-- ============================================================
-- 数据统计接口索引脚本（幂等，可重复执行；执行后自动 ANALYZE 相关表）
-- schema: "qixiao-apaas"
-- 说明：先跑「索引现状检查」，确认缺什么再整体执行 DO 块部分。
-- ============================================================

-- 索引现状检查（先执行这条，查看现有索引）
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'qixiao-apaas'
  AND tablename LIKE 't_auto_hltgq_water_%'
ORDER BY tablename, indexname;

-- ① msg_info：单日/区间流水扫描、(msg='rainInfo') 聚合、EXISTS(site,tm)、COUNT(*)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_msg_tm_site_msg') THEN
    CREATE INDEX idx_hltgq_msg_tm_site_msg
      ON "qixiao-apaas".t_auto_hltgq_water_msg_info (tm, site, msg);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_msg_site_tm') THEN
    CREATE INDEX idx_hltgq_msg_site_tm
      ON "qixiao-apaas".t_auto_hltgq_water_msg_info (site, tm);
  END IF;
END $$;

-- ② 五张业务表：(tm, site) 覆盖"有效窗扫描/MAX(tm) GROUP BY/COUNT(*)"
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_river_tm_site') THEN
    CREATE INDEX idx_hltgq_river_tm_site ON "qixiao-apaas".t_auto_hltgq_water_river_info (tm, site);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_rain_tm_site') THEN
    CREATE INDEX idx_hltgq_rain_tm_site ON "qixiao-apaas".t_auto_hltgq_water_rain_info (tm, site);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_wt_tm_site') THEN
    CREATE INDEX idx_hltgq_wt_tm_site ON "qixiao-apaas".t_auto_hltgq_water_wt_nfo (tm, site);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_soil_tm_site') THEN
    CREATE INDEX idx_hltgq_soil_tm_site ON "qixiao-apaas".t_auto_hltgq_water_soil_data (tm, site);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_gate_tm_site') THEN
    CREATE INDEX idx_hltgq_gate_tm_site ON "qixiao-apaas".t_auto_hltgq_water_gate (tm, site);
  END IF;
  -- gate 额外需要 (site) 前缀索引：querySites 的 EXISTS(SELECT 1 FROM gate WHERE site = s.id)
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_gate_site_tm') THEN
    CREATE INDEX idx_hltgq_gate_site_tm ON "qixiao-apaas".t_auto_hltgq_water_gate (site, tm);
  END IF;
END $$;

-- ③ 入库行数 COUNT 用辅助表
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_vol_tm') THEN
    CREATE INDEX idx_hltgq_vol_tm ON "qixiao-apaas".t_auto_hltgq_water_vol_info (tm);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_sluice_tm') THEN
    CREATE INDEX idx_hltgq_sluice_tm ON "qixiao-apaas".t_auto_hltgq_water_sluice_discharge (tm);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_nmisp_tm') THEN
    CREATE INDEX idx_hltgq_nmisp_tm ON "qixiao-apaas".t_auto_hltgq_water_nmisp_info (tm);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_pcp_spt') THEN
    CREATE INDEX idx_hltgq_pcp_spt ON "qixiao-apaas".t_auto_hltgq_water_pcp_info (spt);
  END IF;
END $$;

-- ④ 日汇总表：stats_date 区间聚合（若已有以 stats_date 为第一列的索引/主键则自动跳过）
DO $$
DECLARE
  has_idx boolean;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM pg_index i
    JOIN pg_class c ON c.oid = i.indrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'qixiao-apaas'
      AND c.relname = 't_auto_hltgq_water_stats_daily'
      AND pg_get_indexdef(i.indexrelid) LIKE '%(stats_date%'
  ) INTO has_idx;
  IF NOT has_idx THEN
    CREATE INDEX idx_hltgq_stats_daily_date
      ON "qixiao-apaas".t_auto_hltgq_water_stats_daily (stats_date);
  END IF;
END $$;

-- ⑤ device：querySites 的 EXISTS(SELECT 1 FROM device WHERE site = s.id)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='qixiao-apaas' AND indexname='idx_hltgq_device_site') THEN
    CREATE INDEX idx_hltgq_device_site ON "qixiao-apaas".t_auto_hltgq_water_device (site);
  END IF;
END $$;

-- ⑥ 更新统计信息（必须执行，否则优化器可能仍不走索引）
ANALYZE "qixiao-apaas".t_auto_hltgq_water_msg_info;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_river_info;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_rain_info;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_wt_nfo;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_soil_data;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_gate;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_vol_info;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_sluice_discharge;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_nmisp_info;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_pcp_info;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_stats_daily;
ANALYZE "qixiao-apaas".t_auto_hltgq_water_device;
```

备注：如需在线无锁建索引，可将 DO 块内语句替换为 `CREATE INDEX CONCURRENTLY ...` 逐条执行（该语法不能放在 DO 块/事务中，重复执行会报"已存在"，可忽略）。

## 5. 验收方法

1. 执行 §4 脚本（含 ANALYZE），部署锁拆分与预热代码，重启 mq；
2. 静默 >90s 让缓存过期，然后**并行**发起 5 个接口（今日区间）。建议内网直连 mq 测试（不经 site 会话拦截）：

```bash
# 内网直连 mq（替换 <mq-host>），5 个接口并行发起
for e in arrival-stats arrival-detail miss-detail service-status collect-stats; do
  curl -s -o /dev/null -w "$e -> %{time_total}s\n" \
    "http://<mq-host>:8081/api/report/$e?startDate=2026-09-11&endDate=2026-09-11" &
done
wait
```

   - 期望：全部 <1s（最差 ≤3s），不再出现"全部 8s"；
   - 从 site 页面验证时需先登录（site 有会话拦截），看浏览器 DevTools Network 中 5 个请求的实际耗时即可（页面已并行发出，见 static/data-statistics.html 的 loadAll）。
3. 回归：任意历史区间（例 30 天）、缺测明细 31 天上限、页面正常查询/导出。

## 6. 附注

- 本清单 5 个接口均不经过 hltgq-device，device 侧无需联动改动。
- 索引脚本未写入 mq 仓库（site 侧约定不修改其他项目代码），脚本执行位置由 mq 团队自行决定。
- mq 工作区另有一笔未提交改动（MonitorDataService.java，核心指标时效宽限学习），属"水位/雨量核心指标"功能线，与本次统计性能无关，请按原计划处置。

## 7. site 侧已完成项

- 数据统计大屏"站点到报明细/缺测明细"分页改为每页 10 条（static/data-statistics.html，PAGE_SIZE=10）。
- site 为纯转发网关（DataStatisticsController → MqStatsClient，read-timeout 15s），5 个接口慢的根因均在 mq 侧计算，site 侧无需再改。
