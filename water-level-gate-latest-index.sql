-- 水位监测分页接口（GET /irrigation-water-level/page）闸站分支性能索引
-- 背景：闸站分支按「站点表驱动 + 每站 LATERAL 取最新一条有效闸前水位 up_z」取数，
--       没有 (site, tm DESC) 索引时，有闸前水位数据的站点会退化为逆序扫 gate 全表
--       （34 万行实测约 0.28s/站），站点一多即变慢。
-- 当前生产数据：监测类型含水位 #1# 的站点均无闸门表数据（该分支经 EXISTS 剪枝后不执行），
--       本索引用于后续配置「闸站水位」站点后的性能保障；建好后该分支每站仅一次索引点查。
-- 适用：KingbaseES v8（PostgreSQL 语法），schema qixiao-apaas
-- 幂等：IF NOT EXISTS；CONCURRENTLY 在线建索引（不锁写，需在事务块外单独执行）
-- 规模：约 34 万行，索引约 15 MB，预计数秒完成
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_gate_site_tm_latest
    ON "qixiao-apaas".t_auto_hltgq_water_gate USING btree (site, tm DESC);

-- 若目标库不支持 CONCURRENTLY，改用（低峰期执行，会短暂阻塞该表写入）：
-- CREATE INDEX IF NOT EXISTS idx_gate_site_tm_latest
--     ON "qixiao-apaas".t_auto_hltgq_water_gate USING btree (site, tm DESC);

-- 回滚：
-- DROP INDEX IF EXISTS "qixiao-apaas".idx_gate_site_tm_latest;
