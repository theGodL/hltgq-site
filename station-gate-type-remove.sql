-- 闸门监测类型剔除：含 #4# 但实体不是闸门的站点，移除闸门类型
-- 判定依据：会不会有闸前/闸后水位。闸门表无有效 up_z/down_z，且实体为涵/隧洞/支渠/斗渠/水电站/滚水坝/渡槽
-- 预计影响行数：14
-- 说明：按 | 拆分后仅移除 #4#，其余编码与原顺序保持不变；WHERE 带原值匹配，重复执行不会叠加
-- 执行前建议先备份：CREATE TABLE bak_station_epjutj_rm_20261026 AS SELECT id, epjutj FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef";
-- 注意：放空隧洞、滚水坝移除后监测类型为空串，它们本身无任何监测数据

BEGIN;
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = 'hsU0N8laxwlHix5hv5T' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '' WHERE id = 'S2EUn2JBwnoBUxqIP3g' AND epjutj = '#4#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = '1Du6aaJuAir8XVKq23b' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = '0EpV9bB2TEAk29VARBI' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#5#' WHERE id = 'LifKcqiYKDLmzbFk6lF' AND epjutj = '#4#|#5#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '' WHERE id = 'u79fCNK9Cjzf1dcMyCX' AND epjutj = '#4#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#6#' WHERE id = 'lD7pQ5D3Kbap7HfRcmM' AND epjutj = '#4#|#6#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = 'Ysoc8DsW0rqPuJ1emPf' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#1#' WHERE id = '555AILkuquRAhkVZQp6' AND epjutj = '#1#|#4#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = 'cYmSXBiav8BFWkDrlo4' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = 'jjpn5e1lHItlKTCHhYR' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = 'xUOk9BhcPoha8YVR8rF' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#3#' WHERE id = '59otmefmphCrR5Ul5dz' AND epjutj = '#4#|#3#';
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#1#' WHERE id = '27z1HHL8ZtibxuaZw24' AND epjutj = '#1#|#4#';
COMMIT;

-- 校验：以下应返回 0 行（含 #4#、站名不含闸、且闸门表无有效上下游水位 → 说明还有漏剔）；
-- 荞麦岭泄洪河虽站名不含闸，但有 2720 条闸前/闸后水位数据，属于闸门，不在漏剔范围
-- SELECT s.id, s.zzkaec, s.epjutj FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
-- WHERE s.epjutj LIKE '%#4#%' AND s.zzkaec NOT LIKE '%闸%'
--   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_gate g WHERE g.site = s.id
--                   AND ((g.up_z IS NOT NULL AND g.up_z NOT IN (-999,-9991))
--                     OR (g.down_z IS NOT NULL AND g.down_z NOT IN (-999,-9991))));

-- 回滚（如需要，整段执行）
-- BEGIN;
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = 'hsU0N8laxwlHix5hv5T' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#' WHERE id = 'S2EUn2JBwnoBUxqIP3g' AND epjutj = '';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = '1Du6aaJuAir8XVKq23b' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = '0EpV9bB2TEAk29VARBI' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#5#' WHERE id = 'LifKcqiYKDLmzbFk6lF' AND epjutj = '#5#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#' WHERE id = 'u79fCNK9Cjzf1dcMyCX' AND epjutj = '';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#6#' WHERE id = 'lD7pQ5D3Kbap7HfRcmM' AND epjutj = '#6#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = 'Ysoc8DsW0rqPuJ1emPf' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#1#|#4#' WHERE id = '555AILkuquRAhkVZQp6' AND epjutj = '#1#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = 'cYmSXBiav8BFWkDrlo4' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = 'jjpn5e1lHItlKTCHhYR' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = 'xUOk9BhcPoha8YVR8rF' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#4#|#3#' WHERE id = '59otmefmphCrR5Ul5dz' AND epjutj = '#3#';
-- UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" SET epjutj = '#1#|#4#' WHERE id = '27z1HHL8ZtibxuaZw24' AND epjutj = '#1#';
-- COMMIT;
