-- ============================================================
-- 应急管理模块 · 正式演示数据脚本
-- 覆盖菜单：应急组织、灾害等级、应急事件、应急保障、应急通知、危险源、
--          危险发生记录、物资目录、物资台账、出入库管理、设备盘点
-- 数据库：KingbaseES V8（PostgreSQL 语法），schema：qixiao-apaas
-- 业务口径：花凉亭灌区（安庆市）防汛抗旱与应急物资管理
--
-- 影响范围：
--   1) 低代码表（t_auto_hltgq_bcmxdv_*）按 corp_code='hltgq' 整体重灌；
--   2) 物资目录/台账/出入库/盘点仅处理“应急物资（type=#2#）”及其关联单据，
--      常用物资（type=#1#）及其单据不动；
--   3) 单号序列按脚本涉及日期回填，避免平台新建单据时撞号。
--
-- 使用约束：本脚本是“演示/首次灌装”的数据重置脚本。每次执行都会清空 hltgq 下
--   全部应急物资（type=#2#，含页面新建的）及其台账、单据、流水，再按脚本重灌；
--   运行期重复执行会覆盖期间页面产生的应急业务数据，仅限演示环境使用。
--   盘点附件只删数据库记录，file-dir/stocktake 下的物理文件需人工清理。
-- 执行方式：整体事务执行（BEGIN ... COMMIT），失败自动回滚。
-- 执行前建议备份：bcmxdv_*、knc3g_slaifi/tzkioe、water_inbound/outbound/stocktake/change_log。
-- ============================================================
BEGIN;

-- ============================================================
-- 0. 清理：应急模块测试数据 + 本脚本历史数据
-- ============================================================

-- 0.1 待清理的应急物资范围（现有应急物资 + 本脚本物资）
CREATE TEMP TABLE tmp_em_material ON COMMIT DROP AS
SELECT id FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
WHERE corp_code = 'hltgq' AND (type = '#2#' OR id LIKE 'emmat%');

-- 0.2 待清理的单据范围（含应急物资明细的单据）
CREATE TEMP TABLE tmp_em_in_order ON COMMIT DROP AS
SELECT DISTINCT order_id FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail
WHERE material_id IN (SELECT id FROM tmp_em_material);
CREATE TEMP TABLE tmp_em_out_order ON COMMIT DROP AS
SELECT DISTINCT order_id FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail
WHERE material_id IN (SELECT id FROM tmp_em_material);
CREATE TEMP TABLE tmp_em_st_order ON COMMIT DROP AS
SELECT DISTINCT order_id FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail
WHERE material_id IN (SELECT id FROM tmp_em_material);

-- 0.3 低代码应急表整体清空
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_faiczc_iaistj_rel WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_faiczc              WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_gzbifm              WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn_deciwv_rel   WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn_pipygn_rel   WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn              WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_ifjcmy_kjlamx_rel   WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_ifjcmy              WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_lmqaew              WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_eqjqmx_fbgazq_rel   WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_eqjqmx              WHERE corp_code = 'hltgq';
DELETE FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_yxgojn              WHERE corp_code = 'hltgq';

-- 0.4 应急物资相关单据、明细、流水
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log
WHERE material_id IN (SELECT id FROM tmp_em_material) OR id LIKE 'emlog%';

-- 明细先删；单头仅在无剩余明细时删除，避免误删「应急+常用」混合单据里的常用物资行
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail
WHERE material_id IN (SELECT id FROM tmp_em_material) OR id LIKE 'emind%';
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order o
WHERE (o.id IN (SELECT order_id FROM tmp_em_in_order) OR o.id LIKE 'emin%')
  AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail d WHERE d.order_id = o.id);

DELETE FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail
WHERE material_id IN (SELECT id FROM tmp_em_material) OR id LIKE 'emckd%';
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order o
WHERE (o.id IN (SELECT order_id FROM tmp_em_out_order) OR o.id LIKE 'emck%')
  AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail d WHERE d.order_id = o.id);

DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail
WHERE material_id IN (SELECT id FROM tmp_em_material) OR id LIKE 'emstd%';
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order o
WHERE (o.id IN (SELECT order_id FROM tmp_em_st_order) OR o.id LIKE 'emst%')
  AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail d WHERE d.order_id = o.id);
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_attachment a
WHERE a.corp_code = 'hltgq'
  AND (a.id LIKE 'ematt%'
       OR (a.order_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order o WHERE o.id = a.order_id)));

-- 0.5 台账与物资目录
DELETE FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
WHERE corp_code = 'hltgq'
  AND (jzikio IN (SELECT id FROM tmp_em_material) OR id LIKE 'emled%');
DELETE FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
WHERE id IN (SELECT id FROM tmp_em_material);
-- 说明：台账已在上一段按“应急物资 id”清理完毕，此处不做“孤儿台账”兜底删除，
-- 避免误删其它业务的台账、并留下无主流水。

-- ============================================================
-- 1. 应急组织（faiczc）+ 成员（faiczc_iaistj_rel）
--    组织类型：#1#指挥组 #kqeu#抢险组 #ycps#通信保障组 #nxmt#电力保障组 #qmqa#后勤保障组 #opzy#宣传培训组
--    状态：#1#草稿 #eigv#启用 #ghfy#停用
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_faiczc
(id, corp_code, created_at, created_by, updated_at, updated_by, soixth, mtqqnd, muwaab, yhceyc, fxqclu, kaxhzf) VALUES
('emorg01','hltgq','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo',
 '防汛抗旱指挥组','#1#','A9jEKZmJhcmUEK4RCZo','统一指挥灌区防汛抗旱与突发事件应急处置，决策响应级别，调度抢险队伍和应急物资，对接市防汛抗旱指挥部。','15212773620','#eigv#'),
('emorg02','hltgq','2026-03-02 09:05:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:05:00','A9jEKZmJhcmUEK4RCZo',
 '工程抢险组','#kqeu#','ivU85mukdyTmOmlyfwY','承担渠道、渠系建筑物、泵站险情的抢护与应急加固，编制并实施抢护方案，负责抢险现场技术指导。','18955687087','#eigv#'),
('emorg03','hltgq','2026-03-02 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:10:00','A9jEKZmJhcmUEK4RCZo',
 '通信保障组','#ycps#','VDLqdrLqoRQkmH7tltL','保障防汛通信、视频会商和雨情水情监测预警系统畅通，负责应急信息收集、报送与发布。','18956011517','#eigv#'),
('emorg04','hltgq','2026-03-02 09:15:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:15:00','A9jEKZmJhcmUEK4RCZo',
 '电力保障组','#nxmt#','YtZXYUoWQnJRZocdDls','保障泵站、闸门启闭设备应急供电，组织电力故障抢修和临时供电线路架设。','18009669031','#eigv#'),
('emorg05','hltgq','2026-03-02 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:20:00','A9jEKZmJhcmUEK4RCZo',
 '后勤保障组','#qmqa#','re1kasoTAOQ9fUYLa3X','负责应急物资、抢险车辆、食宿和医疗保障，落实抢险队伍后勤供给与转移群众安置。','15357087685','#eigv#'),
('emorg06','hltgq','2026-03-02 09:25:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:25:00','A9jEKZmJhcmUEK4RCZo',
 '宣传培训组','#opzy#','IB21PXErczeQn3QCvie','组织防汛避险宣传、应急演练和业务培训，负责信息发布与舆情应对。','13905565129','#eigv#');

INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_faiczc_iaistj_rel
(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order) VALUES
('emrel01','hltgq','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','emorg01','A9jEKZmJhcmUEK4RCZo',1),
('emrel02','hltgq','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','emorg01','AYNLpaKmcMfTQXKVWbK',2),
('emrel03','hltgq','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:00:00','A9jEKZmJhcmUEK4RCZo','emorg01','EiCDnlpzopRMJzZKYS4',3),
('emrel04','hltgq','2026-03-02 09:05:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:05:00','A9jEKZmJhcmUEK4RCZo','emorg02','ivU85mukdyTmOmlyfwY',1),
('emrel05','hltgq','2026-03-02 09:05:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:05:00','A9jEKZmJhcmUEK4RCZo','emorg02','EiCDnlpzopRMJzZKYS4',2),
('emrel06','hltgq','2026-03-02 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:10:00','A9jEKZmJhcmUEK4RCZo','emorg03','VDLqdrLqoRQkmH7tltL',1),
('emrel07','hltgq','2026-03-02 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:10:00','A9jEKZmJhcmUEK4RCZo','emorg03','IB21PXErczeQn3QCvie',2),
('emrel08','hltgq','2026-03-02 09:15:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:15:00','A9jEKZmJhcmUEK4RCZo','emorg04','YtZXYUoWQnJRZocdDls',1),
('emrel09','hltgq','2026-03-02 09:15:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:15:00','A9jEKZmJhcmUEK4RCZo','emorg04','ivU85mukdyTmOmlyfwY',2),
('emrel10','hltgq','2026-03-02 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:20:00','A9jEKZmJhcmUEK4RCZo','emorg05','re1kasoTAOQ9fUYLa3X',1),
('emrel11','hltgq','2026-03-02 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:20:00','A9jEKZmJhcmUEK4RCZo','emorg05','AYNLpaKmcMfTQXKVWbK',2),
('emrel12','hltgq','2026-03-02 09:25:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:25:00','A9jEKZmJhcmUEK4RCZo','emorg06','IB21PXErczeQn3QCvie',1),
('emrel13','hltgq','2026-03-02 09:25:00','A9jEKZmJhcmUEK4RCZo','2026-03-02 09:25:00','A9jEKZmJhcmUEK4RCZo','emorg06','re1kasoTAOQ9fUYLa3X',2);

-- ============================================================
-- 2. 灾害等级（gzbifm）
--    灾害等级：#1#特别重大灾害 #ikik#重大灾害 #lxwi#较大灾害 #qjfg#一般灾害
--    响应级别：#1#一级响应 #svol#二级响应 #hwkc#三级响应 #tavn#四级响应
--    启用状态：#1#草稿 #juqe#启用 #ziek#停用
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_gzbifm
(id, corp_code, created_at, created_by, updated_at, updated_by, apjale, aymvna, hfvgij, ablimz, iyvenp, logcyk) VALUES
('emlvl01','hltgq','2026-03-05 10:00:00','A9jEKZmJhcmUEK4RCZo','2026-03-05 10:00:00','A9jEKZmJhcmUEK4RCZo',
 'Ⅰ级（特别重大）灾害响应','花凉亭水库水位超汛限水位2米以上，或总干渠发生漫溢、决口，灌区受淹面积超过20万亩。','#1#',
 '灌区防指立即启动Ⅰ级响应，全体人员上堤，24小时值守巡查；转移危险区群众，抢险队伍和物资全部到位，必要时请求市级支援。','#juqe#','#1#'),
('emlvl02','hltgq','2026-03-05 10:05:00','A9jEKZmJhcmUEK4RCZo','2026-03-05 10:05:00','A9jEKZmJhcmUEK4RCZo',
 'Ⅱ级（重大）灾害响应','水库水位超汛限水位1至2米，或干渠发生重大险情，灌区受淹面积10至20万亩。','#svol#',
 '启动Ⅱ级响应，抢险队伍全员到位，重点部位加密巡查，预置抢险机械与物资，做好群众转移准备。','#juqe#','#ikik#'),
('emlvl03','hltgq','2026-03-05 10:10:00','A9jEKZmJhcmUEK4RCZo','2026-03-05 10:10:00','A9jEKZmJhcmUEK4RCZo',
 'Ⅲ级（较大）灾害响应','24小时降雨量达100毫米以上，或干渠出现较大险情，灌区受淹面积5至10万亩。','#hwkc#',
 '启动Ⅲ级响应，加强渠道与渠系建筑物巡查防守，预置抢险物资和机械设备，落实24小时值班和每日两次信息报送。','#juqe#','#lxwi#'),
('emlvl04','hltgq','2026-03-05 10:15:00','A9jEKZmJhcmUEK4RCZo','2026-03-05 10:15:00','A9jEKZmJhcmUEK4RCZo',
 'Ⅳ级（一般）灾害响应','24小时降雨量50至100毫米，或支渠发生局部险情，灌区受淹面积5万亩以下。','#tavn#',
 '启动Ⅳ级响应，加密雨情水情监测，做好抢险队伍和物资准备，发现险情立即上报并先期处置。','#juqe#','#qjfg#');

-- ============================================================
-- 3. 应急事件（bewqdn）+ 协同人员 + 关联应急保障
--    确认状态：#1#未确认 #yamt#已确认 #ffyq#响应中 #izni#已解除
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn
(id, corp_code, created_at, created_by, updated_at, updated_by, iltilq, ixfpye, uwokbw, hejmit,
 jhjtcd, jaaiza, sctlfp, agwcqh, afjnbi, neawds, ipawlz, kfapni, disfal, szfbgi) VALUES
('emevt01','hltgq','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','2026-07-10 18:30:00','A9jEKZmJhcmUEK4RCZo',
 '监测系统告警','花凉亭水库水位超汛限水位0.35米，总干渠渠首流量增至185立方米每秒','总干渠沿线及太湖县徐桥镇、小池镇',
 '2026-07-08 06:20:00','#izni#','emorg01','emlvl03','2026-07-08 07:00:00',
 '启动Ⅲ级响应，调度总干渠节制闸分级泄流，工程抢险组上堤分段巡查，落实24小时值班。','ivU85mukdyTmOmlyfwY',
 '2026-07-08 07:00:00','2026-07-10 18:00:00','每日8时、20时报送水位、流量与巡查情况','汛期强降雨过程，7月10日18时水位退至汛限以下后解除响应'),
('emevt02','hltgq','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','2026-07-17 17:40:00','A9jEKZmJhcmUEK4RCZo',
 '人工上报','东干渠K12+300渠堤背水坡出现渗漏，渗水携带细颗粒呈浑浊状','东干渠徐桥镇段，影响灌溉面积1.2万亩',
 '2026-07-15 14:05:00','#izni#','emorg02','emlvl04','2026-07-15 14:40:00',
 '采用导渗沟导流加反滤料压渗，观测点每2小时观测渗流量与浑浊度，稳定前限制水位。','ivU85mukdyTmOmlyfwY',
 '2026-07-15 15:00:00','2026-07-17 18:00:00','每2小时报送渗流量观测记录，异常立即上报','经72小时观测渗流量稳定在0.2升每秒，水质清澈，险情解除'),
('emevt03','hltgq','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','2026-08-06 09:00:00','A9jEKZmJhcmUEK4RCZo',
 '气象预警','未来24小时灌区累计降雨量将达120毫米，局地超过150毫米','全灌区，涉及太湖、望江、怀宁、宿松四个县管理所',
 '2026-08-02 16:30:00','#izni#','emorg01','emlvl03','2026-08-02 17:00:00',
 '启动Ⅲ级响应，提前腾空渠系水位，抢险物资前置至各管理所，泵站和闸门进入待命状态。','A9jEKZmJhcmUEK4RCZo',
 '2026-08-02 17:00:00','2026-08-05 20:00:00','各管理所每日报送雨情、水情、工情和物资调用情况','降雨过程结束，8月6日9时解除响应，转入渠道冲刷与设备复核'),
('emevt04','hltgq','2026-08-19 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-08-19 18:10:00','A9jEKZmJhcmUEK4RCZo',
 '设备告警','花凉亭泵站2号机组轴承温度升至85℃，超过报警值','花凉亭泵站及西干渠供水区域',
 '2026-08-19 09:12:00','#izni#','emorg04','emlvl04','2026-08-19 09:45:00',
 '立即停机并切换备用机组，检查发现轴承润滑油脂劣化，更换轴承及润滑油脂后空载试运行。','YtZXYUoWQnJRZocdDls',
 '2026-08-19 09:45:00','2026-08-19 18:00:00','抢修完成后报送试运行温度记录','温度稳定在62℃，已恢复并网运行'),
('emevt05','hltgq','2026-09-10 10:30:00','A9jEKZmJhcmUEK4RCZo','2026-09-12 18:10:00','A9jEKZmJhcmUEK4RCZo',
 '巡检发现','南干渠节制闸启闭机钢丝绳断股，闸门无法正常启闭','南干渠下游2.3万亩灌溉供水',
 '2026-09-10 10:20:00','#izni#','emorg02','emlvl04','2026-09-10 11:00:00',
 '启用备用启闭机控制闸门，联系厂家更换钢丝绳，抢修期间限制闸门操作并安排专人值守。','ivU85mukdyTmOmlyfwY',
 '2026-09-10 11:00:00','2026-09-12 18:00:00','抢修进度每日报送，完成后报送试运行情况','钢丝绳已更换，闸门启闭试验正常'),
('emevt06','hltgq','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','2026-10-08 17:00:00','A9jEKZmJhcmUEK4RCZo',
 '人工上报','西干渠怀宁段K8+600渠道边坡滑塌约15米，土方约320立方米，堵塞部分过水断面','西干渠怀宁县管理所辖区，影响灌溉面积0.8万亩',
 '2026-09-28 08:40:00','#ffyq#','emorg02','emlvl03','2026-09-28 09:10:00',
 '组织挖掘机清淤、坡脚回填压重，设置警示标志和观测点并安排专人值守，同步编制永久治理方案。','ivU85mukdyTmOmlyfwY',
 '2026-09-28 09:10:00','2026-10-31 18:00:00','每周报送治理进度和边坡观测数据','应急处置已完成，永久治理施工中');

INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn_deciwv_rel
(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order) VALUES
('emrel21','hltgq','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','emevt01','VDLqdrLqoRQkmH7tltL',1),
('emrel22','hltgq','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','emevt01','sN1whojZaDfmArRp7GA',2),
('emrel23','hltgq','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','emevt02','sN1whojZaDfmArRp7GA',1),
('emrel24','hltgq','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','emevt02','YtZXYUoWQnJRZocdDls',2),
('emrel25','hltgq','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','emevt03','ivU85mukdyTmOmlyfwY',1),
('emrel26','hltgq','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','emevt03','sN1whojZaDfmArRp7GA',2),
('emrel27','hltgq','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','emevt03','VDLqdrLqoRQkmH7tltL',3),
('emrel28','hltgq','2026-08-19 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-08-19 09:20:00','A9jEKZmJhcmUEK4RCZo','emevt04','ivU85mukdyTmOmlyfwY',1),
('emrel29','hltgq','2026-09-10 10:30:00','A9jEKZmJhcmUEK4RCZo','2026-09-10 10:30:00','A9jEKZmJhcmUEK4RCZo','emevt05','sN1whojZaDfmArRp7GA',1),
('emrel30','hltgq','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','emevt06','EiCDnlpzopRMJzZKYS4',1),
('emrel31','hltgq','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','emevt06','A9jEKZmJhcmUEK4RCZo',2);

INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn_pipygn_rel
(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order) VALUES
('emrel41','hltgq','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','emevt01','emsup03',1),
('emrel42','hltgq','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','2026-07-08 06:25:00','A9jEKZmJhcmUEK4RCZo','emevt01','emsup07',2),
('emrel43','hltgq','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','emevt02','emsup03',1),
('emrel44','hltgq','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','2026-07-15 14:10:00','A9jEKZmJhcmUEK4RCZo','emevt02','emsup04',2),
('emrel45','hltgq','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','emevt03','emsup01',1),
('emrel46','hltgq','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','2026-08-02 16:35:00','A9jEKZmJhcmUEK4RCZo','emevt03','emsup03',2),
('emrel47','hltgq','2026-08-19 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-08-19 09:20:00','A9jEKZmJhcmUEK4RCZo','emevt04','emsup05',1),
('emrel48','hltgq','2026-09-10 10:30:00','A9jEKZmJhcmUEK4RCZo','2026-09-10 10:30:00','A9jEKZmJhcmUEK4RCZo','emevt05','emsup01',1),
('emrel49','hltgq','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','emevt06','emsup04',1),
('emrel50','hltgq','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','2026-09-28 08:50:00','A9jEKZmJhcmUEK4RCZo','emevt06','emsup07',2);

-- ============================================================
-- 4. 应急保障（ifjcmy）
--    保障类别：#1#队伍专家 #nkzk#物资装备 #jcpi#通信电力 #ysfb#车辆交通
--             #lzyz#医疗后勤 #uvbu#资源调度 #flhd#培训演练
--    当前状态：#1#可用 #fwdj#停用
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_ifjcmy
(id, corp_code, created_at, created_by, updated_at, updated_by,
 diziai, pxsefg, haqjle, fhlhmx, obhwqq, bljckc, btjuks, gmypct, onizsz, zfvgfx) VALUES
('emsup01','hltgq','2026-04-15 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-04-15 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-001','#1#','灌区防汛抢险突击队','由工程管理科、调度运行科及四个县管理所骨干组成，共45人，配备救生衣、编织袋、桩木等抢险工具。',
 '45人','工程管理科','渠道漫溢、堤防渗漏、渠系建筑物险情抢护','#1#','2026-04-15','接防指指令后30分钟内完成集结并出发'),
('emsup02','hltgq','2026-03-20 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-03-20 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-002','#1#','省级防汛抗旱专家库（安庆组）','水利工程、水文水资源、工程地质专业专家12名，提供险情研判、技术方案论证与处置指导。',
 '12人','安庆市水利局','重大险情技术会商、应急处置方案审定','#1#','2026-03-20','通过市防指协调调用，2小时内响应并到达现场'),
('emsup03','hltgq','2026-04-10 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-04-10 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-003','#nkzk#','防汛编织袋','用于渠道抢险堵漏、坡脚压渗和临时围堰填筑，储备于灌区主仓库。',
 '50000条','工程管理科','渠道渗漏、决口抢险、临时围堰','#1#','2026-04-10','应急抢险时由主仓库紧急调拨，2小时内送达现场'),
('emsup04','hltgq','2026-04-10 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-04-10 09:10:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-004','#nkzk#','块石、碎石及砂料','储备于徐桥、小池两处料场，用于反滤压渗、抛石护脚和坡脚回填压重。',
 '3200立方米','工程管理科','堤防淘刷、坡脚失稳、抛石护岸','#1#','2026-04-10','就近料场装运，机械配合抛投，4小时内进场'),
('emsup05','hltgq','2026-05-06 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-08-20 16:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-005','#jcpi#','移动式柴油发电机','50千瓦移动发电机2台，保障闸站启闭、视频监控和应急照明用电。',
 '2台','调度运行科','停电应急供电、闸门启闭保障','#fwdj#','2026-08-20','1台送厂保养，其余保持待命；接通知后30分钟内送达现场'),
('emsup06','hltgq','2026-05-06 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-05-06 09:10:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-006','#jcpi#','卫星电话及应急通信包','卫星电话4部、对讲机30台、便携基站1套，保障公网中断情况下的应急通信。',
 '4部+30台','调度运行科','公网中断时的应急通信保障','#1#','2026-05-06','由通信保障组统一配发，每月进行一次通话测试'),
('emsup07','hltgq','2026-04-28 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-04-28 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-007','#ysfb#','抢险运输车及挖掘机','自卸车4辆、挖掘机2台、装载机1台，汛期24小时待命。',
 '7台','后勤保障组','抢险人员物资运输、土石方抢护','#1#','2026-04-28','接通知后30分钟内出动，机械随车配司机'),
('emsup08','hltgq','2026-05-12 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-05-12 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-008','#lzyz#','应急医疗与生活保障','与徐桥镇中心卫生院建立联动机制，配备急救箱20套、帐篷30顶、折叠床100张。',
 '20套+30顶','后勤保障组','抢险现场医疗救护、转移群众临时安置','#1#','2026-05-12','现场设医疗点，医护人员15分钟内到位'),
('emsup09','hltgq','2026-06-01 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-009','#uvbu#','社会应急资源调用清单','与周边3家施工企业签订应急支援协议，可调用土石方机械12台、运输车20辆、劳务人员80人。',
 '3家企业','调度运行科','特大险情力量增援、大规模抢护','#1#','2026-06-01','由灌区防指统一发函调用，4小时内进场'),
('emsup10','hltgq','2026-04-25 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-04-25 09:00:00','A9jEKZmJhcmUEK4RCZo',
 'YJBZ-2026-010','#flhd#','年度防汛应急演练','每年汛前组织一次全流程演练，覆盖预警发布、响应启动、抢险处置和人员转移。',
 '1次/年','宣传培训组','汛前准备、队伍磨合、预案检验','#1#','2026-04-25','演练方案报灌区防指审定后组织实施');

-- ============================================================
-- 5. 应急通知（lmqaew）
--    响应类别：#1#防汛 #vtsx#抗旱 #aivs#其他
--    通知事项：#1#启动响应 #ifde#升级响应 #sjib#降级响应 #doga#终止响应 #tckv#补充工作要求
--    市级响应等级：#1#Ⅰ级 #ggdd#Ⅱ级 #mytc#Ⅲ级 #bylw#Ⅳ级
--    通知状态：#1#草稿 #ucpd#已发布 #mevt#已终止
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_lmqaew
(id, corp_code, created_at, created_by, updated_at, updated_by,
 ypdfei, vqpwue, pxkzdz, sycosi, ioiwki, innfii, bqtufn, yimqhb, tbznmf, cgkpil) VALUES
('emnot01','hltgq','2026-07-08 06:50:00','A9jEKZmJhcmUEK4RCZo','2026-07-08 06:50:00','A9jEKZmJhcmUEK4RCZo',
 '关于启动防汛Ⅲ级应急响应的通知','#1#','#1#','#mytc#','安庆市防汛抗旱指挥部','2026-07-08 06:40:00','安防指〔2026〕18号',
 '{"value":"<p>7月8日6时，花凉亭水库水位超汛限水位0.35米，总干渠渠首流量增至185立方米每秒，未来24小时仍有强降雨。</p><p>经会商研判，市防指决定自7月8日7时起启动防汛Ⅲ级应急响应，请各县（市、区）防指和灌区管理单位按预案落实防御措施。</p>"}',
 '立即进入Ⅲ级响应，节制闸分级泄流，抢险组上堤巡查，每日两次报送水情。',
 '#ucpd#'),
('emnot02','hltgq','2026-07-10 18:20:00','A9jEKZmJhcmUEK4RCZo','2026-07-10 18:20:00','A9jEKZmJhcmUEK4RCZo',
 '关于终止防汛Ⅲ级应急响应的通知','#1#','#doga#','#mytc#','安庆市防汛抗旱指挥部','2026-07-10 18:10:00','安防指〔2026〕21号',
 '{"value":"<p>7月10日18时，花凉亭水库水位退至汛限水位以下，总干渠渠首流量回落至96立方米每秒，灌区骨干工程运行正常，未发生新的险情。</p><p>市防指决定自7月10日18时起终止防汛Ⅲ级应急响应，转入正常防汛值守。</p>"}',
 '保持汛期值班和日常巡查，做好渠道冲刷复核与物资清点补充。',
 '#mevt#'),
('emnot03','hltgq','2026-08-02 17:10:00','A9jEKZmJhcmUEK4RCZo','2026-08-02 17:10:00','A9jEKZmJhcmUEK4RCZo',
 '关于启动防汛Ⅲ级应急响应的通知','#1#','#1#','#mytc#','安庆市防汛抗旱指挥部','2026-08-02 16:50:00','安防指〔2026〕25号',
 '{"value":"<p>据气象部门预报，未来24小时灌区累计降雨量将达120毫米，局地超过150毫米，市气象台已发布暴雨橙色预警。</p><p>市防指决定自8月2日17时起启动防汛Ⅲ级应急响应，请各单位提前腾空渠系水位、前置抢险物资，做好低洼地段人员转移准备。</p>"}',
 '提前腾空渠系水位，抢险物资前置至关键部位，泵站闸门待命，每日报送雨情工情。',
 '#ucpd#'),
('emnot04','hltgq','2026-08-04 09:05:00','A9jEKZmJhcmUEK4RCZo','2026-08-04 09:05:00','A9jEKZmJhcmUEK4RCZo',
 '关于做好当前防汛工作的补充通知','#1#','#tckv#','#mytc#','安庆市水利局','2026-08-04 08:40:00','安水防〔2026〕31号',
 '{"value":"<p>强降雨过程已基本结束，但土壤含水量饱和，渠道退水期仍存在滑坡、渗漏风险。</p><p>请各灌区管理单位保持Ⅲ级响应状态不变，重点加强退水期渠道边坡和渠系建筑物巡查，及时清理淤积和漂浮物。</p>"}',
 'Ⅲ级响应延续执行，退水期每日全线巡查，重点排查边坡滑塌与渠堤渗漏。',
 '#ucpd#'),
('emnot05','hltgq','2026-09-28 15:30:00','A9jEKZmJhcmUEK4RCZo','2026-09-28 15:30:00','A9jEKZmJhcmUEK4RCZo',
 '关于加强西干渠滑塌段安全防范的通知','#1#','#tckv#','#bylw#','安庆市水利局','2026-09-28 15:00:00','安水防〔2026〕42号',
 '{"value":"<p>9月28日8时40分，西干渠怀宁段K8+600渠道边坡滑塌约15米，土方约320立方米，堵塞部分过水断面。</p><p>请灌区管理单位立即设置警示标志和观测点，安排专人值守，抓紧编制永久治理方案并落实资金。</p>"}',
 '滑塌段24小时值守观测，汛期限制渠道水位，9月30日前完成坡脚回填。',
 '#ucpd#'),
('emnot06','hltgq','2026-10-20 10:00:00','A9jEKZmJhcmUEK4RCZo','2026-10-20 10:00:00','A9jEKZmJhcmUEK4RCZo',
 '关于做好今冬明春抗旱保灌工作的通知','#vtsx#','#tckv#','#bylw#','安庆市水利局','2026-10-20 09:30:00','安水防〔2026〕48号',
 '{"value":"<p>据水文预测，今冬明春灌区来水总体偏少，花凉亭水库蓄水量较常年同期偏少一成。</p><p>请各管理单位提前编制抗旱保灌用水计划，做好渠系清淤和泵站检修，保障明春育秧和早稻灌溉用水。</p>"}',
 '11月10日前报送用水计划，12月底前完成泵站闸门检修，补足抗旱物资。',
 '#1#'),
('emnot07','hltgq','2026-08-06 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-08-06 09:10:00','A9jEKZmJhcmUEK4RCZo',
 '关于终止防汛Ⅲ级应急响应的通知','#1#','#doga#','#mytc#','安庆市防汛抗旱指挥部','2026-08-06 09:00:00','安防指〔2026〕26号',
 '{"value":"<p>8月6日8时，灌区降雨过程结束，花凉亭水库水位降至汛限水位以下1.2米，渠系工程运行正常。</p><p>市防指决定自8月6日9时起终止防汛Ⅲ级应急响应，转入正常防汛值守。</p>"}',
 '响应终止后转入正常值守，做好渠道冲刷、设备复核和物资清点补充。',
 '#mevt#');

-- ============================================================
-- 6. 危险源（yxgojn）
--    风险等级：#1#低风险 #vioy#一般风险 #ncuu#较大风险 #gqfd#重大风险
--    状态：#1#正常 #ptki#预警中 #fxaa#已发生 #atka#待整改 #mxgi#整改中 #ibig#已处置 #iswz#停用
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_yxgojn
(id, corp_code, created_at, created_by, updated_at, updated_by,
 dcejwz, mdkodd, egxaqe, xglilt, edikxg_x, edikxg_y, tdpxli, metxqp, tyymde, aiihpi,
 jucann, wvpgip, skklvy, ootito, dfxmtu, vudptt, cmmikk, pfmtgf) VALUES
('emhaz01','hltgq','2026-03-12 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-05-28 16:00:00','A9jEKZmJhcmUEK4RCZo',
 '花凉亭水库大坝','黏土心墙土石坝，坝长1150米，最大坝高57米。汛期高水位运行存在坝体渗流、坝坡失稳风险。',
 '立即报灌区防指并启动相应级别响应，坝后导渗、坝顶抢筑子堤，必要时加大泄流降低库水位，转移下游受影响人员。',
 '太湖县晋熙镇花凉亭水库大坝',116.309500,30.453600,'花凉亭水库枢纽管理中心','A9jEKZmJhcmUEK4RCZo','#1#','#ncuu#',
 '2026-03-12 09:00:00',
 '汛前疏通渗流观测孔并修补坝坡护砌；汛期每周外观检查、每日渗流量观测。',
 '2026-05-31',
 '设置坝后渗流观测点和安全警示标识，储备编织袋、块石等抢险物资，落实24小时值班和巡查责任人。',
 '汛前检查和护砌修补已完成，渗流量观测数据稳定，通过汛前安全检查。','护砌修补到位，观测孔疏通完好，通过汛前安全检查。','2026-05-28 15:00:00','甘武剑'),
('emhaz02','hltgq','2026-06-20 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-07-08 07:30:00','A9jEKZmJhcmUEK4RCZo',
 '总干渠渠首节制闸','4孔弧形钢闸门，设计流量120立方米每秒。汛期启闭频繁，存在闸门卡阻、启闭机故障和停电无法启闭风险。',
 '启用备用电源和手动启闭装置组织抢修；闸门无法关闭时在渠首分流泄水，同时降低上游来水，防止渠道漫溢。',
 '太湖县晋熙镇总干渠渠首',116.303300,30.447800,'调度运行科','VDLqdrLqoRQkmH7tltL','#ptki#','#vioy#',
 '2026-06-20 09:00:00',
 '汛前完成启闭试验和启闭机保养；汛期每日检查闸门、启闭机及备用电源。',
 '2026-06-30',
 '备用发电机每周试运行一次，启闭机房安排专人值守，配备手动启闭工具和应急照明。',
 NULL, NULL, NULL, NULL),
('emhaz03','hltgq','2026-07-15 14:30:00','A9jEKZmJhcmUEK4RCZo','2026-10-15 17:00:00','A9jEKZmJhcmUEK4RCZo',
 '东干渠K12+300渗漏段','东干渠为均质土堤，K12+300段背水坡曾发生渗漏险情，堤身填筑质量不均，存在管涌和滑坡风险。',
 '立即降低渠道水位并报防指；背水坡开挖导渗沟、铺设反滤料压渗；设置观测点监测渗流量和浑浊度，险情稳定前限制水位运行。',
 '东干渠徐桥镇段K12+300',116.420000,30.380000,'工程管理科','ivU85mukdyTmOmlyfwY','#ibig#','#gqfd#',
 '2026-07-15 14:30:00',
 '汛期实施导渗沟导流加反滤料压渗应急处置；汛后完成渠堤加培和防渗处理，恢复堤身完整。',
 '2026-10-10',
 '设置渗流量观测点每2小时观测一次，限制渠道水位；储备编织袋、土工布和反滤料，明确撤离路线。',
 '7月17日渗流量稳定、水质转清，险情解除；汛后渠堤加培和防渗处理已完成，观测正常。',
 '应急抢护到位，汛后加培和防渗处理完成，观测正常。',
 '2026-10-15 16:00:00','甘武剑'),
('emhaz04','hltgq','2026-08-19 09:30:00','A9jEKZmJhcmUEK4RCZo','2026-08-22 15:00:00','A9jEKZmJhcmUEK4RCZo',
 '花凉亭泵站2号机组','立式轴流泵配套630千瓦电机，长期高负荷运行，存在轴承温度过高、润滑失效和机组振动异常风险。',
 '立即停机并切换备用机组；检查轴承和润滑油脂，更换损坏部件；空载试运行合格后方可并网，抢修期间保障西干渠供水。',
 '太湖县花凉亭泵站',116.301800,30.450200,'调度运行科','YtZXYUoWQnJRZocdDls','#ibig#','#vioy#',
 '2026-08-19 09:30:00',
 '更换轴承及润滑油脂，调整机组运行工况，加装轴承温度在线监测；汛期每班记录轴承温度和振动值。',
 '2026-08-25',
 '轴承温度超过80℃自动报警停机，备用机组保持完好可用，抢修工器具和备品备件定点存放。',
 '8月19日完成抢修，空载试运行轴承温度稳定在62℃，已恢复并网运行，在线监测投运正常。','抢修完成，空载试运行温度稳定在62℃，验收合格。','2026-08-22 14:00:00','杨琪'),
('emhaz05','hltgq','2026-09-28 09:00:00','A9jEKZmJhcmUEK4RCZo','2026-10-24 16:00:00','A9jEKZmJhcmUEK4RCZo',
 '西干渠怀宁段K8+600滑塌边坡','渠道左侧边坡为膨胀土，遇水易软化崩解，K8+600段曾发生滑塌，存在再次滑塌堵塞渠道风险。',
 '立即设置警示标志和观测点，安排专人值守；坡脚回填压重、渠道清淤，编制永久治理方案；汛期限制渠道水位运行。',
 '西干渠怀宁县管理所辖区K8+600',116.758200,30.611400,'花凉亭灌区怀宁县管理所','EiCDnlpzopRMJzZKYS4','#mxgi#','#ncuu#',
 '2026-09-28 09:00:00',
 '坡脚回填压重、坡面削坡减载，增设坡顶截水沟和排水盲沟，坡脚设置抗滑挡墙，施工期加强变形观测。',
 '2026-12-31',
 '滑塌段24小时值守并每日观测，设置绕行警示和隔离围栏，汛期限制渠道水位，落实抢险机械和物资。',
 '9月30日完成坡脚回填压重和渠道清淤，过水断面恢复；永久治理施工中，边坡观测数据稳定。',
 NULL, NULL, NULL),
('emhaz06','hltgq','2026-09-10 10:00:00','A9jEKZmJhcmUEK4RCZo','2026-09-15 11:00:00','A9jEKZmJhcmUEK4RCZo',
 '南干渠节制闸启闭机房','启闭机钢丝绳使用年限较长，存在断股、锈蚀导致闸门无法启闭的风险，直接影响下游2.3万亩灌溉供水。',
 '启用备用启闭机控制闸门，联系厂家更换钢丝绳；抢修期间限制闸门操作并安排专人值守，必要时采取临时提闸措施。',
 '望江县南干渠节制闸',116.688600,30.283100,'望江县花凉亭灌区管理中心','IB21PXErczeQn3QCvie','#ibig#','#vioy#',
 '2026-09-10 10:00:00',
 '更换启闭机钢丝绳并做启闭试验，建立钢丝绳定期检查和探伤制度，启闭机加装限位保护。',
 '2026-09-30',
 '每月检查钢丝绳磨损和断丝情况，启闭机房配备手动启闭工具和应急照明，落实专人值守。',
 '9月12日完成钢丝绳更换，闸门启闭试验正常，限位保护动作可靠。','钢丝绳更换完毕，启闭试验正常，限位保护可靠。','2026-09-15 10:00:00','甘武剑');

-- ============================================================
-- 7. 危险发生记录（eqjqmx）：jufdfi 关联危险源
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_bcmxdv_eqjqmx
(id, corp_code, created_at, created_by, updated_at, updated_by, azglgq, wzsuid, spspil, jufdfi) VALUES
('emrec01','hltgq','2026-07-15 15:00:00','A9jEKZmJhcmUEK4RCZo','2026-07-17 18:00:00','A9jEKZmJhcmUEK4RCZo',
 '7月15日14时05分巡查发现东干渠K12+300渠堤背水坡出现渗漏，渗水携带细颗粒呈浑浊状，渗漏量约0.2升每秒。',
 '渗漏点位于背水坡坡脚以上1.2米，渗水浑浊，有发展为管涌的趋势，威胁下游1.2万亩灌溉供水安全和渠堤稳定。',
 '采用导渗沟导流加反滤料压渗处理，设置渗流量和浑浊度观测点每2小时观测一次，险情稳定前限制渠道水位。经72小时观测渗流量稳定、水质转清，7月17日险情解除。',
 'emhaz03'),
('emrec02','hltgq','2026-08-19 09:40:00','A9jEKZmJhcmUEK4RCZo','2026-08-19 18:00:00','A9jEKZmJhcmUEK4RCZo',
 '8月19日9时12分，花凉亭泵站2号机组轴承温度升至85℃，超过报警值，机组振动值同步上升。',
 '轴承润滑油脂劣化导致温度持续升高，若继续运行可能造成轴承烧损、机组停运，影响西干渠供水区域灌溉用水。',
 '立即停机并切换备用机组，检查发现轴承润滑油脂劣化，更换轴承及润滑油脂后空载试运行；温度稳定在62℃后恢复并网运行。',
 'emhaz04'),
('emrec03','hltgq','2026-09-10 10:30:00','A9jEKZmJhcmUEK4RCZo','2026-09-12 18:00:00','A9jEKZmJhcmUEK4RCZo',
 '9月10日10时20分巡检发现南干渠节制闸启闭机钢丝绳断股，闸门无法正常启闭。',
 '钢丝绳断股数量超过报废标准，闸门失去正常启闭能力，直接影响下游2.3万亩灌溉供水调度。',
 '启用备用启闭机控制闸门，联系厂家更换钢丝绳；抢修期间限制闸门操作并安排专人值守，9月12日完成更换，启闭试验正常。',
 'emhaz06'),
('emrec04','hltgq','2026-09-28 09:10:00','A9jEKZmJhcmUEK4RCZo','2026-10-24 16:00:00','A9jEKZmJhcmUEK4RCZo',
 '9月28日8时40分，西干渠怀宁段K8+600渠道左侧边坡滑塌约15米，土方约320立方米，堵塞部分过水断面。',
 '滑塌体挤占渠道过水断面约三分之一，渠道水位壅高，若遇降雨可能继续滑塌，威胁下游0.8万亩灌溉供水和渠道安全。',
 '组织挖掘机清淤、坡脚回填压重，设置警示标志和观测点并安排专人值守；9月30日过水断面恢复，永久治理方案已编制，施工中。',
 'emhaz05');

-- ============================================================
-- 8. 物资目录（slaifi），物资类别 type=#2# 应急物资
--    物资分类：#1#金属管材 #ezcp#阀门管件 #gjdc#水泥建材 #wbfy#电气设备
--             #scpx#五金工具 #ziyi#劳保用品 #iiuh#化学药剂 #nlej#仪表仪器 #cbzx#其他
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
(id, corp_code, created_at, created_by, updated_at, updated_by,
 higsdd, avlsdv, jwimko, fqztqm, iwnyuk, boipik, unqvqe, fmewia, epvuta, type) VALUES
('emmat01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-001','防汛编织袋','#cbzx#','安庆市华丰塑编有限公司','50kg/条','条','50000','pjyKVSZCduA8ul0ucnc','聚丙烯编织袋，用于渠道抢险堵漏和临时围堰填筑','#2#'),
('emmat02','hltgq','2026-06-01 09:21:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:21:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-002','彩条布','#cbzx#','安庆市华丰塑编有限公司','6m×50m','平方米','3000','pjyKVSZCduA8ul0ucnc','PE双覆膜彩条布，用于渠堤防雨覆盖和临时挡水','#2#'),
('emmat03','hltgq','2026-06-01 09:22:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:22:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-003','土工布','#gjdc#','山东路德新材料股份有限公司','400g/平方米','平方米','20000','pjyKVSZCduA8ul0ucnc','无纺土工布，用于反滤压渗和渠堤防护','#2#'),
('emmat04','hltgq','2026-06-01 09:23:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:23:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-004','松木桩','#cbzx#','太湖县林源木材加工厂','直径12cm×长4m','根','400','pjyKVSZCduA8ul0ucnc','用于渠堤坡脚抗滑和导渗沟支护','#2#'),
('emmat05','hltgq','2026-06-01 09:24:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:24:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-005','块石','#gjdc#','太湖县徐桥采石场','20-40cm','立方米','300','mltTVFIVmbNUgQgVqqM','用于抛石护脚、坡脚回填压重和反滤压渗','#2#'),
('emmat06','hltgq','2026-06-01 09:25:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:25:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-006','中砂','#gjdc#','太湖县徐桥砂石场','中粗砂','立方米','200','mltTVFIVmbNUgQgVqqM','用于反滤层铺筑和混凝土抢修配料','#2#'),
('emmat07','hltgq','2026-06-01 09:26:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:26:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-007','潜水泵','#wbfy#','上海连成（集团）有限公司','QY65-25-7.5','台','8','mltTVFIVmbNUgQgVqqM','用于渠道退水和基坑排涝，配套软管和电缆','#2#'),
('emmat08','hltgq','2026-06-02 09:30:00','A9jEKZmJhcmUEK4RCZo','2026-06-02 09:30:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-008','移动式柴油发电机','#wbfy#','江苏星光发电设备有限公司','50kW','台','2','96sbKxyuOP8xlbqTJfF','用于闸站启闭、视频监控和应急照明供电','#2#'),
('emmat09','hltgq','2026-06-02 09:31:00','A9jEKZmJhcmUEK4RCZo','2026-06-02 09:31:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-009','应急照明灯','#wbfy#','浙江正辉照明工程有限公司','LED 2×50W','套','15','96sbKxyuOP8xlbqTJfF','充电式移动照明灯，用于夜间抢险现场照明','#2#'),
('emmat10','hltgq','2026-06-02 09:32:00','A9jEKZmJhcmUEK4RCZo','2026-06-02 09:32:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-010','救生衣','#ziyi#','宁波海神救生设备有限公司','成人款','件','80','96sbKxyuOP8xlbqTJfF','抢险人员和转移群众使用，配备反光标识和哨子','#2#'),
('emmat11','hltgq','2026-06-01 09:27:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:27:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-011','雨衣雨靴套装','#ziyi#','安徽华信劳保用品有限公司','分体式','套','60','pjyKVSZCduA8ul0ucnc','抢险人员雨天作业防护用品','#2#'),
('emmat12','hltgq','2026-06-01 09:28:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:28:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-012','对讲机','#nlej#','海能达通信股份有限公司','PD-508','部','15','pjyKVSZCduA8ul0ucnc','抢险现场通信，单机续航不低于12小时','#2#'),
('emmat13','hltgq','2026-06-01 09:29:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:29:00','A9jEKZmJhcmUEK4RCZo',
 'YJWZ-2026-013','抢险工具包','#scpx#','安徽鑫盾五金工具厂','12件套','套','25','pjyKVSZCduA8ul0ucnc','含铁锹、镐、撬棍、手锯、扳手等常用抢险工具','#2#');

-- ============================================================
-- 9. 物资台账（tzkioe）：库存状态 #1#正常 / #2#偏低（库存不高于安全库存）
--    台账按「物资 + 仓库」唯一，仓库与科室取自仓库档案
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
(id, corp_code, created_at, created_by, updated_at, updated_by,
 jzikio, qhtxml, wpxwwi, hughww, mddokm, kxhsks, ejgxlq, vhvhja, mojktw, czdwpj, oywydb) VALUES
('emled01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo',
 'emmat01','安庆市华丰塑编有限公司','防汛编织袋','50kg/条','条','条',80000,50000,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc'),
('emled02','hltgq','2026-06-01 09:21:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:21:00','A9jEKZmJhcmUEK4RCZo',
 'emmat02','安庆市华丰塑编有限公司','彩条布','6m×50m','平方米','平方米',4500,3000,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc'),
('emled03','hltgq','2026-06-01 09:22:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:22:00','A9jEKZmJhcmUEK4RCZo',
 'emmat03','山东路德新材料股份有限公司','土工布','400g/平方米','平方米','平方米',30000,20000,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc'),
('emled04','hltgq','2026-06-01 09:23:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:23:00','A9jEKZmJhcmUEK4RCZo',
 'emmat04','太湖县林源木材加工厂','松木桩','直径12cm×长4m','根','根',500,400,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc'),
('emled05','hltgq','2026-06-01 10:05:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 10:05:00','A9jEKZmJhcmUEK4RCZo',
 'emmat05','太湖县徐桥采石场','块石','20-40cm','立方米','立方米',372,300,'#1#','OAFAaweGr78i4HB1bC2','mltTVFIVmbNUgQgVqqM'),
('emled06','hltgq','2026-06-01 10:05:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 10:05:00','A9jEKZmJhcmUEK4RCZo',
 'emmat06','太湖县徐桥砂石场','中砂','中粗砂','立方米','立方米',238,200,'#1#','OAFAaweGr78i4HB1bC2','mltTVFIVmbNUgQgVqqM'),
('emled07','hltgq','2026-06-01 10:05:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 10:05:00','A9jEKZmJhcmUEK4RCZo',
 'emmat07','上海连成（集团）有限公司','潜水泵','QY65-25-7.5','台','台',10,8,'#1#','OAFAaweGr78i4HB1bC2','mltTVFIVmbNUgQgVqqM'),
('emled08','hltgq','2026-06-02 09:30:00','A9jEKZmJhcmUEK4RCZo','2026-06-02 09:30:00','A9jEKZmJhcmUEK4RCZo',
 'emmat08','江苏星光发电设备有限公司','移动式柴油发电机','50kW','台','台',2,2,'#2#','NwHOOk2bI4LqYM3CVl9','96sbKxyuOP8xlbqTJfF'),
('emled09','hltgq','2026-06-02 09:31:00','A9jEKZmJhcmUEK4RCZo','2026-06-02 09:31:00','A9jEKZmJhcmUEK4RCZo',
 'emmat09','浙江正辉照明工程有限公司','应急照明灯','LED 2×50W','套','套',20,15,'#1#','NwHOOk2bI4LqYM3CVl9','96sbKxyuOP8xlbqTJfF'),
('emled10','hltgq','2026-06-02 09:32:00','A9jEKZmJhcmUEK4RCZo','2026-06-02 09:32:00','A9jEKZmJhcmUEK4RCZo',
 'emmat10','宁波海神救生设备有限公司','救生衣','成人款','件','件',100,80,'#1#','NwHOOk2bI4LqYM3CVl9','96sbKxyuOP8xlbqTJfF'),
('emled11','hltgq','2026-06-01 09:27:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:27:00','A9jEKZmJhcmUEK4RCZo',
 'emmat11','安徽华信劳保用品有限公司','雨衣雨靴套装','分体式','套','套',80,60,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc'),
('emled12','hltgq','2026-06-01 09:28:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:28:00','A9jEKZmJhcmUEK4RCZo',
 'emmat12','海能达通信股份有限公司','对讲机','PD-508','部','部',20,15,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc'),
('emled13','hltgq','2026-06-01 09:29:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:29:00','A9jEKZmJhcmUEK4RCZo',
 'emmat13','安徽鑫盾五金工具厂','抢险工具包','12件套','套','套',30,25,'#1#','ggEKzLyMEG7nD55XjIW','pjyKVSZCduA8ul0ucnc');

-- ============================================================
-- 10. 入库单（3 单 / 13 明细 / 13 流水）
--     入库类型：#1#采购入库 #2#调拨入库 #3#退料入库 #4#其他入库
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_inbound_order
(id, order_no, inbound_type, order_date, warehouse_id, source_unit, department_id,
 operator_id, operator_name, remark, del_flag, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emin001','RK202606010001','#1#','2026-06-01','pjyKVSZCduA8ul0ucnc','安庆市防汛抗旱物资供应商','ggEKzLyMEG7nD55XjIW',
 'A9jEKZmJhcmUEK4RCZo','李磊','汛前应急物资集中采购入库','#2#','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emin002','RK202606010002','#2#','2026-06-01','mltTVFIVmbNUgQgVqqM','花凉亭灌区主仓库','OAFAaweGr78i4HB1bC2',
 'VDLqdrLqoRQkmH7tltL','杨琪','主仓库调拨块石、中砂及潜水泵至1号仓库','#2#','hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emin003','RK202606020001','#4#','2026-06-02','96sbKxyuOP8xlbqTJfF','安庆市应急管理局','NwHOOk2bI4LqYM3CVl9',
 'IB21PXErczeQn3QCvie','余晓东','市级应急物资调拨补充入库','#2#','hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail
(id, order_id, material_id, material_code, material_name, material_brand, material_spec, material_unit,
 quantity, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emind001','emin001','emmat01','YJWZ-2026-001','防汛编织袋','安庆市华丰塑编有限公司','50kg/条','条',92000,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind002','emin001','emmat02','YJWZ-2026-002','彩条布','安庆市华丰塑编有限公司','6m×50m','平方米',6000,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind003','emin001','emmat03','YJWZ-2026-003','土工布','山东路德新材料股份有限公司','400g/平方米','平方米',32000,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind004','emin001','emmat04','YJWZ-2026-004','松木桩','太湖县林源木材加工厂','直径12cm×长4m','根',560,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind005','emin001','emmat11','YJWZ-2026-011','雨衣雨靴套装','安徽华信劳保用品有限公司','分体式','套',100,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind006','emin001','emmat12','YJWZ-2026-012','对讲机','海能达通信股份有限公司','PD-508','部',20,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind007','emin001','emmat13','YJWZ-2026-013','抢险工具包','安徽鑫盾五金工具厂','12件套','套',30,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emind008','emin002','emmat05','YJWZ-2026-005','块石','太湖县徐桥采石场','20-40cm','立方米',400,'hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emind009','emin002','emmat06','YJWZ-2026-006','中砂','太湖县徐桥砂石场','中粗砂','立方米',300,'hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emind010','emin002','emmat07','YJWZ-2026-007','潜水泵','上海连成（集团）有限公司','QY65-25-7.5','台',10,'hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emind011','emin003','emmat08','YJWZ-2026-008','移动式柴油发电机','江苏星光发电设备有限公司','50kW','台',3,'hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie'),
('emind012','emin003','emmat09','YJWZ-2026-009','应急照明灯','浙江正辉照明工程有限公司','LED 2×50W','套',30,'hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie'),
('emind013','emin003','emmat10','YJWZ-2026-010','救生衣','宁波海神救生设备有限公司','成人款','件',120,'hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stock_change_log
(id, ledger_id, material_id, warehouse_id, change_type, order_kind, order_id, order_no, biz_type,
 change_qty, qty_before, qty_after, counterparty, operator_name, order_date, corp_code,
 created_at, created_by, updated_at, updated_by) VALUES
('emlog001','emled01','emmat01','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',92000,0,92000,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog002','emled02','emmat02','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',6000,0,6000,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog003','emled03','emmat03','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',32000,0,32000,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog004','emled04','emmat04','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',560,0,560,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog005','emled11','emmat11','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',100,0,100,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog006','emled12','emmat12','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',20,0,20,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog007','emled13','emmat13','pjyKVSZCduA8ul0ucnc','#1#','#1#','emin001','RK202606010001','#1#',30,0,30,'安庆市防汛抗旱物资供应商','李磊','2026-06-01','hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emlog008','emled05','emmat05','mltTVFIVmbNUgQgVqqM','#1#','#1#','emin002','RK202606010002','#2#',400,0,400,'花凉亭灌区主仓库','杨琪','2026-06-01','hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emlog009','emled06','emmat06','mltTVFIVmbNUgQgVqqM','#1#','#1#','emin002','RK202606010002','#2#',300,0,300,'花凉亭灌区主仓库','杨琪','2026-06-01','hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emlog010','emled07','emmat07','mltTVFIVmbNUgQgVqqM','#1#','#1#','emin002','RK202606010002','#2#',10,0,10,'花凉亭灌区主仓库','杨琪','2026-06-01','hltgq','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL','2026-06-01 10:05:00','VDLqdrLqoRQkmH7tltL'),
('emlog011','emled08','emmat08','96sbKxyuOP8xlbqTJfF','#1#','#1#','emin003','RK202606020001','#4#',3,0,3,'安庆市应急管理局','余晓东','2026-06-02','hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie'),
('emlog012','emled09','emmat09','96sbKxyuOP8xlbqTJfF','#1#','#1#','emin003','RK202606020001','#4#',30,0,30,'安庆市应急管理局','余晓东','2026-06-02','hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie'),
('emlog013','emled10','emmat10','96sbKxyuOP8xlbqTJfF','#1#','#1#','emin003','RK202606020001','#4#',120,0,120,'安庆市应急管理局','余晓东','2026-06-02','hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie');

-- ============================================================
-- 11. 出库单（5 单 / 11 明细 / 11 流水）
--     出库类型：#1#工程领用 #2#调拨出库 #3#退料出库 #4#报废出库 #5#其他出库
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_outbound_order
(id, order_no, outbound_type, order_date, warehouse_id, receive_unit, department_id,
 operator_id, operator_name, remark, del_flag, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emck001','CK202607150001','#1#','2026-07-15','pjyKVSZCduA8ul0ucnc','东干渠徐桥段抢险队','ggEKzLyMEG7nD55XjIW',
 'ivU85mukdyTmOmlyfwY','甘武剑','东干渠K12+300渠堤渗漏抢险用料','#2#','hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emck002','CK202607150002','#1#','2026-07-15','mltTVFIVmbNUgQgVqqM','徐桥镇防汛抢险队','OAFAaweGr78i4HB1bC2',
 'EiCDnlpzopRMJzZKYS4','刘林海','徐桥段渠道抛石护脚和反滤压渗用料','#2#','hltgq','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4'),
('emck003','CK202609100001','#1#','2026-09-10','96sbKxyuOP8xlbqTJfF','南干渠节制闸抢修组','NwHOOk2bI4LqYM3CVl9',
 'sN1whojZaDfmArRp7GA','万江淮','启闭机抢修现场供电、照明及人员防护用品','#2#','hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emck004','CK202609100002','#1#','2026-09-10','pjyKVSZCduA8ul0ucnc','南干渠节制闸抢修组','ggEKzLyMEG7nD55XjIW',
 'ivU85mukdyTmOmlyfwY','甘武剑','抢修人员雨天作业防护用品','#2#','hltgq','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY'),
('emck005','CK202609120001','#2#','2026-09-12','mltTVFIVmbNUgQgVqqM','西干渠怀宁段维修养护施工队','OAFAaweGr78i4HB1bC2',
 'VDLqdrLqoRQkmH7tltL','杨琪','西干渠怀宁段渠道维修养护调拨用砂','#2#','hltgq','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail
(id, order_id, material_id, material_code, material_name, material_brand, material_spec, material_unit,
 quantity, stock_before, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emckd001','emck001','emmat01','YJWZ-2026-001','防汛编织袋','安庆市华丰塑编有限公司','50kg/条','条',12000,92000,'hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emckd002','emck001','emmat02','YJWZ-2026-002','彩条布','安庆市华丰塑编有限公司','6m×50m','平方米',1500,6000,'hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emckd003','emck001','emmat03','YJWZ-2026-003','土工布','山东路德新材料股份有限公司','400g/平方米','平方米',2000,32000,'hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emckd004','emck001','emmat04','YJWZ-2026-004','松木桩','太湖县林源木材加工厂','直径12cm×长4m','根',60,560,'hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emckd005','emck002','emmat05','YJWZ-2026-005','块石','太湖县徐桥采石场','20-40cm','立方米',30,400,'hltgq','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4'),
('emckd006','emck002','emmat06','YJWZ-2026-006','中砂','太湖县徐桥砂石场','中粗砂','立方米',25,300,'hltgq','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4'),
('emckd007','emck003','emmat08','YJWZ-2026-008','移动式柴油发电机','江苏星光发电设备有限公司','50kW','台',1,3,'hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emckd008','emck003','emmat09','YJWZ-2026-009','应急照明灯','浙江正辉照明工程有限公司','LED 2×50W','套',10,30,'hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emckd009','emck003','emmat10','YJWZ-2026-010','救生衣','宁波海神救生设备有限公司','成人款','件',20,120,'hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emckd010','emck004','emmat11','YJWZ-2026-011','雨衣雨靴套装','安徽华信劳保用品有限公司','分体式','套',20,100,'hltgq','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY'),
('emckd011','emck005','emmat06','YJWZ-2026-006','中砂','太湖县徐桥砂石场','中粗砂','立方米',35,275,'hltgq','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stock_change_log
(id, ledger_id, material_id, warehouse_id, change_type, order_kind, order_id, order_no, biz_type,
 change_qty, qty_before, qty_after, counterparty, operator_name, order_date, corp_code,
 created_at, created_by, updated_at, updated_by) VALUES
('emlog014','emled01','emmat01','pjyKVSZCduA8ul0ucnc','#2#','#2#','emck001','CK202607150001','#1#',12000,92000,80000,'东干渠徐桥段抢险队','甘武剑','2026-07-15','hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emlog015','emled02','emmat02','pjyKVSZCduA8ul0ucnc','#2#','#2#','emck001','CK202607150001','#1#',1500,6000,4500,'东干渠徐桥段抢险队','甘武剑','2026-07-15','hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emlog016','emled03','emmat03','pjyKVSZCduA8ul0ucnc','#2#','#2#','emck001','CK202607150001','#1#',2000,32000,30000,'东干渠徐桥段抢险队','甘武剑','2026-07-15','hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emlog017','emled04','emmat04','pjyKVSZCduA8ul0ucnc','#2#','#2#','emck001','CK202607150001','#1#',60,560,500,'东干渠徐桥段抢险队','甘武剑','2026-07-15','hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY'),
('emlog018','emled05','emmat05','mltTVFIVmbNUgQgVqqM','#2#','#2#','emck002','CK202607150002','#1#',30,400,370,'徐桥镇防汛抢险队','刘林海','2026-07-15','hltgq','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4'),
('emlog019','emled06','emmat06','mltTVFIVmbNUgQgVqqM','#2#','#2#','emck002','CK202607150002','#1#',25,300,275,'徐桥镇防汛抢险队','刘林海','2026-07-15','hltgq','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4'),
('emlog020','emled08','emmat08','96sbKxyuOP8xlbqTJfF','#2#','#2#','emck003','CK202609100001','#1#',1,3,2,'南干渠节制闸抢修组','万江淮','2026-09-10','hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emlog021','emled09','emmat09','96sbKxyuOP8xlbqTJfF','#2#','#2#','emck003','CK202609100001','#1#',10,30,20,'南干渠节制闸抢修组','万江淮','2026-09-10','hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emlog022','emled10','emmat10','96sbKxyuOP8xlbqTJfF','#2#','#2#','emck003','CK202609100001','#1#',20,120,100,'南干渠节制闸抢修组','万江淮','2026-09-10','hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA'),
('emlog023','emled11','emmat11','pjyKVSZCduA8ul0ucnc','#2#','#2#','emck004','CK202609100002','#1#',20,100,80,'南干渠节制闸抢修组','甘武剑','2026-09-10','hltgq','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY'),
('emlog024','emled06','emmat06','mltTVFIVmbNUgQgVqqM','#2#','#2#','emck005','CK202609120001','#2#',35,275,240,'西干渠怀宁段维修养护施工队','杨琪','2026-09-12','hltgq','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL');

-- ============================================================
-- 12. 设备盘点（2 单 / 10 明细 / 2 条盘盈盘亏流水）
--     仅差异行写流水：盘盈 #3#、盘亏 #4#，order_kind=#3#，biz_type=#3#
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stocktake_order
(id, order_no, check_date, warehouse_id, operator_id, operator_name, remark,
 del_flag, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emst001','PDD202609150001','2026-09-15','mltTVFIVmbNUgQgVqqM','VDLqdrLqoRQkmH7tltL','杨琪','1号仓库第三季度物资盘点，块石盘盈、中砂盘亏','#2#','hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emst002','PDD202609200001','2026-09-20','pjyKVSZCduA8ul0ucnc','VDLqdrLqoRQkmH7tltL','杨琪','主仓库第三季度物资盘点，账实相符','#2#','hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail
(id, order_id, material_id, material_code, material_name, material_brand, material_spec, material_unit,
 book_qty, check_qty, profit_qty, loss_qty, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emstd001','emst001','emmat05','YJWZ-2026-005','块石','太湖县徐桥采石场','20-40cm','立方米',370,372,2,0,'hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd002','emst001','emmat06','YJWZ-2026-006','中砂','太湖县徐桥砂石场','中粗砂','立方米',240,238,0,2,'hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd003','emst001','emmat07','YJWZ-2026-007','潜水泵','上海连成（集团）有限公司','QY65-25-7.5','台',10,10,0,0,'hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd004','emst002','emmat01','YJWZ-2026-001','防汛编织袋','安庆市华丰塑编有限公司','50kg/条','条',80000,80000,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd005','emst002','emmat02','YJWZ-2026-002','彩条布','安庆市华丰塑编有限公司','6m×50m','平方米',4500,4500,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd006','emst002','emmat03','YJWZ-2026-003','土工布','山东路德新材料股份有限公司','400g/平方米','平方米',30000,30000,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd007','emst002','emmat04','YJWZ-2026-004','松木桩','太湖县林源木材加工厂','直径12cm×长4m','根',500,500,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd008','emst002','emmat11','YJWZ-2026-011','雨衣雨靴套装','安徽华信劳保用品有限公司','分体式','套',80,80,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd009','emst002','emmat12','YJWZ-2026-012','对讲机','海能达通信股份有限公司','PD-508','部',20,20,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emstd010','emst002','emmat13','YJWZ-2026-013','抢险工具包','安徽鑫盾五金工具厂','12件套','套',30,30,0,0,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stock_change_log
(id, ledger_id, material_id, warehouse_id, change_type, order_kind, order_id, order_no, biz_type,
 change_qty, qty_before, qty_after, counterparty, operator_name, order_date, corp_code,
 created_at, created_by, updated_at, updated_by) VALUES
('emlog025','emled05','emmat05','mltTVFIVmbNUgQgVqqM','#3#','#3#','emst001','PDD202609150001','#3#',2,370,372,NULL,'杨琪','2026-09-15','hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emlog026','emled06','emmat06','mltTVFIVmbNUgQgVqqM','#4#','#3#','emst001','PDD202609150001','#3#',2,240,238,NULL,'杨琪','2026-09-15','hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL');

-- ============================================================
-- 13. 单号序列回填：避免平台新建单据与脚本单号撞号
--     唯一约束 (serial_type, serial_date)，重复执行取较大值
--     注意：ON CONFLICT 更新时裸写 current_seq 会报 column reference ambiguous，
--           必须用表名限定目标表那一侧的值。
-- ============================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_order_no_seq
(id, serial_type, serial_date, current_seq, corp_code, created_at, created_by, updated_at, updated_by) VALUES
('emseq01','RK','20260601',2,'hltgq','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo','2026-06-01 09:20:00','A9jEKZmJhcmUEK4RCZo'),
('emseq02','RK','20260602',1,'hltgq','2026-06-02 09:30:00','IB21PXErczeQn3QCvie','2026-06-02 09:30:00','IB21PXErczeQn3QCvie'),
('emseq03','CK','20260715',2,'hltgq','2026-07-15 14:50:00','ivU85mukdyTmOmlyfwY','2026-07-15 15:20:00','EiCDnlpzopRMJzZKYS4'),
('emseq04','CK','20260910',2,'hltgq','2026-09-10 11:20:00','sN1whojZaDfmArRp7GA','2026-09-10 15:40:00','ivU85mukdyTmOmlyfwY'),
('emseq05','CK','20260912',1,'hltgq','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL','2026-09-12 10:00:00','VDLqdrLqoRQkmH7tltL'),
('emseq06','PDD','20260915',1,'hltgq','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-15 09:00:00','VDLqdrLqoRQkmH7tltL'),
('emseq07','PDD','20260920',1,'hltgq','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL','2026-09-20 09:00:00','VDLqdrLqoRQkmH7tltL')
ON CONFLICT (serial_type, serial_date) DO UPDATE
SET current_seq = GREATEST(t_auto_hltgq_water_order_no_seq.current_seq, EXCLUDED.current_seq);

COMMIT;

-- ============================================================
-- 14. 执行后自检（只读，可整段执行核对）
-- ============================================================
-- 14.1 应急模块各表条数
-- SELECT '应急组织' AS 菜单, COUNT(*) AS 条数 FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_faiczc WHERE corp_code='hltgq'
-- UNION ALL SELECT '灾害等级', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_gzbifm WHERE corp_code='hltgq'
-- UNION ALL SELECT '应急事件', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_bewqdn WHERE corp_code='hltgq'
-- UNION ALL SELECT '应急保障', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_ifjcmy WHERE corp_code='hltgq'
-- UNION ALL SELECT '应急通知', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_lmqaew WHERE corp_code='hltgq'
-- UNION ALL SELECT '危险源', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_yxgojn WHERE corp_code='hltgq'
-- UNION ALL SELECT '危险发生记录', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_bcmxdv_eqjqmx WHERE corp_code='hltgq';
--
-- 14.2 应急物资目录与台账
-- SELECT COUNT(*) AS 应急物资 FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi WHERE corp_code='hltgq' AND id LIKE 'emmat%';
-- SELECT COUNT(*) AS 台账行 FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe WHERE corp_code='hltgq' AND id LIKE 'emled%';
--
-- 14.3 台账期末与流水末端一致性（应返回 0 行）
-- SELECT l.id, l.wpxwwi, l.ejgxlq, c.qty_after
-- FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe l
-- JOIN "qixiao-apaas".t_auto_hltgq_water_stock_change_log c ON c.ledger_id = l.id
-- WHERE l.id LIKE 'emled%'
--   AND c.created_at = (SELECT MAX(c2.created_at) FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log c2 WHERE c2.ledger_id = l.id)
--   AND c.qty_after <> l.ejgxlq;
--
-- 14.4 出入库与盘点单据条数
-- SELECT '入库单' AS 类别, COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order WHERE id LIKE 'emin%'
-- UNION ALL SELECT '入库明细', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail WHERE id LIKE 'emind%'
-- UNION ALL SELECT '出库单', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order WHERE id LIKE 'emck%'
-- UNION ALL SELECT '出库明细', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail WHERE id LIKE 'emckd%'
-- UNION ALL SELECT '盘点单', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order WHERE id LIKE 'emst%'
-- UNION ALL SELECT '盘点明细', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail WHERE id LIKE 'emstd%'
-- UNION ALL SELECT '变动流水', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log WHERE id LIKE 'emlog%';
