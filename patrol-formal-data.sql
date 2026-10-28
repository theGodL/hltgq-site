-- ============================================================
-- 巡检运维管理 正式业务数据脚本
--   覆盖菜单：巡检计划 / 巡检记录 / 问题记录 / 工单管理
--   适用数据库：KingbaseES V8（PostgreSQL 语法），schema：qixiao-apaas
--   目标：清除测试数据，写入覆盖「计划→巡检→上报问题→转工单/直接处理→处理→验收关闭」闭环的正式业务数据
-- ------------------------------------------------------------
-- 一、清理范围（只删测试数据；统一按 id 白名单 + ptl 前缀双重限定，可重复执行）
--   1. 巡检记录照片 4 张（4 条测试记录）
--   2. 问题照片 17 张（28 条测试问题下 12 张 + 上传未绑定 5 张）
--   3. 问题记录 28 条（AQ-202609xx / QST-17xxxxxxxxx 等测试标题）
--   4. 工单附件：7 条人工测试工单下无附件（当前 0 个）；SYSTEM 工单的 1 个附件保留
--   5. 工单 7 条人工测试工单（带 created_by <> 'SYSTEM' 兜底，绝不触碰自动告警工单）
--   6. 巡检记录 16 条（含 4 条空白占位草稿）
--   7. 计划巡查范围关系 45 行（12 行属测试计划 + 33 行历史孤儿）
--   8. 计划巡检人员旧多选关系 13 行（历史孤儿，模块已改为主表 "user" 单选）
--   9. 巡检计划 10 条
--   保留：全部存量 SYSTEM 自动告警工单及其附件（数量随告警持续增长，以执行前现场为准）；
--         站点/设备/部门/人员档案一律不动
-- ------------------------------------------------------------
-- 二、写入内容
--   巡检计划 9 条（年度 6 / 月度 3；草稿 1 / 进行中 6 / 已完成 1 / 已取消 1）
--   计划巡查范围 51 行
--   巡检记录 31 条（已提交 30 / 草稿 1；正常 19 / 异常 4 / 隐患 4 / 缺陷 3 / 故障 1）
--   问题记录 14 条（待处理 2 / 处理中 2 / 已转工单 2 / 已关闭 6 / 已作废 2）
--   工单 12 条（待指派 2 / 处理中 3 / 已关闭 6 / 已取消 1）
-- ------------------------------------------------------------
-- 三、口径说明
--   1. 时间线：2026-01 计划建账 → 2026-04/07 年度计划季度巡检 → 2026-09/10 集中巡检与整改
--      → 2026-10-27 最近一次巡检，与存量业务时间连续
--   2. 编号形态与程序生成一致：计划 XCJH+13 位毫秒；问题 QST-+13 位毫秒；工单 GD+yyyyMMddHHmmss+3 位流水
--   3. 审计列 created_by / updated_by 存用户 id（与平台一致），不使用姓名
--   4. 业务时间列均为 timestamp（不带时区），脚本按服务器本地时区直接写入，不做时区偏移
--   5. 本脚本不生成现场照片（记录照片 / 问题照片留空）：记录提交要求至少 1 张照片，属既定取舍
--   6. 关联一致性已核对：记录站点 ∈ 计划范围；记录/问题/工单设备 ∈ 站点；问题与工单双向引用；
--      问题关闭时间对齐工单处理完成时间；工单状态流转符合「待指派→处理中→已关闭」条件更新口径
-- ============================================================

BEGIN;

-- ============================================================
-- 0. 清理（顺序：照片 → 问题 → 工单附件 → 工单 → 记录 → 关系 → 计划）
-- ============================================================

-- 0.1 巡检记录现场照片
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_record_photo"
WHERE record_id IN ('BkhdRqJz71ymsTdd90m','eGg5ypAc93TYQVmzrwBWVm','GKRLJXCksnMY2skWZeaiR9','hmm9yj9RS2atqZFsuyJ','ICNN31R86TuWHslrLgzpgP','MacE4gXNLbsrhfKgEEgB8e','nswNYbtE9avp4VHJ2r4htk','oLyCTNcE4ECMASoWgGm','pmkjj8VWDQxRNg86E8tYZF','QG1brzIOka4Itd9lhgfK22','sDYakxpWtIM49HmEKWTDa2','Smysb14Wk7QwL3aRz26','Uho9vnAFhpoPoJmwVaBHID','vaMQay19YrVDXV9e6eW0nv','Y56XDqYQfdWzM5IgxOvpWE','yd4pqTjqapDZapTk3kiJtw')
   OR record_id LIKE 'ptl%';

-- 0.2 巡检问题照片（含上传后未绑定的行：issue_id 为空）
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_issue_photo"
WHERE issue_id IS NULL
   OR issue_id IN ('0CASXqdLPvCTvJglAq1','44U0UxHkcRLegvEEEcCJTm','4IUDxulH063bBC66NRn','4qNxzki9ZbOZCfj7l45','70AICrxSrN110voX7ZJ','8EjUPsomtlQmBFNqSxW','AthpAylDCfK590staw4','cmx3HllssB5PkwXmUTu','Cwm6EruYGtinBAe13kb','eleDWGpHAP3zcrFdaPV','FuFn4E1Tb5ZCuQPtUDi','g7Lp1cXu1EG38x2SXvK','HNeNYdB0p3i3DYAsykv','IHkmkTj1mANsMqsIe3f','in7lRKdFBUB8bPYbLLU0e8','K08TM0zgJ3l2sZynZ0c','L1kM0K6vpIiskZ3doBp','lfxDSPi9Wsss1JO4GEtqJc','MLWEuIadr2OrHXHvu7O','NyDa3uVsMtnQKYgZuWL','OypmshZUCtdn6ZD65JR','PAa2K2K3e0gyuE1EWEihgv','Pb6qVEwevfKcscOVfvi','SLsn9FoGwADmRljdV51','toEZFvaa53byI0CqNGi','u7IoFJ0kMHErVkZpmHt','xNb8iTrlxMy4jeqi99h','XpBApjyUPKezf7hhVtt')
   OR issue_id LIKE 'ptl%';

-- 0.3 巡检问题记录
DELETE FROM "qixiao-apaas"."t_auto_hltgq_knc3g_bpzjoh"
WHERE id IN ('0CASXqdLPvCTvJglAq1','44U0UxHkcRLegvEEEcCJTm','4IUDxulH063bBC66NRn','4qNxzki9ZbOZCfj7l45','70AICrxSrN110voX7ZJ','8EjUPsomtlQmBFNqSxW','AthpAylDCfK590staw4','cmx3HllssB5PkwXmUTu','Cwm6EruYGtinBAe13kb','eleDWGpHAP3zcrFdaPV','FuFn4E1Tb5ZCuQPtUDi','g7Lp1cXu1EG38x2SXvK','HNeNYdB0p3i3DYAsykv','IHkmkTj1mANsMqsIe3f','in7lRKdFBUB8bPYbLLU0e8','K08TM0zgJ3l2sZynZ0c','L1kM0K6vpIiskZ3doBp','lfxDSPi9Wsss1JO4GEtqJc','MLWEuIadr2OrHXHvu7O','NyDa3uVsMtnQKYgZuWL','OypmshZUCtdn6ZD65JR','PAa2K2K3e0gyuE1EWEihgv','Pb6qVEwevfKcscOVfvi','SLsn9FoGwADmRljdV51','toEZFvaa53byI0CqNGi','u7IoFJ0kMHErVkZpmHt','xNb8iTrlxMy4jeqi99h','XpBApjyUPKezf7hhVtt')
   OR id LIKE 'ptl%';

-- 0.4 工单处理结果附件（仅 7 条人工测试工单）
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_work_order_attachment"
WHERE order_id IN ('5Yndw4XICggfMZEzCvm','a73CYK4wVZIhvHbITSs','edZSmyEyJS15oBv2GdX','GgYsJXzIrKv7xiRvU8I','hE0lLmuBEC6E0dGkoG5','UVhVVvpW0BvkQVNmVly','zCjEQk840wDKOp84te7')
   OR order_id LIKE 'ptl%';

-- 0.5 工单（人工 7 条测试工单；created_by <> 'SYSTEM' 双保险，自动告警工单不会被删）
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_work_order"
WHERE (id IN ('5Yndw4XICggfMZEzCvm','a73CYK4wVZIhvHbITSs','edZSmyEyJS15oBv2GdX','GgYsJXzIrKv7xiRvU8I','hE0lLmuBEC6E0dGkoG5','UVhVVvpW0BvkQVNmVly','zCjEQk840wDKOp84te7')
       OR id LIKE 'ptl%')
  AND created_by <> 'SYSTEM'
  AND alert IS NULL;

-- 0.6 巡检记录（16 条测试记录，含 4 条空白占位草稿）
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_inspection_record"
WHERE id IN ('BkhdRqJz71ymsTdd90m','eGg5ypAc93TYQVmzrwBWVm','GKRLJXCksnMY2skWZeaiR9','hmm9yj9RS2atqZFsuyJ','ICNN31R86TuWHslrLgzpgP','MacE4gXNLbsrhfKgEEgB8e','nswNYbtE9avp4VHJ2r4htk','oLyCTNcE4ECMASoWgGm','pmkjj8VWDQxRNg86E8tYZF','QG1brzIOka4Itd9lhgfK22','sDYakxpWtIM49HmEKWTDa2','Smysb14Wk7QwL3aRz26','Uho9vnAFhpoPoJmwVaBHID','vaMQay19YrVDXV9e6eW0nv','Y56XDqYQfdWzM5IgxOvpWE','yd4pqTjqapDZapTk3kiJtw')
   OR id LIKE 'ptl%';

-- 0.7 计划巡查范围关系（12 行属测试计划 + 33 行历史孤儿计划）
DELETE FROM "qixiao-apaas"."t_auto_hltgq_knc3g_nlbdju_tkwovk_rel"
WHERE biz_id IN ('6Joa4knfy6Ko8SBQ0J9','742qLRqPN4GVSOy6etIAWb','7knGsBbUZ1Yaxriryjf9Do','AydJZiR4lI9vhWqUY50','cu1afjc0MKootNQCKot','Ic1t2WVIgd8fswEylKHcpv','ish8UlZ9SOvMoqHU8G5','JuUHmzYdkJHuVEmFXgl','KeGIncWvHjvWySLZZ7m','KHGn2DpffBUHdBagON5bws','lYmvIFEsp8QW1GStWy9GU6','Ngn22hqjBDTSE1BOs47iZP','Po6DFNpMoCTHUdeULbd','qHak4jiXW9i7U7je7qNriQ','sBrVBPLkppbMzBMVhFoo46','Ub1KgNq0Sf5hNVi0kUl','Urek6bIA5rMyctQaNuR','uwQGiOFk2v3ZzvR1sXV','wkxdUEYWYSjwWOFUEvLoGd','zRxUR2qDOQ0EYRTU2qJ')
   OR biz_id LIKE 'ptl%';

-- 0.8 计划巡检人员旧多选关系（模块已废弃该表；仅清历史孤儿与测试计划行）
DELETE FROM "qixiao-apaas"."t_auto_hltgq_knc3g_nlbdju_user_rel"
WHERE corp_code = 'hltgq'
  AND (biz_id IN ('742qLRqPN4GVSOy6etIAWb','7knGsBbUZ1Yaxriryjf9Do','AydJZiR4lI9vhWqUY50','Ic1t2WVIgd8fswEylKHcpv','KHGn2DpffBUHdBagON5bws','lYmvIFEsp8QW1GStWy9GU6','Ngn22hqjBDTSE1BOs47iZP','qHak4jiXW9i7U7je7qNriQ','sBrVBPLkppbMzBMVhFoo46','wkxdUEYWYSjwWOFUEvLoGd')
       OR biz_id LIKE 'ptl%'
       OR biz_id NOT IN (SELECT p.id FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_schedule" p WHERE p.corp_code = 'hltgq'));

-- 0.9 巡检计划
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_schedule"
WHERE id IN ('742qLRqPN4GVSOy6etIAWb','7knGsBbUZ1Yaxriryjf9Do','AydJZiR4lI9vhWqUY50','Ic1t2WVIgd8fswEylKHcpv','KHGn2DpffBUHdBagON5bws','lYmvIFEsp8QW1GStWy9GU6','Ngn22hqjBDTSE1BOs47iZP','qHak4jiXW9i7U7je7qNriQ','sBrVBPLkppbMzBMVhFoo46','wkxdUEYWYSjwWOFUEvLoGd')
   OR id LIKE 'ptl%';

-- ============================================================
-- 1. 巡检计划 9 条
--   状态：#1# 草稿 / #2# 进行中 / #3# 已完成 / #4# 已取消
--   类型：#1# 年度计划 / #zjgg# 月度计划（年度兼容 #wavn#）
--   "user" = 巡检人员（单选，主表列）；created_by 存创建人用户 id
-- ============================================================
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_patrol_schedule"
("id","corp_code","created_at","created_by","updated_at","updated_by",
 "code","title","content","start_time","end_time","status","xhonqv","user")
VALUES
('ptlplan2026000001','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd',
 'XCJH1767489120000','2026年度渠系工程日常巡检计划',
 '对渠首进水闸、北干渠进水闸、南干渠进水闸、太宿干渠进水闸、太怀干渠进水闸、王家桥节制闸及渠首管理所、渠首水电站开展日常巡查，重点检查闸门启闭、边坡稳定、渠道通水与管理所院内设施运行情况，发现问题当日报送并跟踪闭环。',
 '2026-01-05 00:00:00','2026-12-31 00:00:00','#2#','#1#','fQQQcbAsdOEph0Jrxdw'),
('ptlplan2026000002','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd',
 'XCJH1767490800000','2026年度水工建筑物安全巡检计划',
 '按季度对毕岭节制闸、汪元节制闸、南山寺节制闸、望牛墩节制闸、长岭跌水节制闸、双庙湖节制闸开展水工建筑物安全巡检，重点检查闸墩、胸墙、护坡、消力池及启闭机房结构安全，汛期加密巡查。',
 '2026-01-05 00:00:00','2026-12-31 00:00:00','#2#','#1#','GXzDrMHmZQe9pJW4jAR'),
('ptlplan2026000003','hltgq','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd',
 'XCJH1767492300000','2026年度机电设备与启闭机巡检计划',
 '开展启闭机、闸门、电气控制柜、备用电源及计量监测设备巡检，重点检查开度指示、限位保护、接触器与线路绝缘，确保机电设备完好率不低于 98%，重要设备汛前汛后各专项检查一次。',
 '2026-01-05 00:00:00','2026-12-31 00:00:00','#2#','#1#','t1Vkw1dNpCdmpUcaDVh'),
('ptlplan2026000004','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw',
 'XCJH1787818800000','2026年9月份渠系巡检计划',
 '9 月份按周对渠首进水闸、北干渠进水闸、南干渠进水闸、汪元节制闸、毕岭节制闸、集岭电站进水闸开展巡查，每次巡查填写巡检记录并留存现场照片，异常情况当日上报。',
 '2026-09-01 00:00:00','2026-09-30 00:00:00','#3#','#zjgg#','NoQEHI2sK4TzODmHnsj'),
('ptlplan2026000005','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw',
 'XCJH1790644500000','2026年10月份渠系巡检计划',
 '10 月份渠系巡检按周开展，覆盖渠首、北干、南干、汪元、毕岭、集岭电站各闸，兼顾国庆期间值班巡查与秋灌通水安全，发现缺陷隐患同步录入问题记录。',
 '2026-10-01 00:00:00','2026-10-31 00:00:00','#2#','#zjgg#','NoQEHI2sK4TzODmHnsj'),
('ptlplan2026000006','hltgq','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw',
 'XCJH1792481400000','2026年11月份渠系巡检计划',
 '11 月份渠系巡检以冬灌供水安全为重点，覆盖渠首进水闸、北干渠进水闸、南干渠进水闸、太宿干渠进水闸、太怀干渠进水闸，计划按周巡查，视冬灌配水情况加密。',
 '2026-11-01 00:00:00','2026-11-30 00:00:00','#1#','#zjgg#','GQY12kEkeIihr5Yq6mi'),
('ptlplan2026000007','hltgq','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','2026-09-25 09:25:00','NoQEHI2sK4TzODmHnsj',
 'XCJH1789699200000','2026年国庆节前安全生产专项巡检',
 '国庆节前对各管理所值班值守、消防设施、用电线路及防汛物资储备开展专项检查。因与 9 月份月度计划合并实施，经调度运行科同意，本专项计划取消。',
 '2026-09-25 00:00:00','2026-09-30 00:00:00','#4#','#1#','GQY12kEkeIihr5Yq6mi'),
('ptlplan2026000008','hltgq','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd',
 'XCJH1790748300000','2026年汛后水工建筑物专项巡检',
 '汛后对集岭管理所、毕岭管理所辖区泄洪闸、节制闸，以及袁畈、郝大屋泄洪闸和南山寺节制闸开展专项巡检，重点检查护坡淘刷、闸门止水、启闭机限位及观测设施完好情况，问题一并纳入整改台账。',
 '2026-10-08 00:00:00','2026-10-31 00:00:00','#2#','#1#','t1Vkw1dNpCdmpUcaDVh'),
('ptlplan2026000009','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd',
 'XCJH1790306400000','2026年四季度渠系工程联合巡检计划',
 '工程管理科联合毕岭管理所，对麻塘湖灌区、龙潭湖泄洪闸、渠首水电站、郝大屋泄洪闸、袁畈泄洪闸、长岭跌水节制闸开展四季度联合巡检，重点核查视频监控、闸门远控与机电设备运行状态。',
 '2026-10-01 00:00:00','2026-12-31 00:00:00','#2#','#1#','Lqi7oMF5PCj58alqWrm');

-- ============================================================
-- 2. 计划巡查范围（51 行；nature_order 为站点展示顺序）
-- ============================================================
INSERT INTO "qixiao-apaas"."t_auto_hltgq_knc3g_nlbdju_tkwovk_rel"
("id","corp_code","created_at","created_by","updated_at","updated_by","biz_id","rel_id","nature_order")
VALUES
-- P1 2026年度渠系工程日常巡检计划（8 站）
('ptlrel2026000001','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','CAYQ739MiBWMg9gQvyi',1),
('ptlrel2026000002','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','4Ieu32Stnz8dTD8S8GD',2),
('ptlrel2026000003','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','cOGko9nLfFz6rRAvKvY',3),
('ptlrel2026000004','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','k6V2otO7e4ZmEba7HMD',4),
('ptlrel2026000005','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','zxgbgrx0lUXS44Jklo8',5),
('ptlrel2026000006','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','LCmIfy4WB6lQ3OROQRo',6),
('ptlrel2026000007','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','YiSfpfSjFTep4pnflU2',7),
('ptlrel2026000008','hltgq','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:12:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000001','LifKcqiYKDLmzbFk6lF',8),
-- P2 2026年度水工建筑物安全巡检计划（6 站）
('ptlrel2026000009','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000002','SQcUlflXiUAC9eagJJx',1),
('ptlrel2026000010','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000002','GwDmpPwcfw6sk8C9JtM',2),
('ptlrel2026000011','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000002','dByBR1Xdze8shnRGAw9',3),
('ptlrel2026000012','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000002','KqZvHx9KTMNRrOuxgYo',4),
('ptlrel2026000013','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000002','pJgk6PcRYVqd9kgYsdl',5),
('ptlrel2026000014','hltgq','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 09:40:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000002','mqG9dLawRteYWHgjqFG',6),
-- P3 2026年度机电设备与启闭机巡检计划（5 站）
('ptlrel2026000015','hltgq','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000003','LifKcqiYKDLmzbFk6lF',1),
('ptlrel2026000016','hltgq','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000003','VzoiGl9W3XT7WheoEXV',2),
('ptlrel2026000017','hltgq','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000003','LCmIfy4WB6lQ3OROQRo',3),
('ptlrel2026000018','hltgq','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000003','SQcUlflXiUAC9eagJJx',4),
('ptlrel2026000019','hltgq','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','2026-01-04 10:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000003','GwDmpPwcfw6sk8C9JtM',5),
-- P4 2026年9月份渠系巡检计划（6 站）
('ptlrel2026000020','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000004','CAYQ739MiBWMg9gQvyi',1),
('ptlrel2026000021','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000004','4Ieu32Stnz8dTD8S8GD',2),
('ptlrel2026000022','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000004','cOGko9nLfFz6rRAvKvY',3),
('ptlrel2026000023','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000004','GwDmpPwcfw6sk8C9JtM',4),
('ptlrel2026000024','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000004','SQcUlflXiUAC9eagJJx',5),
('ptlrel2026000025','hltgq','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','2026-08-27 16:20:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000004','VzoiGl9W3XT7WheoEXV',6),
-- P5 2026年10月份渠系巡检计划（6 站）
('ptlrel2026000026','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000005','CAYQ739MiBWMg9gQvyi',1),
('ptlrel2026000027','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000005','4Ieu32Stnz8dTD8S8GD',2),
('ptlrel2026000028','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000005','cOGko9nLfFz6rRAvKvY',3),
('ptlrel2026000029','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000005','GwDmpPwcfw6sk8C9JtM',4),
('ptlrel2026000030','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000005','SQcUlflXiUAC9eagJJx',5),
('ptlrel2026000031','hltgq','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','2026-09-29 09:15:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000005','VzoiGl9W3XT7WheoEXV',6),
-- P6 2026年11月份渠系巡检计划（草稿，5 站）
('ptlrel2026000032','hltgq','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000006','CAYQ739MiBWMg9gQvyi',1),
('ptlrel2026000033','hltgq','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000006','4Ieu32Stnz8dTD8S8GD',2),
('ptlrel2026000034','hltgq','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000006','cOGko9nLfFz6rRAvKvY',3),
('ptlrel2026000035','hltgq','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000006','k6V2otO7e4ZmEba7HMD',4),
('ptlrel2026000036','hltgq','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-20 15:30:00','fQQQcbAsdOEph0Jrxdw','ptlplan2026000006','zxgbgrx0lUXS44Jklo8',5),
-- P7 2026年国庆节前安全生产专项巡检（已取消，4 站）
('ptlrel2026000037','hltgq','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','ptlplan2026000007','YiSfpfSjFTep4pnflU2',1),
('ptlrel2026000038','hltgq','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','ptlplan2026000007','y3HShbAuGKS2OUaQZFg',2),
('ptlrel2026000039','hltgq','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','ptlplan2026000007','hrYBO8MN9AegZehWUd6',3),
('ptlrel2026000040','hltgq','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','2026-09-18 10:40:00','NoQEHI2sK4TzODmHnsj','ptlplan2026000007','JsrIMB8v9oKi0MpT79N',4),
-- P8 2026年汛后水工建筑物专项巡检（5 站）
('ptlrel2026000041','hltgq','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000008','JsrIMB8v9oKi0MpT79N',1),
('ptlrel2026000042','hltgq','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000008','hrYBO8MN9AegZehWUd6',2),
('ptlrel2026000043','hltgq','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000008','dByBR1Xdze8shnRGAw9',3),
('ptlrel2026000044','hltgq','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000008','dcWsH6rGjniFOxg4OR3',4),
('ptlrel2026000045','hltgq','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','2026-09-30 14:05:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000008','PiTASJ0oNB5OHLQTvPd',5),
-- P9 2026年四季度渠系工程联合巡检计划（6 站）
('ptlrel2026000046','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000009','OOH2pADzfwQioCb5qAF',1),
('ptlrel2026000047','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000009','RjuglJWdGBNFgja6g5D',2),
('ptlrel2026000048','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000009','LifKcqiYKDLmzbFk6lF',3),
('ptlrel2026000049','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000009','PiTASJ0oNB5OHLQTvPd',4),
('ptlrel2026000050','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000009','dcWsH6rGjniFOxg4OR3',5),
('ptlrel2026000051','hltgq','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','2026-09-25 11:20:00','pbJdAWiiG3Ce6uEnXYd','ptlplan2026000009','pJgk6PcRYVqd9kgYsdl',6);

-- ============================================================
-- 3. 巡检记录 31 条（已提交 30 / 草稿 1）
--   result：#2# 正常 / #3# 异常 / #4# 隐患 / #5# 缺陷 / #6# 故障
--   status：#1# 草稿 / #2# 已提交；szeedc = 巡检成本（元）
--   站点均在所属计划巡查范围内；"user" 与计划巡检人员一致（H5 列表按当前登录人过滤）
-- ============================================================
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_inspection_record"
("id","corp_code","created_at","created_by","updated_at","updated_by",
 "time","patrol_schedule","site","_d_10497_device","user","result","content","status","szeedc")
VALUES
-- P1 2026年度渠系工程日常巡检计划 / 徐爱民（6 条）
('ptlrec2026000001','hltgq','2026-09-08 09:20:00','fQQQcbAsdOEph0Jrxdw','2026-09-08 09:20:00','fQQQcbAsdOEph0Jrxdw',
 '2026-09-08 09:20:00','ptlplan2026000001','CAYQ739MiBWMg9gQvyi','CAYQ739MiBWMg9gQvy3','fQQQcbAsdOEph0Jrxdw','#2#',
 '渠首进水闸三孔闸门启闭机运行正常，上下游水位平稳，启闭机房无渗漏，渠道通水正常。','#2#',NULL),
('ptlrec2026000002','hltgq','2026-09-16 09:35:00','fQQQcbAsdOEph0Jrxdw','2026-09-16 09:35:00','fQQQcbAsdOEph0Jrxdw',
 '2026-09-16 09:35:00','ptlplan2026000001','4Ieu32Stnz8dTD8S8GD','MYMFQIuOwZDXNlzcC3QT','fQQQcbAsdOEph0Jrxdw','#2#',
 '北干渠进水闸三孔闸门启闭灵活，止水良好，启闭机行程一致，运行无异常声响。','#2#',NULL),
('ptlrec2026000003','hltgq','2026-09-24 09:10:00','fQQQcbAsdOEph0Jrxdw','2026-09-24 09:10:00','fQQQcbAsdOEph0Jrxdw',
 '2026-09-24 09:10:00','ptlplan2026000001','cOGko9nLfFz6rRAvKvY','TcAShqLLK4LdznXA3xKi','fQQQcbAsdOEph0Jrxdw','#2#',
 '南干渠进水闸运行工况正常，流量计数据与渠道水位相符，闸室无渗水。','#2#',NULL),
('ptlrec2026000004','hltgq','2026-10-09 09:40:00','fQQQcbAsdOEph0Jrxdw','2026-10-09 09:42:00','fQQQcbAsdOEph0Jrxdw',
 '2026-10-09 09:40:00','ptlplan2026000001','k6V2otO7e4ZmEba7HMD','kQnPCB5nv7v0eI9O6INC','fQQQcbAsdOEph0Jrxdw','#3#',
 '闸室上游左岸翼墙勾缝大面积脱落，局部砂浆缺失，雨后渗水明显，已现场拍照并上报问题。','#2#',80),
('ptlrec2026000005','hltgq','2026-10-16 15:00:00','fQQQcbAsdOEph0Jrxdw','2026-10-16 15:05:00','fQQQcbAsdOEph0Jrxdw',
 '2026-10-16 15:00:00','ptlplan2026000001','zxgbgrx0lUXS44Jklo8','Qt9s3NccbfYHv4VyfI72','fQQQcbAsdOEph0Jrxdw','#4#',
 '太怀进水闸上游左岸护坡顶部堆放秸秆约 20 平方米，占压巡查通道，存在火灾隐患。','#2#',60),
('ptlrec2026000006','hltgq','2026-10-23 09:30:00','fQQQcbAsdOEph0Jrxdw','2026-10-23 09:30:00','fQQQcbAsdOEph0Jrxdw',
 '2026-10-23 09:30:00','ptlplan2026000001','YiSfpfSjFTep4pnflU2','6d4oOVPnqs9wWOm2BTV7','fQQQcbAsdOEph0Jrxdw','#2#',
 '管理所院内用电线路、消防器材及冲沙闸启闭设备检查正常，值班记录完整。','#2#',NULL),
-- P2 2026年度水工建筑物安全巡检计划 / 杨同海（2 条）
('ptlrec2026000029','hltgq','2026-04-16 09:30:00','GXzDrMHmZQe9pJW4jAR','2026-04-16 09:30:00','GXzDrMHmZQe9pJW4jAR',
 '2026-04-16 09:30:00','ptlplan2026000002','SQcUlflXiUAC9eagJJx','Ry3ro4h1T9DBL3y8yBST','GXzDrMHmZQe9pJW4jAR','#2#',
 '毕岭节制闸闸墩、胸墙及护坡结构完好，启闭机运行正常，汛前检查未发现新增裂缝与渗漏。','#2#',NULL),
('ptlrec2026000030','hltgq','2026-07-21 09:40:00','GXzDrMHmZQe9pJW4jAR','2026-07-21 09:40:00','GXzDrMHmZQe9pJW4jAR',
 '2026-07-21 09:40:00','ptlplan2026000002','dByBR1Xdze8shnRGAw9','dByBR1Xdze8shnRGAw5','GXzDrMHmZQe9pJW4jAR','#2#',
 '汛期加密巡查，南山寺节制闸五孔闸门启闭正常，消力池、护坡及观测设施完好，上下游水位平稳。','#2#',NULL),
-- P3 2026年度机电设备与启闭机巡检计划 / 刘学文（5 条）
('ptlrec2026000007','hltgq','2026-09-03 09:10:00','t1Vkw1dNpCdmpUcaDVh','2026-09-03 09:10:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-09-03 09:10:00','ptlplan2026000003','VzoiGl9W3XT7WheoEXV','z9l8FOWCbmWGYuZLA1uG','t1Vkw1dNpCdmpUcaDVh','#2#',
 '集岭电站进水闸三孔闸门启闭机运行正常，开度指示与限位保护动作可靠。','#2#',NULL),
('ptlrec2026000008','hltgq','2026-09-12 09:15:00','t1Vkw1dNpCdmpUcaDVh','2026-09-12 09:22:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-09-12 09:15:00','ptlplan2026000003','SQcUlflXiUAC9eagJJx','Ry3ro4h1T9DBL3y8yBST','t1Vkw1dNpCdmpUcaDVh','#5#',
 '1#启闭机开度仪显示值与闸门实际开度偏差约 8cm，闸门精确调度受影响，已上报问题。','#2#',NULL),
('ptlrec2026000009','hltgq','2026-09-19 09:40:00','t1Vkw1dNpCdmpUcaDVh','2026-09-19 09:40:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-09-19 09:40:00','ptlplan2026000003','GwDmpPwcfw6sk8C9JtM','JFXAF9QHF2vNWt9LckN5','t1Vkw1dNpCdmpUcaDVh','#2#',
 '汪元节制闸启闭机、控制柜及备用电源运行正常，闸门启闭试验两次均正常。','#2#',NULL),
('ptlrec2026000010','hltgq','2026-09-26 09:55:00','t1Vkw1dNpCdmpUcaDVh','2026-09-26 10:05:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-09-26 09:55:00','ptlplan2026000003','LCmIfy4WB6lQ3OROQRo','cci8DBTwGXBpp0ANd1qD','t1Vkw1dNpCdmpUcaDVh','#6#',
 '1#电动启闭机接触器烧蚀，启闭时伴有异响，已停用该孔闸门并切换备用电源，现场上报问题。','#2#',120),
('ptlrec2026000011','hltgq','2026-10-15 10:20:00','t1Vkw1dNpCdmpUcaDVh','2026-10-15 10:20:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-10-15 10:20:00','ptlplan2026000003','LifKcqiYKDLmzbFk6lF','ChcG9o7gXJ08pAlkyWVC','t1Vkw1dNpCdmpUcaDVh','#2#',
 '电站厂房排水泵、配电柜及闸门监控运行正常，厂房无渗漏。','#2#',NULL),
-- P4 2026年9月份渠系巡检计划 / 翟向晨（6 条）
('ptlrec2026000012','hltgq','2026-09-02 09:30:00','NoQEHI2sK4TzODmHnsj','2026-09-02 09:35:00','NoQEHI2sK4TzODmHnsj',
 '2026-09-02 09:30:00','ptlplan2026000004','CAYQ739MiBWMg9gQvyi','46kDkhaJmKMTsizTrnSc','NoQEHI2sK4TzODmHnsj','#4#',
 '上游拦污栅栅条锈蚀，局部焊点开焊，栅前漂浮物清理不及时，已上报问题。','#2#',50),
('ptlrec2026000013','hltgq','2026-09-05 09:15:00','NoQEHI2sK4TzODmHnsj','2026-09-05 09:15:00','NoQEHI2sK4TzODmHnsj',
 '2026-09-05 09:15:00','ptlplan2026000004','4Ieu32Stnz8dTD8S8GD','bzpFqIcKyPPwXU5Zx3n3','NoQEHI2sK4TzODmHnsj','#2#',
 '北干渠进水闸 2# 闸门止水良好，启闭机运行平稳，渠道水位正常。','#2#',NULL),
('ptlrec2026000014','hltgq','2026-09-09 10:00:00','NoQEHI2sK4TzODmHnsj','2026-09-09 10:05:00','NoQEHI2sK4TzODmHnsj',
 '2026-09-09 10:00:00','ptlplan2026000004','cOGko9nLfFz6rRAvKvY','pXFUN6DMCMgZH57DovvU','NoQEHI2sK4TzODmHnsj','#4#',
 '南干渠进水闸流量计探头淤堵，测值偏小约 15%，需清淤校核，已上报问题。','#2#',50),
('ptlrec2026000015','hltgq','2026-09-15 10:30:00','NoQEHI2sK4TzODmHnsj','2026-09-15 10:35:00','NoQEHI2sK4TzODmHnsj',
 '2026-09-15 10:30:00','ptlplan2026000004','GwDmpPwcfw6sk8C9JtM','vO75rcB9kUCnNaPCkZ1M','NoQEHI2sK4TzODmHnsj','#3#',
 '下游右岸巡查便道局部塌陷，长约 3 米、深约 0.4 米，影响巡查通行安全。','#2#',NULL),
('ptlrec2026000016','hltgq','2026-09-21 09:20:00','NoQEHI2sK4TzODmHnsj','2026-09-21 09:20:00','NoQEHI2sK4TzODmHnsj',
 '2026-09-21 09:20:00','ptlplan2026000004','SQcUlflXiUAC9eagJJx','evj89epX9lnnkPjTMIoI','NoQEHI2sK4TzODmHnsj','#2#',
 '毕岭节制闸 2# 闸门启闭正常，闸室无渗漏，观测设施完好。','#2#',NULL),
('ptlrec2026000017','hltgq','2026-09-28 09:45:00','NoQEHI2sK4TzODmHnsj','2026-09-28 09:45:00','NoQEHI2sK4TzODmHnsj',
 '2026-09-28 09:45:00','ptlplan2026000004','VzoiGl9W3XT7WheoEXV','Hb1IBesIQ35O6hTWa86P','NoQEHI2sK4TzODmHnsj','#2#',
 '集岭电站进水闸 2# 闸门运行正常，上下游水位差在设计范围内。','#2#',NULL),
-- P5 2026年10月份渠系巡检计划 / 翟向晨（8 条，末条为草稿）
('ptlrec2026000018','hltgq','2026-10-08 09:10:00','NoQEHI2sK4TzODmHnsj','2026-10-08 09:10:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-08 09:10:00','ptlplan2026000005','CAYQ739MiBWMg9gQvyi','CAYQ739MiBWMg9gQvy2','NoQEHI2sK4TzODmHnsj','#2#',
 '国庆假期后首轮巡查，2# 闸门、启闭机及监控设施运行正常，无新增缺陷。','#2#',NULL),
('ptlrec2026000019','hltgq','2026-10-12 09:30:00','NoQEHI2sK4TzODmHnsj','2026-10-12 09:30:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-12 09:30:00','ptlplan2026000005','4Ieu32Stnz8dTD8S8GD','WOSATFAwh7trgH1GttIM','NoQEHI2sK4TzODmHnsj','#2#',
 '北干渠进水闸 3# 闸门启闭灵活，止水橡皮完好，关闭后无漏水。','#2#',NULL),
('ptlrec2026000020','hltgq','2026-10-14 10:10:00','NoQEHI2sK4TzODmHnsj','2026-10-14 10:45:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-14 10:10:00','ptlplan2026000005','cOGko9nLfFz6rRAvKvY','bMpR5Au2kMUJalnhxYdM','NoQEHI2sK4TzODmHnsj','#3#',
 '南干渠进水闸 2# 闸门底止水橡皮局部脱落，闸门关闭后渗漏明显，启闭时伴随异响，已上报两条问题。','#2#',90),
('ptlrec2026000021','hltgq','2026-10-17 10:50:00','NoQEHI2sK4TzODmHnsj','2026-10-17 11:00:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-17 10:50:00','ptlplan2026000005','GwDmpPwcfw6sk8C9JtM','SNucIdYvbIjh1WJfxx28','NoQEHI2sK4TzODmHnsj','#4#',
 '汪元节制闸上游水面漂浮垃圾聚集约 15 平方米，部分进入拦污栅前，需组织打捞。','#2#',NULL),
('ptlrec2026000022','hltgq','2026-10-19 15:20:00','NoQEHI2sK4TzODmHnsj','2026-10-19 15:30:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-19 15:20:00','ptlplan2026000005','SQcUlflXiUAC9eagJJx','mhDWj11mPHY8ldv84oGW','NoQEHI2sK4TzODmHnsj','#5#',
 '毕岭节制闸流量计太阳能供电板积尘严重，蓄电池电压偏低，监测数据频繁掉线。','#2#',NULL),
('ptlrec2026000023','hltgq','2026-10-22 09:35:00','NoQEHI2sK4TzODmHnsj','2026-10-22 09:35:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-22 09:35:00','ptlplan2026000005','VzoiGl9W3XT7WheoEXV','2LZnJSoPJP7xaavZDCPa','NoQEHI2sK4TzODmHnsj','#2#',
 '集岭电站进水闸 3# 闸门及启闭机运行正常，电站发电引水稳定。','#2#',NULL),
('ptlrec2026000024','hltgq','2026-10-24 09:20:00','NoQEHI2sK4TzODmHnsj','2026-10-24 09:20:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-24 09:20:00','ptlplan2026000005','CAYQ739MiBWMg9gQvyi','HJSSfPeaFbrcaV9U3MhE','NoQEHI2sK4TzODmHnsj','#2#',
 '渠首进水闸室内电气柜、启闭机及消防器材检查正常，闸室无渗水。','#2#',NULL),
('ptlrec2026000025','hltgq','2026-10-27 09:50:00','NoQEHI2sK4TzODmHnsj','2026-10-27 09:58:00','NoQEHI2sK4TzODmHnsj',
 '2026-10-27 09:50:00','ptlplan2026000005','4Ieu32Stnz8dTD8S8GD','MYMFQIuOwZDXNlzcC3QT','NoQEHI2sK4TzODmHnsj','#2#',
 '北干渠进水闸 1# 闸门启闭机运行正常，止水良好，现场情况已记录，待补充照片后提交。','#1#',NULL),
-- P8 2026年汛后水工建筑物专项巡检 / 刘学文（3 条）
('ptlrec2026000026','hltgq','2026-10-12 10:20:00','t1Vkw1dNpCdmpUcaDVh','2026-10-12 10:30:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-10-12 10:20:00','ptlplan2026000008','JsrIMB8v9oKi0MpT79N','HqhH5OWfe5Hl1TAVRUwI','t1Vkw1dNpCdmpUcaDVh','#5#',
 '安居龙泄洪闸启闭机上限位开关固定螺栓松动，动作不灵敏，存在闸门过卷风险。','#2#',150),
('ptlrec2026000027','hltgq','2026-10-16 14:50:00','t1Vkw1dNpCdmpUcaDVh','2026-10-16 15:20:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-10-16 14:50:00','ptlplan2026000008','hrYBO8MN9AegZehWUd6','cDQ6dsljSoGsWDPnKorM','t1Vkw1dNpCdmpUcaDVh','#3#',
 '段垅节制闸上游左岸护坡被水流淘刷，长约 6 米，坡脚局部掏空；拦污栅两根栅条受漂浮物撞击变形。','#2#',200),
('ptlrec2026000028','hltgq','2026-10-22 09:40:00','t1Vkw1dNpCdmpUcaDVh','2026-10-22 09:40:00','t1Vkw1dNpCdmpUcaDVh',
 '2026-10-22 09:40:00','ptlplan2026000008','dByBR1Xdze8shnRGAw9','dByBR1Xdze8shnRGAw5','t1Vkw1dNpCdmpUcaDVh','#2#',
 '南山寺节制闸五孔闸门启闭机、限位保护及观测设施运行正常，汛后未发现新增缺陷。','#2#',180),
-- P9 2026年四季度渠系工程联合巡检计划 / 何汪洋（1 条）
('ptlrec2026000031','hltgq','2026-10-14 09:50:00','Lqi7oMF5PCj58alqWrm','2026-10-14 09:50:00','Lqi7oMF5PCj58alqWrm',
 '2026-10-14 09:50:00','ptlplan2026000009','OOH2pADzfwQioCb5qAF','D5ETmpXhZQcjQns7Y0o5','Lqi7oMF5PCj58alqWrm','#2#',
 '麻塘湖灌区泄洪闸前院及周边摄像机在线率、图像质量核查正常，视频监控与调度中心联通稳定。','#2#',NULL);
-- ============================================================
-- 4. 工单 12 条（待指派 2 / 处理中 3 / 已关闭 6 / 已取消 1）
--   类型：#1# 日常巡检 / #yjje# 设备维护 / #hxqm# 设备故障抢修 / #zopb# 水工建筑物维护
--         #kjua# 渠道及附属设施养护 / #enak# 计量与监测维护 / #bgtg# 信息化与通信维护
--         #pfqj# 安全隐患整改 / #uvxb# 应急处置
--   流转：待指派 #1#（org/user/time 为空）→ 指派 #2#（写负责部门/处理人员/要求完成时间）
--         → 填写处理结果（result + pyxcen）→ 提交验收 #3#；待指派可直接取消 #4#
--   注：自动告警工单（created_by = SYSTEM、alert 非空）不在本脚本范围内
-- ============================================================
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_work_order"
("id","corp_code","created_at","created_by","updated_at","updated_by",
 "code","title","qjulvf","content","site","device","org","user","time","result","status","pyxcen")
VALUES
-- 待指派（2 条）
('ptlwo2026000001','hltgq','2026-10-26 09:15:12','GXzDrMHmZQe9pJW4jAR','2026-10-26 09:15:12','GXzDrMHmZQe9pJW4jAR',
 'GD20261026091512001','汪元节制闸启闭机房照明线路改造','#yjje#',
 '汪元节制闸启闭机房照明线路老化，部分灯具不亮，需整体更换线路与灯具，同步加装漏电保护装置，改造后满足机房照度要求。',
 'GwDmpPwcfw6sk8C9JtM',NULL,NULL,NULL,NULL,NULL,'#1#',NULL),
('ptlwo2026000002','hltgq','2026-10-27 10:05:04','fQQQcbAsdOEph0Jrxdw','2026-10-27 10:05:04','fQQQcbAsdOEph0Jrxdw',
 'GD20261027100504001','北干渠进水闸上游左岸边坡滑塌应急处置','#uvxb#',
 '受连续降雨影响，北干渠进水闸上游左岸边坡局部滑塌约 15 立方米，已设置警戒线与警示牌，需组织应急处置、清理滑塌体并恢复护坡。',
 '4Ieu32Stnz8dTD8S8GD','W82ZeXXvJ6UWtNmzj03u',NULL,NULL,NULL,NULL,'#1#',NULL),
-- 处理中（3 条）
('ptlwo2026000003','hltgq','2026-10-20 09:05:03','xICEjugQanQ5PVtaAhS','2026-10-20 09:30:00','xICEjugQanQ5PVtaAhS',
 'GD20261020090503001','渠首水电站厂房渗漏排水泵检修','#hxqm#',
 '厂房集水井排水泵启动后出水压力不足，运行电流偏高，需检修泵体与叶轮，必要时更换密封件并复核自动启停水位。',
 'LifKcqiYKDLmzbFk6lF','ChcG9o7gXJ08pAlkyWVC','IGMDmIAlrqXCXYBxUAm','xICEjugQanQ5PVtaAhS','2026-10-30 18:00:00',NULL,'#2#',NULL),
('ptlwo2026000004','hltgq','2026-09-12 10:35:11','t1Vkw1dNpCdmpUcaDVh','2026-09-23 16:40:00','Lqi7oMF5PCj58alqWrm',
 'GD20260912103511001','毕岭节制闸1#启闭机开度仪校准','#yjje#',
 '1# 启闭机开度仪示值与闸门实际开度偏差约 8cm，需重新标定零点与满量程，并复测 0.5m、1.5m、2.5m 三档示值。',
 'SQcUlflXiUAC9eagJJx','Ry3ro4h1T9DBL3y8yBST','0QpohwXXHVEcWAw5M1o','Lqi7oMF5PCj58alqWrm','2026-09-24 18:00:00',
 '已重新标定开度仪零点与满量程，0.5m、1.5m、2.5m 三档复测示值偏差均小于 1cm，闸门启闭与开度显示一致。','#2#','2026-09-23 16:40:00'),
('ptlwo2026000005','hltgq','2026-09-26 10:50:26','t1Vkw1dNpCdmpUcaDVh','2026-09-27 08:40:00','xVaMWYMWGSQy01QfUju',
 'GD20260926105026001','王家桥节制闸1#电动启闭机接触器更换','#hxqm#',
 '1# 电动启闭机接触器烧蚀，启闭时伴有异响，需更换同型号接触器并检查控制回路绝缘，更换后做三次启闭试验。',
 'LCmIfy4WB6lQ3OROQRo','cci8DBTwGXBpp0ANd1qD','SZexx7Il3TxpjUl2AzG','xVaMWYMWGSQy01QfUju','2026-10-30 18:00:00',NULL,'#2#',NULL),
-- 已关闭（6 条）
('ptlwo2026000006','hltgq','2026-10-15 11:20:14','NoQEHI2sK4TzODmHnsj','2026-10-21 16:10:00','fQQQcbAsdOEph0Jrxdw',
 'GD20261015112014001','南干渠进水闸2#闸门止水橡皮更换','#zopb#',
 '2# 闸门底止水橡皮脱落约 2 米，闸门关闭后渗漏明显，需更换止水橡皮、调整压板并做启闭试验。',
 'cOGko9nLfFz6rRAvKvY','bMpR5Au2kMUJalnhxYdM','SZexx7Il3TxpjUl2AzG','YVwd6FnOgm1IAUmKVUH','2026-10-22 18:00:00',
 '已完成 2# 闸门底止水橡皮更换，压板螺栓全部紧固，闸门关闭后无渗漏，启闭试验三次正常，验收合格。','#3#','2026-10-21 16:10:00'),
('ptlwo2026000007','hltgq','2026-10-19 16:05:19','NoQEHI2sK4TzODmHnsj','2026-10-25 15:30:00','NoQEHI2sK4TzODmHnsj',
 'GD20261019160519001','毕岭节制闸流量计太阳能供电板清洗及蓄电池更换','#enak#',
 '流量计太阳能供电板积尘严重、蓄电池电压偏低，监测数据频繁掉线，需清洗供电板并更换蓄电池，恢复数据连续上传。',
 'SQcUlflXiUAC9eagJJx','mhDWj11mPHY8ldv84oGW','0QpohwXXHVEcWAw5M1o','IakslthlJqtjdEfIy1q','2026-10-26 18:00:00',
 '已清洗太阳能供电板并更换 12V/100Ah 蓄电池，连续 72 小时数据上传正常，蓄电池电压稳定在 13.2V，验收合格。','#3#','2026-10-25 15:30:00'),
('ptlwo2026000008','hltgq','2026-10-12 11:15:12','t1Vkw1dNpCdmpUcaDVh','2026-10-17 10:25:00','t1Vkw1dNpCdmpUcaDVh',
 'GD20261012111512001','安居龙泄洪闸启闭机限位开关紧固','#yjje#',
 '启闭机上限位开关固定螺栓松动，上限位动作不灵敏，存在闸门过卷风险，需紧固螺栓并复核限位行程。',
 'JsrIMB8v9oKi0MpT79N','HqhH5OWfe5Hl1TAVRUwI','COYX8zL4KM7BKSsK56V','XCDaBfhFxEJjdX6OhRp','2026-10-18 18:00:00',
 '已更换限位开关固定螺栓并加装防松垫片，复核上限位行程与闸门全开位置一致，过卷保护试验动作可靠，验收合格。','#3#','2026-10-17 10:25:00'),
('ptlwo2026000009','hltgq','2026-09-11 09:40:11','fQQQcbAsdOEph0Jrxdw','2026-09-24 17:20:00','fQQQcbAsdOEph0Jrxdw',
 'GD20260911094011001','渠首进水闸闸门防腐涂装','#zopb#',
 '渠首进水闸 1# 闸门门叶及支臂局部锈蚀，需除锈至 St2 级后涂刷环氧富锌底漆两道、面漆两道，涂层厚度不小于 200μm。',
 'CAYQ739MiBWMg9gQvyi','CAYQ739MiBWMg9gQvy3','SZexx7Il3TxpjUl2AzG','YVwd6FnOgm1IAUmKVUH','2026-09-25 18:00:00',
 '1# 闸门门叶、支臂及埋件除锈后涂刷环氧富锌底漆两道、面漆两道，防腐面积约 46 平方米，漆膜厚度检测合格，验收通过。','#3#','2026-09-24 17:20:00'),
('ptlwo2026000010','hltgq','2026-10-06 10:20:06','GXzDrMHmZQe9pJW4jAR','2026-10-14 16:45:00','GXzDrMHmZQe9pJW4jAR',
 'GD20261006102006001','汪元管理所院内排水沟清淤','#kjua#',
 '管理所院内排水沟淤积约 30 厘米，雨季排水不畅，需人工配合小型机械清淤，并修复局部塌陷沟壁。',
 'y3HShbAuGKS2OUaQZFg','po78pbyIEKPYHBNN6lba','4MOmAqQiOwpmKPJBqpQ','GQY12kEkeIihr5Yq6mi','2026-10-15 18:00:00',
 '完成院内排水沟清淤 120 米，清出淤泥及杂物约 8 立方米，修复塌陷沟壁 6 米，通水试验排水通畅，验收合格。','#3#','2026-10-14 16:45:00'),
('ptlwo2026000011','hltgq','2026-09-29 09:30:29','t1Vkw1dNpCdmpUcaDVh','2026-10-09 17:10:00','t1Vkw1dNpCdmpUcaDVh',
 'GD20260929093029001','集岭管理所防汛物资仓库屋面漏水处理','#pfqj#',
 '防汛物资仓库屋面防水层老化，雨后多处渗漏，影响编织袋、土工布等物资存放，需清除老化层并重做屋面防水。',
 'JsrIMB8v9oKi0MpT79N','B253REE7NnfivkcFukXS','COYX8zL4KM7BKSsK56V','VDYslnZzhupmNMKB2ix','2026-10-10 18:00:00',
 '已清除原老化防水层并新做 SBS 防水卷材 180 平方米，闭水试验 24 小时无渗漏，防汛物资已全部回库，验收合格。','#3#','2026-10-09 17:10:00'),
-- 已取消（1 条）
('ptlwo2026000012','hltgq','2026-10-13 15:10:13','pbJdAWiiG3Ce6uEnXYd','2026-10-18 09:20:00','pbJdAWiiG3Ce6uEnXYd',
 'GD20261013151013001','长岭跌水节制闸闸门远程控制改造','#bgtg#',
 '长岭跌水节制闸闸门现为现地手动操作，计划接入调度中心远程控制，需增设控制柜与通信模块。因年度信息化改造资金调整，本工单取消，纳入 2027 年度计划。',
 'pJgk6PcRYVqd9kgYsdl','By7FWFI6i3cWtNhEOgl7',NULL,NULL,NULL,NULL,'#4#',NULL);
-- ============================================================
-- 5. 问题记录 14 条（待处理 2 / 处理中 2 / 已转工单 2 / 已关闭 6 / 已作废 2）
--   status：#1# 待处理 / #2# 处理中 / #3# 已转工单 / #4# 已关闭 / #5# 已作废
--   handle_status：#1# 待确认 / #2# 转工单 / #3# 直接处理
--   risk_level：#1# 低风险 / #2# 中风险 / #3# 高风险
--   abqezf = 关联巡检记录；work_order_id = 转工单后回写；
--   转工单类问题的关闭由工单验收触发（closeIssuesByWorkOrder），因此 ezuvil 为空、结果见工单
-- ============================================================
INSERT INTO "qixiao-apaas"."t_auto_hltgq_knc3g_bpzjoh"
("id","corp_code","created_at","created_by","updated_at","updated_by",
 "code","title","site","device","content","user","time",
 "handle_status","status","work_order_id","abqezf","ezuvil","risk_level")
VALUES
-- 待处理（2 条）
('ptliss2026000001','hltgq','2026-10-09 09:42:00','fQQQcbAsdOEph0Jrxdw','2026-10-09 09:42:00','fQQQcbAsdOEph0Jrxdw',
 'QST-1791510120000','太宿干渠进水闸上游翼墙勾缝脱落','k6V2otO7e4ZmEba7HMD','kQnPCB5nv7v0eI9O6INC',
 '上游左岸翼墙勾缝砂浆大面积脱落，脱落面积约 6 平方米，雨后渗水明显，需尽快安排修补并观察墙体变形。',
 'fQQQcbAsdOEph0Jrxdw','2026-10-09 09:42:00','#1#','#1#',NULL,'ptlrec2026000004',NULL,'#2#'),
('ptliss2026000002','hltgq','2026-10-16 15:05:00','fQQQcbAsdOEph0Jrxdw','2026-10-16 15:05:00','fQQQcbAsdOEph0Jrxdw',
 'QST-1792134300000','太怀干渠进水闸上游护坡顶堆放秸秆','zxgbgrx0lUXS44Jklo8','Qt9s3NccbfYHv4VyfI72',
 '上游左岸护坡顶部堆放秸秆约 20 平方米，占压巡查通道，存在火灾隐患，需联系属地清理并设置禁堆标识。',
 'fQQQcbAsdOEph0Jrxdw','2026-10-16 15:05:00','#1#','#1#',NULL,'ptlrec2026000005',NULL,'#1#'),
-- 已转工单（2 条）
('ptliss2026000003','hltgq','2026-09-12 09:22:00','t1Vkw1dNpCdmpUcaDVh','2026-09-12 10:35:00','t1Vkw1dNpCdmpUcaDVh',
 'QST-1789176120000','毕岭节制闸1#启闭机开度仪示值偏差','SQcUlflXiUAC9eagJJx','Ry3ro4h1T9DBL3y8yBST',
 '1# 启闭机开度仪显示值与闸门实际开度偏差约 8cm，影响闸门精确调度，建议重新标定并复测。',
 't1Vkw1dNpCdmpUcaDVh','2026-09-12 09:22:00','#2#','#3#','ptlwo2026000004','ptlrec2026000008',NULL,'#2#'),
('ptliss2026000004','hltgq','2026-09-26 10:05:00','t1Vkw1dNpCdmpUcaDVh','2026-09-26 10:50:00','t1Vkw1dNpCdmpUcaDVh',
 'QST-1790388300000','王家桥节制闸1#电动启闭机接触器烧蚀','LCmIfy4WB6lQ3OROQRo','cci8DBTwGXBpp0ANd1qD',
 '1# 电动启闭机接触器烧蚀，启闭时伴随异响，已停用该孔闸门并切换备用电源，需尽快更换接触器。',
 't1Vkw1dNpCdmpUcaDVh','2026-09-26 10:05:00','#2#','#3#','ptlwo2026000005','ptlrec2026000010',NULL,'#3#'),
-- 已关闭（6 条；含直接处理闭环 3 条、转工单验收闭环 3 条）
('ptliss2026000005','hltgq','2026-09-02 09:35:00','NoQEHI2sK4TzODmHnsj','2026-09-03 16:20:00','NoQEHI2sK4TzODmHnsj',
 'QST-1788312900000','渠首进水闸上游拦污栅锈蚀','CAYQ739MiBWMg9gQvyi','46kDkhaJmKMTsizTrnSc',
 '上游拦污栅栅条锈蚀，局部焊点开焊，栅前漂浮物清理不及时，影响过水与拦污效果。',
 'NoQEHI2sK4TzODmHnsj','2026-09-02 09:35:00','#3#','#4#',NULL,'ptlrec2026000012',
 '已组织人员对拦污栅补焊除锈并涂刷防锈漆，同步清理栅前漂浮物，过水恢复正常，9 月 3 日完成。','#1#'),
('ptliss2026000006','hltgq','2026-09-09 10:05:00','NoQEHI2sK4TzODmHnsj','2026-09-10 11:10:00','NoQEHI2sK4TzODmHnsj',
 'QST-1788919500000','南干渠进水闸流量计探头淤堵','cOGko9nLfFz6rRAvKvY','pXFUN6DMCMgZH57DovvU',
 '流量计探头淤堵，测值偏小约 15%，影响水量计量准确性，需拆卸清淤并重新率定。',
 'NoQEHI2sK4TzODmHnsj','2026-09-09 10:05:00','#3#','#4#',NULL,'ptlrec2026000014',
 '已拆卸探头清淤并重新率定，复测数据与渠道水位相符，计量偏差恢复至 2% 以内，9 月 10 日完成。','#1#'),
('ptliss2026000007','hltgq','2026-09-15 10:35:00','NoQEHI2sK4TzODmHnsj','2026-09-17 15:40:00','NoQEHI2sK4TzODmHnsj',
 'QST-1789439700000','汪元节制闸下游巡查便道塌陷','GwDmpPwcfw6sk8C9JtM','vO75rcB9kUCnNaPCkZ1M',
 '下游右岸巡查便道局部塌陷，长约 3 米、深约 0.4 米，影响巡查通行安全。',
 'NoQEHI2sK4TzODmHnsj','2026-09-15 10:35:00','#3#','#4#',NULL,'ptlrec2026000015',
 '已回填夯实并铺设碎石面层，塌陷段增设警示标识，9 月 17 日恢复通行，巡查路线正常。','#2#'),
('ptliss2026000008','hltgq','2026-10-14 10:20:00','NoQEHI2sK4TzODmHnsj','2026-10-21 16:10:00','fQQQcbAsdOEph0Jrxdw',
 'QST-1791944400000','南干渠进水闸2#闸门止水橡皮脱落','cOGko9nLfFz6rRAvKvY','bMpR5Au2kMUJalnhxYdM',
 '2# 闸门底止水橡皮局部脱落约 2 米，闸门关闭后渗漏明显，启闭时伴随异响，需更换止水橡皮。',
 'NoQEHI2sK4TzODmHnsj','2026-10-14 10:20:00','#2#','#4#','ptlwo2026000006','ptlrec2026000020',NULL,'#2#'),
('ptliss2026000009','hltgq','2026-10-19 15:30:00','NoQEHI2sK4TzODmHnsj','2026-10-25 15:30:00','NoQEHI2sK4TzODmHnsj',
 'QST-1792395000000','毕岭节制闸流量计供电异常','SQcUlflXiUAC9eagJJx','mhDWj11mPHY8ldv84oGW',
 '流量计太阳能供电板积尘严重，蓄电池电压偏低，监测数据频繁掉线，需清洗供电板并更换蓄电池。',
 'NoQEHI2sK4TzODmHnsj','2026-10-19 15:30:00','#2#','#4#','ptlwo2026000007','ptlrec2026000022',NULL,'#1#'),
('ptliss2026000010','hltgq','2026-10-12 10:30:00','t1Vkw1dNpCdmpUcaDVh','2026-10-17 10:25:00','t1Vkw1dNpCdmpUcaDVh',
 'QST-1791772200000','安居龙泄洪闸启闭机限位开关松动','JsrIMB8v9oKi0MpT79N','HqhH5OWfe5Hl1TAVRUwI',
 '启闭机上限位开关固定螺栓松动，上限位动作不灵敏，存在闸门过卷风险，需紧固并复核限位行程。',
 't1Vkw1dNpCdmpUcaDVh','2026-10-12 10:30:00','#2#','#4#','ptlwo2026000008','ptlrec2026000026',NULL,'#2#'),
-- 处理中（2 条）
('ptliss2026000011','hltgq','2026-10-14 10:45:00','NoQEHI2sK4TzODmHnsj','2026-10-22 09:30:00','NoQEHI2sK4TzODmHnsj',
 'QST-1791945900000','南干渠进水闸2#启闭机运行异响','cOGko9nLfFz6rRAvKvY','bMpR5Au2kMUJalnhxYdM',
 '2# 启闭机运行过程中减速箱有间歇性异响，需检查齿轮啮合与润滑油情况，必要时更换轴承。',
 'NoQEHI2sK4TzODmHnsj','2026-10-14 10:45:00','#3#','#2#',NULL,'ptlrec2026000020',
 '已完成减速箱开箱检查，更换润滑油并调整齿轮间隙，异响明显减弱，待继续观察 7 天运行情况后关闭。','#2#'),
('ptliss2026000012','hltgq','2026-10-16 15:00:00','t1Vkw1dNpCdmpUcaDVh','2026-10-16 15:00:00','t1Vkw1dNpCdmpUcaDVh',
 'QST-1792134000000','段垅节制闸上游护坡淘刷','hrYBO8MN9AegZehWUd6','cDQ6dsljSoGsWDPnKorM',
 '上游左岸护坡被水流淘刷，长约 6 米，坡脚局部掏空，汛后需及时抛石护脚并修复护坡。',
 't1Vkw1dNpCdmpUcaDVh','2026-10-16 15:00:00','#3#','#2#',NULL,'ptlrec2026000027',NULL,'#3#'),
-- 已作废（2 条）
('ptliss2026000013','hltgq','2026-10-17 11:00:00','NoQEHI2sK4TzODmHnsj','2026-10-18 08:50:00','NoQEHI2sK4TzODmHnsj',
 'QST-1792206000000','汪元节制闸上游漂浮垃圾聚集','GwDmpPwcfw6sk8C9JtM','SNucIdYvbIjh1WJfxx28',
 '上游水面漂浮垃圾聚集约 15 平方米，部分进入拦污栅前，需组织打捞。',
 'NoQEHI2sK4TzODmHnsj','2026-10-17 11:00:00','#1#','#5#',NULL,'ptlrec2026000021',NULL,'#1#'),
('ptliss2026000014','hltgq','2026-10-16 15:20:00','t1Vkw1dNpCdmpUcaDVh','2026-10-17 09:10:00','t1Vkw1dNpCdmpUcaDVh',
 'QST-1792135200000','段垅节制闸拦污栅栅条变形','hrYBO8MN9AegZehWUd6','cDQ6dsljSoGsWDPnKorM',
 '拦污栅两根栅条受漂浮物撞击变形，栅距不均，需整形或更换栅条。',
 't1Vkw1dNpCdmpUcaDVh','2026-10-16 15:20:00','#1#','#5#',NULL,'ptlrec2026000027',NULL,'#2#');

COMMIT;

-- ============================================================
-- 6. 执行后自检（只读，不影响数据）
-- ============================================================

-- 6.1 本次写入行数（期望：计划 9 / 范围 51 / 记录 31 / 工单 12 / 问题 14）
SELECT 'plan' AS object, COUNT(*) AS rows FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_schedule" WHERE id LIKE 'ptl%'
UNION ALL SELECT 'plan_site_rel', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_knc3g_nlbdju_tkwovk_rel" WHERE id LIKE 'ptl%'
UNION ALL SELECT 'record', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_inspection_record" WHERE id LIKE 'ptl%'
UNION ALL SELECT 'work_order', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_work_order" WHERE id LIKE 'ptl%'
UNION ALL SELECT 'issue', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_knc3g_bpzjoh" WHERE id LIKE 'ptl%'
ORDER BY object;

-- 6.2 残留测试数据（前三项与 wo_human_left 期望为 0；
--     wo_system_kept 应与执行前数量一致，本脚本不增删自动告警工单）
SELECT 'plan_left' AS check_item, COUNT(*) AS cnt FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_schedule" WHERE id NOT LIKE 'ptl%'
UNION ALL SELECT 'record_left', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_inspection_record" WHERE id NOT LIKE 'ptl%'
UNION ALL SELECT 'issue_left', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_knc3g_bpzjoh" WHERE id NOT LIKE 'ptl%'
UNION ALL SELECT 'wo_system_kept', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_work_order" WHERE created_by = 'SYSTEM'
UNION ALL SELECT 'wo_human_left', COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_work_order" WHERE created_by <> 'SYSTEM' AND id NOT LIKE 'ptl%'
ORDER BY check_item;

-- 6.3 关联一致性（期望全部 0 行）
SELECT r.id AS record_id, r.patrol_schedule, r.site
FROM "qixiao-apaas"."t_auto_hltgq_water_inspection_record" r
WHERE r.id LIKE 'ptl%' AND NOT EXISTS (
        SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_knc3g_nlbdju_tkwovk_rel" x
        WHERE x.biz_id = r.patrol_schedule AND x.rel_id = r.site)
UNION ALL
SELECT i.id, i.work_order_id, i.abqezf
FROM "qixiao-apaas"."t_auto_hltgq_knc3g_bpzjoh" i
WHERE i.id LIKE 'ptl%' AND (
        (i.abqezf IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_inspection_record" r WHERE r.id = i.abqezf))
     OR (i.work_order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_work_order" w WHERE w.id = i.work_order_id)))
UNION ALL
SELECT w.id, w.site, w.device
FROM "qixiao-apaas"."t_auto_hltgq_water_work_order" w
WHERE w.id LIKE 'ptl%' AND (
        (w.device IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_device" d WHERE d.id = w.device AND d.site = w.site))
     OR (w.org IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_apaas_uc_org" o WHERE o.id = w.org))
     OR (w."user" IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_apaas_uc_user" u WHERE u.id = w."user")));

-- 6.4 状态分布（核对口径）
SELECT 'plan_status_' || status AS item, COUNT(*) AS cnt FROM "qixiao-apaas"."t_auto_hltgq_water_patrol_schedule" WHERE id LIKE 'ptl%' GROUP BY status
UNION ALL SELECT 'record_status_' || status, COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_inspection_record" WHERE id LIKE 'ptl%' GROUP BY status
UNION ALL SELECT 'issue_status_' || status, COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_knc3g_bpzjoh" WHERE id LIKE 'ptl%' GROUP BY status
UNION ALL SELECT 'wo_status_' || status, COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_work_order" WHERE id LIKE 'ptl%' GROUP BY status
ORDER BY item;

-- ============================================================
-- 执行说明
--   1. 脚本整体在事务中执行，末尾已 COMMIT；若中途报错请 ROLLBACK 后反馈
--   2. 计划「2026年11月份渠系巡检计划」为草稿；列表展示态「未开始/已完成」由后端按巡检记录推导，不落库
--   3. 巡检记录 31 条中「待补充照片后提交」的草稿（ptlrec2026000025）用于验证提交校验：
--      进入巡检记录 → 编辑该草稿 → 上传照片后提交，即可走通记录提交流程
--   4. 重复执行：脚本按 ptl 前缀回收自身写入的数据（历史测试行按 id 白名单），可反复执行重置；
--      若执行后通过界面在 ptl 计划下新增了记录/问题，重跑不会回收这些新行，
--      计划被替换后记录会成为孤儿，重跑前请先删除界面新增数据
-- ============================================================
