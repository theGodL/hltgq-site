-- ============================================================================
-- 库存管理（常用物资）正式数据整改脚本
-- 覆盖菜单：仓库管理 / 物资目录 / 物资台账 / 出入库管理 / 设备盘点
-- ----------------------------------------------------------------------------
-- 一、整改原则
--   1. 只处理「常用物资」（t_auto_hltgq_knc3g_slaifi.type 不为 #2#）；应急物资
--      （type=#2#，YJWZ-2026-001~013）及其台账、出入库单、流水、盘点单一律不动，
--      所有 DELETE 均追加「非应急」兜底（排除 em* 前缀单号 + 目录类别二次判定），并统一
--      排除本脚本自有种子数据（cw* 前缀）；重跑会在第 3 节主键冲突处整体回滚，
--      不会留下中间删除状态。
--   2. 清理 2026-10-12 及以后产生的全部测试单据、测试盘点、测试流水、测试台账与
--      目录档案（10-12 上线核验、10-15 与 10-21 手工测试批次，以及 10-27 只取号
--      未落单的序列残留；名称如「物资1/物资2/二极管/螺丝/6~20」的目录等）。
--   3. 重建 18 种水利工程常用物资，主仓库、望江分库、宿松分库三个在用仓库
--      的完整业务闭环：采购入库 → 调拨/领用/退料/报废出库 → 季度盘点（含盘盈
--      盘亏），时间线 2026-02（建档）至 2026-10-16。
--   4. 台账期末数量 = 累计入库 - 累计出库 ± 盘点调整；每条流水 qty_before /
--      qty_after 与前后笔首尾相接；台账 vhvhja/mojktw 按目录安全库存重算。
--   5. 太湖分库保留为停用状态仓库（库房迁址），用于验证停用仓库不可收发货。
-- 二、执行方式
--   本脚本整体在事务中执行，任意语句失败自动回滚；请用 UTF-8 客户端整段执行。
--   执行后可用文末「核对查询」逐项核对，预期值已写在注释中。
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. 清理测试数据（先子表后主表；应急物资相关数据不在清理范围内）
-- ============================================================================

-- 1.1 盘点附件：仅 2 个测试盘点附件（应用导出和安装.docx / 出入库单据明细.xlsx）
--     注意：此处只删库表记录，file-dir/stocktake 下的物理文件需另行人工清理
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_attachment
 WHERE order_id IN (SELECT id FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order
                     WHERE corp_code = 'hltgq'
                       AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
                       AND id NOT LIKE 'em%' AND id NOT LIKE 'cwst%');

-- 1.2 测试盘点明细：PDD202610120001、PDD202610150001~005、PDD202610210010 共 14 行
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail
 WHERE order_id IN (SELECT id FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order
                     WHERE corp_code = 'hltgq'
                       AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
                       AND id NOT LIKE 'em%' AND id NOT LIKE 'cwst%');

-- 1.3 测试盘点主表：7 张（应急盘点 PDD202609150001 / PDD202609200001 保留）
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order
 WHERE corp_code = 'hltgq'
   AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
   AND id NOT LIKE 'em%' AND id NOT LIKE 'cwst%';

-- 1.4 测试流水：2026-10-12 及以后产生共 67 条（应急流水最晚 2026-09-15，不受影响）
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log l
 WHERE l.corp_code = 'hltgq'
   AND l.created_at >= TIMESTAMP '2026-10-12 00:00:00'
   AND l.id NOT LIKE 'emlog%' AND l.id NOT LIKE 'cwlog%'
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi m
                    WHERE m.id = l.material_id
                      AND TRIM(COALESCE(m."type", '')) IN ('#2#', '应急物资'));

-- 1.5 出库明细 → 出库单：CK202610120001、CK202610150001~010、CK202610210001~002 共 13 张 / 明细 20 行
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail
 WHERE order_id IN (SELECT id FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order
                     WHERE corp_code = 'hltgq'
                       AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
                       AND id NOT LIKE 'em%' AND id NOT LIKE 'cwck%');
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order
 WHERE corp_code = 'hltgq'
   AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
   AND id NOT LIKE 'em%' AND id NOT LIKE 'cwck%';

-- 1.6 入库明细 → 入库单：RK202610120001、RK202610150001~008、RK202610210001~003 共 12 张 / 明细 37 行
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail
 WHERE order_id IN (SELECT id FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order
                     WHERE corp_code = 'hltgq'
                       AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
                       AND id NOT LIKE 'em%' AND id NOT LIKE 'cwin%');
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order
 WHERE corp_code = 'hltgq'
   AND created_at >= TIMESTAMP '2026-10-12 00:00:00'
   AND id NOT LIKE 'em%' AND id NOT LIKE 'cwin%';

-- 1.7 常用物资测试台账：仅删下列 21 个测试物资名下的台账行，共 27 行
--     （21 个目录分布在主仓库/1 号/2 号仓库，应急台账 emled01~13 保留）
DELETE FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
 WHERE corp_code = 'hltgq'
   AND id NOT LIKE 'cwled%'
   AND jzikio IN ('QQd8wNRlVgbIAefYQPi','6M0lxKCRBD5yzMFn7hg','V3prBzZgkFtE5yG04Ig','F20SadYTTnpSLx0soA9',
                  'RKwfN9ghdL5v6ZEcjo1','CUoSgP1XHoWp0T0q0oj','0m6CAMMlmFUtGQeJKGu','XhftKhXRnufOYlp8Vob',
                  '5r8xdDjsaItqqRm5HMX','SNAw3BEaoIhnHKYaLK1','PhX1LysofLw4liLnwRf','ejMAfpkQK9GyVf3vNVH',
                  'LtPpD8ZNjZrImyrWdFM','iSMrzUYfkjKlqc5gha9','FGCSXVUmJI6kxELrHSA','HG0BY9zhIzaoCRwnzob',
                  '4IcKX7UQ2K7pUmELMYc','tmq34nOKPHHEvblm3UV','S8G98NSUF7uopYoDBr3','YlKSWXNXhNjJk15wy6o',
                  'hFHOn0l2DakLBS9HXvA')
   AND jzikio IN (SELECT id FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
                   WHERE TRIM(COALESCE("type", '')) NOT IN ('#2#', '应急物资'));

-- 1.8 常用物资测试目录：21 条（编号 001 / WZ1792… / WZ17925…，名称物资1/物资2/二极管/螺丝/6~20 等）
DELETE FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
 WHERE corp_code = 'hltgq'
   AND TRIM(COALESCE("type", '')) NOT IN ('#2#', '应急物资')
   AND id NOT LIKE 'cwmat%'
   AND id IN ('QQd8wNRlVgbIAefYQPi','6M0lxKCRBD5yzMFn7hg','V3prBzZgkFtE5yG04Ig','F20SadYTTnpSLx0soA9',
              'RKwfN9ghdL5v6ZEcjo1','CUoSgP1XHoWp0T0q0oj','0m6CAMMlmFUtGQeJKGu','XhftKhXRnufOYlp8Vob',
              '5r8xdDjsaItqqRm5HMX','SNAw3BEaoIhnHKYaLK1','PhX1LysofLw4liLnwRf','ejMAfpkQK9GyVf3vNVH',
              'LtPpD8ZNjZrImyrWdFM','iSMrzUYfkjKlqc5gha9','FGCSXVUmJI6kxELrHSA','HG0BY9zhIzaoCRwnzob',
              '4IcKX7UQ2K7pUmELMYc','tmq34nOKPHHEvblm3UV','S8G98NSUF7uopYoDBr3','YlKSWXNXhNjJk15wy6o',
              'hFHOn0l2DakLBS9HXvA');

-- 1.9 测试取号序列：RK/CK/PDD 三类中不属于应急业务日期的行（YJBZ 属应急保障模块，保留）
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_order_no_seq
 WHERE corp_code = 'hltgq'
   AND serial_type IN ('RK', 'CK', 'PDD')
   AND id NOT LIKE 'cwsq%'
   AND NOT (
        (serial_type = 'RK'  AND serial_date IN ('20260601','20260602'))
     OR (serial_type = 'CK'  AND serial_date IN ('20260715','20260910','20260912'))
     OR (serial_type = 'PDD' AND serial_date IN ('20260915','20260920'))
   );

-- ============================================================================
-- 2. 仓库规范化（4 个仓库：3 个在用 + 1 个停用保留；id 不变，历史单据关联不受影响）
-- ============================================================================
UPDATE "qixiao-apaas".t_auto_hltgq_knc3g_twufyx
   SET xoysox = '花凉亭灌区主仓库',
       bcewqm = 'HLT-CK-01',
       aeleiw = '#1#',
       ikpaxg = '怀宁县高河镇花凉亭灌区管理处1号库房',
       naojhm = '灌区常用物资主库，承担集中采购、储备与调拨',
       created_at = TIMESTAMP '2025-11-18 09:00:00',
       uktvut = NULL,
       fbixxs = '15212773620',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'A9jEKZmJhcmUEK4RCZo'
 WHERE id = 'pjyKVSZCduA8ul0ucnc' AND corp_code = 'hltgq';

UPDATE "qixiao-apaas".t_auto_hltgq_knc3g_twufyx
   SET xoysox = '花凉亭灌区望江分库',
       bcewqm = 'HLT-CK-02',
       aeleiw = '#1#',
       ikpaxg = '望江县华阳镇花凉亭灌区望江管理中心院内',
       naojhm = '望江片区维修养护物资周转库',
       created_at = TIMESTAMP '2025-11-18 09:10:00',
       uktvut = NULL,
       fbixxs = '18956011517',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'A9jEKZmJhcmUEK4RCZo'
 WHERE id = 'mltTVFIVmbNUgQgVqqM' AND corp_code = 'hltgq';

UPDATE "qixiao-apaas".t_auto_hltgq_knc3g_twufyx
   SET xoysox = '花凉亭灌区宿松分库',
       bcewqm = 'HLT-CK-03',
       aeleiw = '#1#',
       ikpaxg = '宿松县孚玉镇花凉亭灌区宿松管理处院内',
       naojhm = '宿松片区堤防养护物资周转库',
       created_at = TIMESTAMP '2025-11-18 09:20:00',
       uktvut = NULL,
       fbixxs = '18955687087',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'A9jEKZmJhcmUEK4RCZo'
 WHERE id = '96sbKxyuOP8xlbqTJfF' AND corp_code = 'hltgq';

UPDATE "qixiao-apaas".t_auto_hltgq_knc3g_twufyx
   SET xoysox = '花凉亭灌区太湖分库',
       bcewqm = 'HLT-CK-04',
       aeleiw = '#zlji#',
       ikpaxg = '太湖县晋熙镇水利工程运行管理中心院内',
       naojhm = '库房迁址改造，暂停收发货',
       created_at = TIMESTAMP '2025-11-18 09:30:00',
       uktvut = NULL,
       fbixxs = '13855657702',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'A9jEKZmJhcmUEK4RCZo'
 WHERE id = 'AuEUDTZHv9QARqVUpCf' AND corp_code = 'hltgq';

-- ============================================================================
-- 3. 物资目录（常用物资 18 种；type 留空即常用，#1#/#2# 编码仅应急使用）
--    物资分类 jwimko 按平台字典取值：#1# 金属管材 / #ezcp# 阀门管件 / #gjdc# 水泥建材 /
--    #wbfy# 电气设备 / #scpx# 五金工具 / #ziyi# 劳保用品 / #iiuh# 化学药剂 /
--    #nlej# 仪表仪器 / #cbzx# 其他
--    说明：#1# 与 #iiuh# 在现存数据中无样本，若前端下拉未配置这两项而显示原编码，
--    可将钢丝绳/主轮/钢管改 #cbzx#、润滑脂/齿轮油改 #gjdc#，不影响任何计算口径
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
  (id, corp_code, created_at, created_by, updated_at, updated_by,
   higsdd, avlsdv, jwimko, fqztqm, iwnyuk, boipik, unqvqe, fmewia, epvuta, "type")
VALUES
 ('cwmat01','hltgq',TIMESTAMP '2026-02-18 09:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-18 09:10:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-001','闸门止水橡皮','#cbzx#','衡水恒润橡塑制品有限公司','P60型 30×60mm','米','200','pjyKVSZCduA8ul0ucnc','用于平面钢闸门止水密封，按闸门周长备料',NULL),
 ('cwmat02','hltgq',TIMESTAMP '2026-02-18 09:16:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-18 09:16:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-002','启闭机钢丝绳','#1#','江苏法尔胜泓昇集团','6×19-Φ16 镀锌','米','300','pjyKVSZCduA8ul0ucnc','卷扬式启闭机提升钢丝绳更换备件',NULL),
 ('cwmat03','hltgq',TIMESTAMP '2026-02-18 09:22:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-18 09:22:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-003','闸门主轮','#1#','河南黄河水工机械有限公司','400kN 铸钢','套','6','pjyKVSZCduA8ul0ucnc','平面闸门行走支承，单扇门配置 4 套',NULL),
 ('cwmat04','hltgq',TIMESTAMP '2026-02-18 09:28:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-18 09:28:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-004','钙基润滑脂','#iiuh#','中国石化长城润滑油','3# 15kg/桶','桶','16','pjyKVSZCduA8ul0ucnc','启闭机齿轮、钢丝绳及闸门滚轮润滑',NULL),
 ('cwmat05','hltgq',TIMESTAMP '2026-02-18 09:34:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-18 09:34:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-005','工业齿轮油','#iiuh#','中国石化长城润滑油','CKD220 18L/桶','桶','10','pjyKVSZCduA8ul0ucnc','卷扬式启闭机减速箱换油',NULL),
 ('cwmat06','hltgq',TIMESTAMP '2026-02-20 09:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-20 09:10:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-006','交流接触器','#wbfy#','正泰电器','CJX2-2510 220V','只','24','pjyKVSZCduA8ul0ucnc','启闭机控制柜主回路接触器备件',NULL),
 ('cwmat07','hltgq',TIMESTAMP '2026-02-20 09:16:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-20 09:16:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-007','塑壳断路器','#wbfy#','正泰电器','DZ47-63 C32 3P','只','24','pjyKVSZCduA8ul0ucnc','闸站配电箱分路保护开关备件',NULL),
 ('cwmat08','hltgq',TIMESTAMP '2026-02-20 09:22:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-20 09:22:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-008','低压动力电缆','#wbfy#','远东电缆股份有限公司','YJV22-4×25','米','400','pjyKVSZCduA8ul0ucnc','启闭机、闸站动力回路敷设与更换',NULL),
 ('cwmat09','hltgq',TIMESTAMP '2026-02-20 09:28:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-20 09:28:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-009','控制电缆','#wbfy#','远东电缆股份有限公司','KVVP-4×1.5','米','600','pjyKVSZCduA8ul0ucnc','启闭机开度、限位信号控制回路',NULL),
 ('cwmat10','hltgq',TIMESTAMP '2026-02-22 09:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-22 09:10:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-010','闸阀','#ezcp#','上海良工阀门厂','Z41H-16C DN100','台','6','pjyKVSZCduA8ul0ucnc','分干渠节制闸检修及管道更换',NULL),
 ('cwmat11','hltgq',TIMESTAMP '2026-02-22 09:16:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-22 09:16:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-011','镀锌钢管','#1#','天津友发钢管集团','DN100×6m','根','30','pjyKVSZCduA8ul0ucnc','放水涵管、闸阀连接管段维修',NULL),
 ('cwmat12','hltgq',TIMESTAMP '2026-02-22 09:22:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-22 09:22:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-012','橡胶止水带','#cbzx#','衡水恒润橡塑制品有限公司','651型 300×8mm','米','100','pjyKVSZCduA8ul0ucnc','渠系建筑物伸缩缝、沉降缝止水',NULL),
 ('cwmat13','hltgq',TIMESTAMP '2026-02-24 09:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-24 09:10:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-013','普通硅酸盐水泥','#gjdc#','芜湖海螺水泥有限公司','P.O42.5 散装','吨','20','pjyKVSZCduA8ul0ucnc','渠道衬砌、闸墩混凝土修补',NULL),
 ('cwmat14','hltgq',TIMESTAMP '2026-02-24 09:16:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-24 09:16:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-014','水工修补砂浆','#gjdc#','江苏苏博特新材料股份有限公司','JCT-Ⅱ 25kg/袋','袋','80','pjyKVSZCduA8ul0ucnc','混凝土剥蚀、裂缝修补，薄层加固',NULL),
 ('cwmat15','hltgq',TIMESTAMP '2026-02-24 09:22:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-24 09:22:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-015','渠道衬砌预制板','#gjdc#','望江县城南预制构件厂','50×50×8cm','块','500','pjyKVSZCduA8ul0ucnc','末级渠道衬砌破损修复',NULL),
 ('cwmat16','hltgq',TIMESTAMP '2026-02-26 09:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-26 09:10:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-016','安全警示桩','#cbzx#','安徽交安设施工程有限公司','玻璃钢 80cm 黄黑','根','80','96sbKxyuOP8xlbqTJfF','堤防、渠堤临水侧安全警示',NULL),
 ('cwmat17','hltgq',TIMESTAMP '2026-02-26 09:16:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-26 09:16:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-017','搪瓷水尺','#nlej#','河北天工水尺厂','1m 分度1cm','根','30','96sbKxyuOP8xlbqTJfF','渠道水位观测，搪瓷面板耐候',NULL),
 ('cwmat18','hltgq',TIMESTAMP '2026-02-26 09:22:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-02-26 09:22:00','A9jEKZmJhcmUEK4RCZo',
  'CW-2026-018','割灌机刀片','#scpx#','斯蒂尔动力设备','230×25.4 三刃','片','90','96sbKxyuOP8xlbqTJfF','堤坡除草割灌机耗材，以旧换新',NULL);

-- ============================================================================
-- 4. 物资台账（22 行：主仓 15、望江分库 4、宿松分库 3）
--    期末数量与流水末笔 qty_after 一致；安全库存快照取目录 unqvqe；
--    库存状态口径：数量 > 安全库存 → #1# 正常，否则 #2# 偏低（等于也算偏低）
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
  (id, corp_code, created_at, created_by, updated_at, updated_by,
   czdwpj, ejgxlq, vhvhja, kxhsks, mojktw, jzikio, qhtxml, wpxwwi, hughww, mddokm, oywydb)
VALUES
 ('cwled01','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',158,200,'米','#2#','cwmat01','衡水恒润橡塑制品有限公司','闸门止水橡皮','P60型 30×60mm','米','pjyKVSZCduA8ul0ucnc'),
 ('cwled02','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',370,300,'米','#1#','cwmat02','江苏法尔胜泓昇集团','启闭机钢丝绳','6×19-Φ16 镀锌','米','pjyKVSZCduA8ul0ucnc'),
 ('cwled03','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',8,6,'套','#1#','cwmat03','河南黄河水工机械有限公司','闸门主轮','400kN 铸钢','套','pjyKVSZCduA8ul0ucnc'),
 ('cwled04','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',22,16,'桶','#1#','cwmat04','中国石化长城润滑油','钙基润滑脂','3# 15kg/桶','桶','pjyKVSZCduA8ul0ucnc'),
 ('cwled05','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',17,10,'桶','#1#','cwmat05','中国石化长城润滑油','工业齿轮油','CKD220 18L/桶','桶','pjyKVSZCduA8ul0ucnc'),
 ('cwled06','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',34,24,'只','#1#','cwmat06','正泰电器','交流接触器','CJX2-2510 220V','只','pjyKVSZCduA8ul0ucnc'),
 ('cwled07','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',40,24,'只','#1#','cwmat07','正泰电器','塑壳断路器','DZ47-63 C32 3P','只','pjyKVSZCduA8ul0ucnc'),
 ('cwled08','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',792,400,'米','#1#','cwmat08','远东电缆股份有限公司','低压动力电缆','YJV22-4×25','米','pjyKVSZCduA8ul0ucnc'),
 ('cwled09','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',1200,600,'米','#1#','cwmat09','远东电缆股份有限公司','控制电缆','KVVP-4×1.5','米','pjyKVSZCduA8ul0ucnc'),
 ('cwled10','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',8,6,'台','#1#','cwmat10','上海良工阀门厂','闸阀','Z41H-16C DN100','台','pjyKVSZCduA8ul0ucnc'),
 ('cwled11','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',40,30,'根','#1#','cwmat11','天津友发钢管集团','镀锌钢管','DN100×6m','根','pjyKVSZCduA8ul0ucnc'),
 ('cwled12','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',120,100,'米','#1#','cwmat12','衡水恒润橡塑制品有限公司','橡胶止水带','651型 300×8mm','米','pjyKVSZCduA8ul0ucnc'),
 ('cwled13','hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',28,20,'吨','#1#','cwmat13','芜湖海螺水泥有限公司','普通硅酸盐水泥','P.O42.5 散装','吨','pjyKVSZCduA8ul0ucnc'),
 ('cwled14','hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',105,80,'袋','#1#','cwmat14','江苏苏博特新材料股份有限公司','水工修补砂浆','JCT-Ⅱ 25kg/袋','袋','pjyKVSZCduA8ul0ucnc'),
 ('cwled15','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',
  'ggEKzLyMEG7nD55XjIW',600,500,'块','#1#','cwmat15','望江县城南预制构件厂','渠道衬砌预制板','50×50×8cm','块','pjyKVSZCduA8ul0ucnc'),
 ('cwled16','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',
  'OAFAaweGr78i4HB1bC2',7,6,'台','#1#','cwmat10','上海良工阀门厂','闸阀','Z41H-16C DN100','台','mltTVFIVmbNUgQgVqqM'),
 ('cwled17','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',
  'OAFAaweGr78i4HB1bC2',37,30,'根','#1#','cwmat11','天津友发钢管集团','镀锌钢管','DN100×6m','根','mltTVFIVmbNUgQgVqqM'),
 ('cwled18','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',
  'OAFAaweGr78i4HB1bC2',75,100,'米','#2#','cwmat12','衡水恒润橡塑制品有限公司','橡胶止水带','651型 300×8mm','米','mltTVFIVmbNUgQgVqqM'),
 ('cwled19','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',
  'OAFAaweGr78i4HB1bC2',553,500,'块','#1#','cwmat15','望江县城南预制构件厂','渠道衬砌预制板','50×50×8cm','块','mltTVFIVmbNUgQgVqqM'),
 ('cwled20','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',
  'NwHOOk2bI4LqYM3CVl9',120,80,'根','#1#','cwmat16','安徽交安设施工程有限公司','安全警示桩','玻璃钢 80cm 黄黑','根','96sbKxyuOP8xlbqTJfF'),
 ('cwled21','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',
  'NwHOOk2bI4LqYM3CVl9',34,30,'根','#1#','cwmat17','河北天工水尺厂','搪瓷水尺','1m 分度1cm','根','96sbKxyuOP8xlbqTJfF'),
 ('cwled22','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',
  'NwHOOk2bI4LqYM3CVl9',63,90,'片','#2#','cwmat18','斯蒂尔动力设备','割灌机刀片','230×25.4 三刃','片','96sbKxyuOP8xlbqTJfF');

-- ============================================================================
-- 5. 入库单（8 张：采购 6 / 调拨 1 / 退料 1）与明细（27 行）
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_inbound_order
  (id, order_no, inbound_type, order_date, warehouse_id, source_unit, department_id,
   operator_id, operator_name, remark, del_flag, corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwin001','RK202603050001','#1#',DATE '2026-03-05','pjyKVSZCduA8ul0ucnc','安庆市水利物资供应站','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','汛前常用维修物资集中采购入库','#2#','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwin002','RK202604120001','#1#',DATE '2026-04-12','pjyKVSZCduA8ul0ucnc','正泰电器安庆销售中心','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','启闭机控制柜电气配件采购入库','#2#','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwin003','RK202604200001','#1#',DATE '2026-04-20','pjyKVSZCduA8ul0ucnc','安庆市水利物资供应站','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','渠道维修管材、止水材料及预制板采购入库','#2#','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwin004','RK202604280001','#2#',DATE '2026-04-28','mltTVFIVmbNUgQgVqqM','花凉亭灌区主仓库','OAFAaweGr78i4HB1bC2',
  'VDLqdrLqoRQkmH7tltL','杨琪','主仓库调拨周转物资至望江分库','#2#','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwin005','RK202605200001','#1#',DATE '2026-05-20','96sbKxyuOP8xlbqTJfF','安徽交安设施工程有限公司','NwHOOk2bI4LqYM3CVl9',
  'ivU85mukdyTmOmlyfwY','甘武剑','堤防养护警示设施及水尺采购入库','#2#','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwin006','RK202606050001','#1#',DATE '2026-06-05','mltTVFIVmbNUgQgVqqM','安庆市水利物资供应站','OAFAaweGr78i4HB1bC2',
  'VDLqdrLqoRQkmH7tltL','杨琪','太慈分干渠维修材料补充采购入库','#2#','hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwin007','RK202608150001','#3#',DATE '2026-08-15','mltTVFIVmbNUgQgVqqM','花凉亭灌区望江县管理中心工程股','OAFAaweGr78i4HB1bC2',
  'VDLqdrLqoRQkmH7tltL','杨琪','太慈分干渠维修剩余止水带退料入库','#2#','hltgq',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL'),
 ('cwin008','RK202608200001','#1#',DATE '2026-08-20','pjyKVSZCduA8ul0ucnc','芜湖海螺水泥安庆分销处','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','渠段衬砌修补水泥及修补砂浆采购入库','#2#','hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail
  (id, order_id, material_id, material_code, material_name, material_brand, material_spec,
   material_unit, quantity, corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwind001','cwin001','cwmat01','CW-2026-001','闸门止水橡皮','衡水恒润橡塑制品有限公司','P60型 30×60mm','米',300,'hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind002','cwin001','cwmat02','CW-2026-002','启闭机钢丝绳','江苏法尔胜泓昇集团','6×19-Φ16 镀锌','米',400,'hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind003','cwin001','cwmat03','CW-2026-003','闸门主轮','河南黄河水工机械有限公司','400kN 铸钢','套',12,'hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind004','cwin001','cwmat04','CW-2026-004','钙基润滑脂','中国石化长城润滑油','3# 15kg/桶','桶',30,'hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind005','cwin001','cwmat05','CW-2026-005','工业齿轮油','中国石化长城润滑油','CKD220 18L/桶','桶',20,'hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind006','cwin002','cwmat06','CW-2026-006','交流接触器','正泰电器','CJX2-2510 220V','只',40,'hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind007','cwin002','cwmat07','CW-2026-007','塑壳断路器','正泰电器','DZ47-63 C32 3P','只',40,'hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind008','cwin002','cwmat08','CW-2026-008','低压动力电缆','远东电缆股份有限公司','YJV22-4×25','米',800,'hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind009','cwin002','cwmat09','CW-2026-009','控制电缆','远东电缆股份有限公司','KVVP-4×1.5','米',1200,'hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind010','cwin003','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',12,'hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind011','cwin003','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',60,'hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind012','cwin003','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',180,'hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind013','cwin003','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',800,'hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind014','cwin004','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',4,'hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind015','cwin004','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',20,'hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind016','cwin004','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',60,'hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind017','cwin004','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',200,'hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind018','cwin005','cwmat16','CW-2026-016','安全警示桩','安徽交安设施工程有限公司','玻璃钢 80cm 黄黑','根',200,'hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwind019','cwin005','cwmat17','CW-2026-017','搪瓷水尺','河北天工水尺厂','1m 分度1cm','根',60,'hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwind020','cwin005','cwmat18','CW-2026-018','割灌机刀片','斯蒂尔动力设备','230×25.4 三刃','片',80,'hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwind021','cwin006','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',6,'hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind022','cwin006','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',30,'hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind023','cwin006','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',90,'hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind024','cwin006','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',500,'hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind025','cwin007','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',15,'hltgq',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL'),
 ('cwind026','cwin008','cwmat13','CW-2026-013','普通硅酸盐水泥','芜湖海螺水泥有限公司','P.O42.5 散装','吨',40,'hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwind027','cwin008','cwmat14','CW-2026-014','水工修补砂浆','江苏苏博特新材料股份有限公司','JCT-Ⅱ 25kg/袋','袋',150,'hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo');

-- ============================================================================
-- 6. 出库单（8 张：工程领用 5 / 调拨 1 / 报废 1 / 其他 1）与明细（21 行）
--    stock_before 与流水 qty_before 一致，为台账扣减前的库存快照
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_outbound_order
  (id, order_no, outbound_type, order_date, warehouse_id, receive_unit, department_id,
   operator_id, operator_name, remark, del_flag, corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwck001','CK202604280001','#2#',DATE '2026-04-28','pjyKVSZCduA8ul0ucnc','花凉亭灌区望江分库','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','调拨望江分库周转库存','#2#','hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwck002','CK202605180001','#1#',DATE '2026-05-18','pjyKVSZCduA8ul0ucnc','花凉亭灌区怀宁县管理所工程维修队','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','总干渠K8+200节制闸检修用料','#2#','hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwck003','CK202607220001','#1#',DATE '2026-07-22','96sbKxyuOP8xlbqTJfF','花凉亭灌区宿松管理处堤防养护队','NwHOOk2bI4LqYM3CVl9',
  'ivU85mukdyTmOmlyfwY','甘武剑','二郎河堤防警示桩补设及水尺更换','#2#','hltgq',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY'),
 ('cwck004','CK202608060001','#1#',DATE '2026-08-06','mltTVFIVmbNUgQgVqqM','花凉亭灌区望江县管理中心工程股','OAFAaweGr78i4HB1bC2',
  'VDLqdrLqoRQkmH7tltL','杨琪','太慈分干渠K4+600闸阀更换及管道维修','#2#','hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwck005','CK202609080001','#5#',DATE '2026-09-08','96sbKxyuOP8xlbqTJfF','花凉亭灌区宿松管理处','NwHOOk2bI4LqYM3CVl9',
  'ivU85mukdyTmOmlyfwY','甘武剑','割灌机刀片以旧换新领用','#2#','hltgq',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwck006','CK202609160001','#1#',DATE '2026-09-16','mltTVFIVmbNUgQgVqqM','花凉亭灌区望江县管理中心工程股','OAFAaweGr78i4HB1bC2',
  'VDLqdrLqoRQkmH7tltL','杨琪','太慈分干渠渠堤衬砌加固','#2#','hltgq',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL'),
 ('cwck007','CK202609250001','#1#',DATE '2026-09-25','pjyKVSZCduA8ul0ucnc','花凉亭灌区怀宁县管理所渠道养护队','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','西干渠K3+000~K5+500渠段衬砌修补','#2#','hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwck008','CK202610090001','#4#',DATE '2026-10-09','pjyKVSZCduA8ul0ucnc','花凉亭灌区怀宁县管理所','ggEKzLyMEG7nD55XjIW',
  'A9jEKZmJhcmUEK4RCZo','李磊','库存钢丝绳受潮锈蚀报废处理','#2#','hltgq',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail
  (id, order_id, material_id, material_code, material_name, material_brand, material_spec,
   material_unit, quantity, stock_before, corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwckd001','cwck001','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',4,12,'hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd002','cwck001','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',20,60,'hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd003','cwck001','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',60,180,'hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd004','cwck001','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',200,800,'hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd005','cwck002','cwmat01','CW-2026-001','闸门止水橡皮','衡水恒润橡塑制品有限公司','P60型 30×60mm','米',80,300,'hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd006','cwck002','cwmat03','CW-2026-003','闸门主轮','河南黄河水工机械有限公司','400kN 铸钢','套',4,12,'hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd007','cwck002','cwmat04','CW-2026-004','钙基润滑脂','中国石化长城润滑油','3# 15kg/桶','桶',8,30,'hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd008','cwck002','cwmat06','CW-2026-006','交流接触器','正泰电器','CJX2-2510 220V','只',6,40,'hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd009','cwck002','cwmat05','CW-2026-005','工业齿轮油','中国石化长城润滑油','CKD220 18L/桶','桶',4,20,'hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd010','cwck003','cwmat16','CW-2026-016','安全警示桩','安徽交安设施工程有限公司','玻璃钢 80cm 黄黑','根',80,200,'hltgq',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY'),
 ('cwckd011','cwck003','cwmat17','CW-2026-017','搪瓷水尺','河北天工水尺厂','1m 分度1cm','根',25,60,'hltgq',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY'),
 ('cwckd012','cwck004','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',3,10,'hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwckd013','cwck004','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',12,50,'hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwckd014','cwck004','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',60,150,'hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwckd015','cwck005','cwmat18','CW-2026-018','割灌机刀片','斯蒂尔动力设备','230×25.4 三刃','片',15,80,'hltgq',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwckd016','cwck006','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',150,700,'hltgq',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL'),
 ('cwckd017','cwck006','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',30,105,'hltgq',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL'),
 ('cwckd018','cwck007','cwmat13','CW-2026-013','普通硅酸盐水泥','芜湖海螺水泥有限公司','P.O42.5 散装','吨',12,40,'hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd019','cwck007','cwmat14','CW-2026-014','水工修补砂浆','江苏苏博特新材料股份有限公司','JCT-Ⅱ 25kg/袋','袋',45,150,'hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd020','cwck007','cwmat01','CW-2026-001','闸门止水橡皮','衡水恒润橡塑制品有限公司','P60型 30×60mm','米',60,220,'hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwckd021','cwck008','cwmat02','CW-2026-002','启闭机钢丝绳','江苏法尔胜泓昇集团','6×19-Φ16 镀锌','米',30,400,'hltgq',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo');

-- ============================================================================
-- 7. 设备盘点（3 张季度盘点单 / 22 行明细）
--    账面库存取盘点时点台账值；实盘与账面差异写盘盈盘亏，台账绝对校正
--    book_qty 为历史时点快照，与当前台账期末值不同属正常（编辑仅限备注/附件/盘点人）
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stocktake_order
  (id, order_no, check_date, warehouse_id, operator_id, operator_name, remark, del_flag,
   corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwst001','PDD202610080001',DATE '2026-10-08','pjyKVSZCduA8ul0ucnc','A9jEKZmJhcmUEK4RCZo','李磊',
  '主仓库2026年第三季度物资盘点','#2#','hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwst002','PDD202610130001',DATE '2026-10-13','mltTVFIVmbNUgQgVqqM','VDLqdrLqoRQkmH7tltL','杨琪',
  '望江分库2026年第三季度物资盘点','#2#','hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwst003','PDD202610160001',DATE '2026-10-16','96sbKxyuOP8xlbqTJfF','ivU85mukdyTmOmlyfwY','甘武剑',
  '宿松分库2026年第三季度物资盘点','#2#','hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY');

INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail
  (id, order_id, material_id, material_code, material_name, material_brand, material_spec, material_unit,
   book_qty, check_qty, profit_qty, loss_qty, corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwstd001','cwst001','cwmat01','CW-2026-001','闸门止水橡皮','衡水恒润橡塑制品有限公司','P60型 30×60mm','米',160,158,0,2,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd002','cwst001','cwmat02','CW-2026-002','启闭机钢丝绳','江苏法尔胜泓昇集团','6×19-Φ16 镀锌','米',400,400,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd003','cwst001','cwmat03','CW-2026-003','闸门主轮','河南黄河水工机械有限公司','400kN 铸钢','套',8,8,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd004','cwst001','cwmat04','CW-2026-004','钙基润滑脂','中国石化长城润滑油','3# 15kg/桶','桶',22,22,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd005','cwst001','cwmat05','CW-2026-005','工业齿轮油','中国石化长城润滑油','CKD220 18L/桶','桶',16,17,1,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd006','cwst001','cwmat06','CW-2026-006','交流接触器','正泰电器','CJX2-2510 220V','只',34,34,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd007','cwst001','cwmat07','CW-2026-007','塑壳断路器','正泰电器','DZ47-63 C32 3P','只',40,40,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd008','cwst001','cwmat08','CW-2026-008','低压动力电缆','远东电缆股份有限公司','YJV22-4×25','米',800,792,0,8,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd009','cwst001','cwmat09','CW-2026-009','控制电缆','远东电缆股份有限公司','KVVP-4×1.5','米',1200,1200,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd010','cwst001','cwmat13','CW-2026-013','普通硅酸盐水泥','芜湖海螺水泥有限公司','P.O42.5 散装','吨',28,28,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd011','cwst001','cwmat14','CW-2026-014','水工修补砂浆','江苏苏博特新材料股份有限公司','JCT-Ⅱ 25kg/袋','袋',105,105,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd012','cwst001','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',8,8,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd013','cwst001','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',40,40,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd014','cwst001','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',120,120,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd015','cwst001','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',600,600,0,0,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwstd016','cwst002','cwmat10','CW-2026-010','闸阀','上海良工阀门厂','Z41H-16C DN100','台',7,7,0,0,'hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwstd017','cwst002','cwmat11','CW-2026-011','镀锌钢管','天津友发钢管集团','DN100×6m','根',38,37,0,1,'hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwstd018','cwst002','cwmat12','CW-2026-012','橡胶止水带','衡水恒润橡塑制品有限公司','651型 300×8mm','米',75,75,0,0,'hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwstd019','cwst002','cwmat15','CW-2026-015','渠道衬砌预制板','望江县城南预制构件厂','50×50×8cm','块',550,553,3,0,'hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwstd020','cwst003','cwmat16','CW-2026-016','安全警示桩','安徽交安设施工程有限公司','玻璃钢 80cm 黄黑','根',120,120,0,0,'hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwstd021','cwst003','cwmat17','CW-2026-017','搪瓷水尺','河北天工水尺厂','1m 分度1cm','根',35,34,0,1,'hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwstd022','cwst003','cwmat18','CW-2026-018','割灌机刀片','斯蒂尔动力设备','230×25.4 三刃','片',65,63,0,2,'hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY');

-- ============================================================================
-- 8. 库存变动流水（55 条：入库 27 / 出库 21 / 盘盈 2 / 盘亏 5）
--    qty_before 与 qty_after 逐笔首尾相接，每件物资末笔 qty_after = 台账期末库存；
--    biz_type 按 order_kind 解释：入库 #1#采购/#2#调拨/#3#退料，出库 #1#领用/#2#调拨/#4#报废/#5#其他，
--    盘点单恒 #3#
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_stock_change_log
  (id, ledger_id, material_id, warehouse_id, change_type, order_kind, order_id, order_no, biz_type,
   change_qty, qty_before, qty_after, counterparty, operator_name, order_date,
   corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwlog001','cwled01','cwmat01','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin001','RK202603050001','#1#',300,0,300,
  '安庆市水利物资供应站','李磊',DATE '2026-03-05','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog002','cwled02','cwmat02','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin001','RK202603050001','#1#',400,0,400,
  '安庆市水利物资供应站','李磊',DATE '2026-03-05','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog003','cwled03','cwmat03','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin001','RK202603050001','#1#',12,0,12,
  '安庆市水利物资供应站','李磊',DATE '2026-03-05','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog004','cwled04','cwmat04','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin001','RK202603050001','#1#',30,0,30,
  '安庆市水利物资供应站','李磊',DATE '2026-03-05','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog005','cwled05','cwmat05','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin001','RK202603050001','#1#',20,0,20,
  '安庆市水利物资供应站','李磊',DATE '2026-03-05','hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog006','cwled06','cwmat06','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin002','RK202604120001','#1#',40,0,40,
  '正泰电器安庆销售中心','李磊',DATE '2026-04-12','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog007','cwled07','cwmat07','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin002','RK202604120001','#1#',40,0,40,
  '正泰电器安庆销售中心','李磊',DATE '2026-04-12','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog008','cwled08','cwmat08','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin002','RK202604120001','#1#',800,0,800,
  '正泰电器安庆销售中心','李磊',DATE '2026-04-12','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog009','cwled09','cwmat09','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin002','RK202604120001','#1#',1200,0,1200,
  '正泰电器安庆销售中心','李磊',DATE '2026-04-12','hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog010','cwled10','cwmat10','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin003','RK202604200001','#1#',12,0,12,
  '安庆市水利物资供应站','李磊',DATE '2026-04-20','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog011','cwled11','cwmat11','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin003','RK202604200001','#1#',60,0,60,
  '安庆市水利物资供应站','李磊',DATE '2026-04-20','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog012','cwled12','cwmat12','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin003','RK202604200001','#1#',180,0,180,
  '安庆市水利物资供应站','李磊',DATE '2026-04-20','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog013','cwled15','cwmat15','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin003','RK202604200001','#1#',800,0,800,
  '安庆市水利物资供应站','李磊',DATE '2026-04-20','hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog014','cwled10','cwmat10','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck001','CK202604280001','#2#',4,12,8,
  '花凉亭灌区望江分库','李磊',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog015','cwled11','cwmat11','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck001','CK202604280001','#2#',20,60,40,
  '花凉亭灌区望江分库','李磊',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog016','cwled12','cwmat12','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck001','CK202604280001','#2#',60,180,120,
  '花凉亭灌区望江分库','李磊',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog017','cwled15','cwmat15','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck001','CK202604280001','#2#',200,800,600,
  '花凉亭灌区望江分库','李磊',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog018','cwled16','cwmat10','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin004','RK202604280001','#2#',4,0,4,
  '花凉亭灌区主仓库','杨琪',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog019','cwled17','cwmat11','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin004','RK202604280001','#2#',20,0,20,
  '花凉亭灌区主仓库','杨琪',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog020','cwled18','cwmat12','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin004','RK202604280001','#2#',60,0,60,
  '花凉亭灌区主仓库','杨琪',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog021','cwled19','cwmat15','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin004','RK202604280001','#2#',200,0,200,
  '花凉亭灌区主仓库','杨琪',DATE '2026-04-28','hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog022','cwled01','cwmat01','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck002','CK202605180001','#1#',80,300,220,
  '花凉亭灌区怀宁县管理所工程维修队','李磊',DATE '2026-05-18','hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog023','cwled03','cwmat03','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck002','CK202605180001','#1#',4,12,8,
  '花凉亭灌区怀宁县管理所工程维修队','李磊',DATE '2026-05-18','hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog024','cwled04','cwmat04','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck002','CK202605180001','#1#',8,30,22,
  '花凉亭灌区怀宁县管理所工程维修队','李磊',DATE '2026-05-18','hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog025','cwled06','cwmat06','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck002','CK202605180001','#1#',6,40,34,
  '花凉亭灌区怀宁县管理所工程维修队','李磊',DATE '2026-05-18','hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog026','cwled05','cwmat05','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck002','CK202605180001','#1#',4,20,16,
  '花凉亭灌区怀宁县管理所工程维修队','李磊',DATE '2026-05-18','hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog027','cwled20','cwmat16','96sbKxyuOP8xlbqTJfF','#1#','#1#','cwin005','RK202605200001','#1#',200,0,200,
  '安徽交安设施工程有限公司','甘武剑',DATE '2026-05-20','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog028','cwled21','cwmat17','96sbKxyuOP8xlbqTJfF','#1#','#1#','cwin005','RK202605200001','#1#',60,0,60,
  '安徽交安设施工程有限公司','甘武剑',DATE '2026-05-20','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog029','cwled22','cwmat18','96sbKxyuOP8xlbqTJfF','#1#','#1#','cwin005','RK202605200001','#1#',80,0,80,
  '安徽交安设施工程有限公司','甘武剑',DATE '2026-05-20','hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog030','cwled16','cwmat10','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin006','RK202606050001','#1#',6,4,10,
  '安庆市水利物资供应站','杨琪',DATE '2026-06-05','hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog031','cwled17','cwmat11','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin006','RK202606050001','#1#',30,20,50,
  '安庆市水利物资供应站','杨琪',DATE '2026-06-05','hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog032','cwled18','cwmat12','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin006','RK202606050001','#1#',90,60,150,
  '安庆市水利物资供应站','杨琪',DATE '2026-06-05','hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog033','cwled19','cwmat15','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin006','RK202606050001','#1#',500,200,700,
  '安庆市水利物资供应站','杨琪',DATE '2026-06-05','hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog034','cwled20','cwmat16','96sbKxyuOP8xlbqTJfF','#2#','#2#','cwck003','CK202607220001','#1#',80,200,120,
  '花凉亭灌区宿松管理处堤防养护队','甘武剑',DATE '2026-07-22','hltgq',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog035','cwled21','cwmat17','96sbKxyuOP8xlbqTJfF','#2#','#2#','cwck003','CK202607220001','#1#',25,60,35,
  '花凉亭灌区宿松管理处堤防养护队','甘武剑',DATE '2026-07-22','hltgq',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog036','cwled16','cwmat10','mltTVFIVmbNUgQgVqqM','#2#','#2#','cwck004','CK202608060001','#1#',3,10,7,
  '花凉亭灌区望江县管理中心工程股','杨琪',DATE '2026-08-06','hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog037','cwled17','cwmat11','mltTVFIVmbNUgQgVqqM','#2#','#2#','cwck004','CK202608060001','#1#',12,50,38,
  '花凉亭灌区望江县管理中心工程股','杨琪',DATE '2026-08-06','hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog038','cwled18','cwmat12','mltTVFIVmbNUgQgVqqM','#2#','#2#','cwck004','CK202608060001','#1#',60,150,90,
  '花凉亭灌区望江县管理中心工程股','杨琪',DATE '2026-08-06','hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog039','cwled18','cwmat12','mltTVFIVmbNUgQgVqqM','#1#','#1#','cwin007','RK202608150001','#3#',15,90,105,
  '花凉亭灌区望江县管理中心工程股','杨琪',DATE '2026-08-15','hltgq',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog040','cwled13','cwmat13','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin008','RK202608200001','#1#',40,0,40,
  '芜湖海螺水泥安庆分销处','李磊',DATE '2026-08-20','hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog041','cwled14','cwmat14','pjyKVSZCduA8ul0ucnc','#1#','#1#','cwin008','RK202608200001','#1#',150,0,150,
  '芜湖海螺水泥安庆分销处','李磊',DATE '2026-08-20','hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog042','cwled22','cwmat18','96sbKxyuOP8xlbqTJfF','#2#','#2#','cwck005','CK202609080001','#5#',15,80,65,
  '花凉亭灌区宿松管理处','甘武剑',DATE '2026-09-08','hltgq',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog043','cwled19','cwmat15','mltTVFIVmbNUgQgVqqM','#2#','#2#','cwck006','CK202609160001','#1#',150,700,550,
  '花凉亭灌区望江县管理中心工程股','杨琪',DATE '2026-09-16','hltgq',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog044','cwled18','cwmat12','mltTVFIVmbNUgQgVqqM','#2#','#2#','cwck006','CK202609160001','#1#',30,105,75,
  '花凉亭灌区望江县管理中心工程股','杨琪',DATE '2026-09-16','hltgq',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog045','cwled13','cwmat13','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck007','CK202609250001','#1#',12,40,28,
  '花凉亭灌区怀宁县管理所渠道养护队','李磊',DATE '2026-09-25','hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog046','cwled14','cwmat14','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck007','CK202609250001','#1#',45,150,105,
  '花凉亭灌区怀宁县管理所渠道养护队','李磊',DATE '2026-09-25','hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog047','cwled01','cwmat01','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck007','CK202609250001','#1#',60,220,160,
  '花凉亭灌区怀宁县管理所渠道养护队','李磊',DATE '2026-09-25','hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog048','cwled01','cwmat01','pjyKVSZCduA8ul0ucnc','#4#','#3#','cwst001','PDD202610080001','#3#',2,160,158,
  NULL,'李磊',DATE '2026-10-08','hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog049','cwled05','cwmat05','pjyKVSZCduA8ul0ucnc','#3#','#3#','cwst001','PDD202610080001','#3#',1,16,17,
  NULL,'李磊',DATE '2026-10-08','hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog050','cwled08','cwmat08','pjyKVSZCduA8ul0ucnc','#4#','#3#','cwst001','PDD202610080001','#3#',8,800,792,
  NULL,'李磊',DATE '2026-10-08','hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog051','cwled02','cwmat02','pjyKVSZCduA8ul0ucnc','#2#','#2#','cwck008','CK202610090001','#4#',30,400,370,
  '花凉亭灌区怀宁县管理所','李磊',DATE '2026-10-09','hltgq',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwlog052','cwled17','cwmat11','mltTVFIVmbNUgQgVqqM','#4#','#3#','cwst002','PDD202610130001','#3#',1,38,37,
  NULL,'杨琪',DATE '2026-10-13','hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog053','cwled19','cwmat15','mltTVFIVmbNUgQgVqqM','#3#','#3#','cwst002','PDD202610130001','#3#',3,550,553,
  NULL,'杨琪',DATE '2026-10-13','hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwlog054','cwled21','cwmat17','96sbKxyuOP8xlbqTJfF','#4#','#3#','cwst003','PDD202610160001','#3#',1,35,34,
  NULL,'甘武剑',DATE '2026-10-16','hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwlog055','cwled22','cwmat18','96sbKxyuOP8xlbqTJfF','#4#','#3#','cwst003','PDD202610160001','#3#',2,65,63,
  NULL,'甘武剑',DATE '2026-10-16','hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY');

-- ============================================================================
-- 9. 取号序列（19 行：RK 8 / CK 8 / PDD 3，当日均为第 1 号）
--    应急业务日期（RK 20260601/20260602、CK 20260715/20260910/20260912、
--    PDD 20260915/20260920）与 YJBZ 序列已在 1.9 节保留，不在此重复插入
-- ============================================================================
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_order_no_seq
  (id, serial_type, serial_date, current_seq, corp_code, created_at, created_by, updated_at, updated_by)
VALUES
 ('cwsq01','RK','20260305',1,'hltgq',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-03-05 09:40:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq02','RK','20260412',1,'hltgq',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-12 10:15:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq03','RK','20260420',1,'hltgq',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-20 09:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq04','RK','20260428',1,'hltgq',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-04-28 14:10:00','VDLqdrLqoRQkmH7tltL'),
 ('cwsq05','RK','20260520',1,'hltgq',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-05-20 10:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwsq06','RK','20260605',1,'hltgq',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-06-05 09:30:00','VDLqdrLqoRQkmH7tltL'),
 ('cwsq07','RK','20260815',1,'hltgq',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-15 16:20:00','VDLqdrLqoRQkmH7tltL'),
 ('cwsq08','RK','20260820',1,'hltgq',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-08-20 09:20:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq09','CK','20260428',1,'hltgq',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-04-28 14:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq10','CK','20260518',1,'hltgq',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-05-18 10:30:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq11','CK','20260722',1,'hltgq',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-07-22 14:30:00','ivU85mukdyTmOmlyfwY'),
 ('cwsq12','CK','20260806',1,'hltgq',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-08-06 10:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwsq13','CK','20260908',1,'hltgq',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-09-08 11:00:00','ivU85mukdyTmOmlyfwY'),
 ('cwsq14','CK','20260916',1,'hltgq',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-09-16 09:40:00','VDLqdrLqoRQkmH7tltL'),
 ('cwsq15','CK','20260925',1,'hltgq',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-09-25 15:10:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq16','CK','20261009',1,'hltgq',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-09 11:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq17','PDD','20261008',1,'hltgq',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo',TIMESTAMP '2026-10-08 09:00:00','A9jEKZmJhcmUEK4RCZo'),
 ('cwsq18','PDD','20261013',1,'hltgq',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL',TIMESTAMP '2026-10-13 09:00:00','VDLqdrLqoRQkmH7tltL'),
 ('cwsq19','PDD','20261016',1,'hltgq',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY',TIMESTAMP '2026-10-16 09:00:00','ivU85mukdyTmOmlyfwY');

COMMIT;

-- ============================================================================
-- 10. 核对查询（在 COMMIT 之后只读执行；actual 与 expected 相等即通过）
--     若任一项不符，说明落库与脚本不一致，按 item 编号回到对应段落排查
-- ============================================================================
SELECT item, actual, expected, CASE WHEN actual = expected THEN 'OK' ELSE 'CHECK' END AS result
  FROM (
    SELECT '1 常用物资目录' AS item, COUNT(*)::INT AS actual, 18 AS expected FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
      WHERE corp_code = 'hltgq' AND TRIM(COALESCE("type", '')) NOT IN ('#2#', '应急物资')
    UNION ALL
    SELECT '2 应急物资目录（保留）', COUNT(*)::INT, 13 FROM "qixiao-apaas".t_auto_hltgq_knc3g_slaifi
      WHERE corp_code = 'hltgq' AND TRIM(COALESCE("type", '')) IN ('#2#', '应急物资')
    UNION ALL
    SELECT '3 常用台账', COUNT(*)::INT, 22 FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
      WHERE corp_code = 'hltgq' AND id LIKE 'cwled%'
    UNION ALL
    SELECT '4 应急台账（保留）', COUNT(*)::INT, 13 FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
      WHERE corp_code = 'hltgq' AND id LIKE 'emled%'
    UNION ALL
    SELECT '5 常用入库单', COUNT(*)::INT, 8 FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order
      WHERE corp_code = 'hltgq' AND id LIKE 'cwin%'
    UNION ALL
    SELECT '6 常用入库明细', COUNT(*)::INT, 27 FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail
      WHERE corp_code = 'hltgq' AND order_id LIKE 'cwin%'
    UNION ALL
    SELECT '7 常用出库单', COUNT(*)::INT, 8 FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order
      WHERE corp_code = 'hltgq' AND id LIKE 'cwck%'
    UNION ALL
    SELECT '8 常用出库明细', COUNT(*)::INT, 21 FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail
      WHERE corp_code = 'hltgq' AND order_id LIKE 'cwck%'
    UNION ALL
    SELECT '9 常用盘点单', COUNT(*)::INT, 3 FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order
      WHERE corp_code = 'hltgq' AND id LIKE 'cwst%'
    UNION ALL
    SELECT '10 常用盘点明细', COUNT(*)::INT, 22 FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail
      WHERE corp_code = 'hltgq' AND order_id LIKE 'cwst%'
    UNION ALL
    SELECT '11 常用流水', COUNT(*)::INT, 55 FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log
      WHERE corp_code = 'hltgq' AND id LIKE 'cwlog%'
    UNION ALL
    SELECT '12 应急流水（保留）', COUNT(*)::INT, 26 FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log
      WHERE corp_code = 'hltgq' AND id LIKE 'emlog%'
    UNION ALL
    -- 13 方向异常：入库/盘盈应 before+qty，出库/盘亏应 before-qty
    SELECT '13 流水方向异常', COUNT(*)::INT, 0 FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log
      WHERE corp_code = 'hltgq' AND ledger_id LIKE 'cwled%'
        AND (   (change_type IN ('#1#', '#3#') AND qty_after <> qty_before + change_qty)
             OR (change_type IN ('#2#', '#4#') AND qty_after <> qty_before - change_qty))
    UNION ALL
    -- 14 断链：同一物资同一仓库按时间排序，本笔 qty_before 应等于上一笔 qty_after
    SELECT '14 流水断链', COUNT(*)::INT, 0 FROM (
      SELECT qty_before, LAG(qty_after) OVER (PARTITION BY material_id, warehouse_id
                                               ORDER BY created_at, id) AS prev_after
        FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log
       WHERE corp_code = 'hltgq' AND ledger_id LIKE 'cwled%') t
      WHERE prev_after IS NOT NULL AND qty_before <> prev_after
    UNION ALL
    -- 15 末笔对账：台账期末库存应等于最后一笔流水的 qty_after
    SELECT '15 末笔与台账不符', COUNT(*)::INT, 0 FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe t
      WHERE t.corp_code = 'hltgq' AND t.id LIKE 'cwled%'
        AND t.ejgxlq <> COALESCE((SELECT l.qty_after
                                    FROM "qixiao-apaas".t_auto_hltgq_water_stock_change_log l
                                   WHERE l.ledger_id = t.id
                                   ORDER BY l.created_at DESC, l.id DESC LIMIT 1), -1)
    UNION ALL
    -- 16 盘点明细盈亏计算
    SELECT '16 盘点盈亏计算异常', COUNT(*)::INT, 0 FROM "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail d
      WHERE d.corp_code = 'hltgq' AND d.order_id LIKE 'cwst%'
        AND (   d.profit_qty <> GREATEST(d.check_qty - d.book_qty, 0)
             OR d.loss_qty   <> GREATEST(d.book_qty - d.check_qty, 0))
    UNION ALL
    -- 17/18 明细与流水数量一致
    SELECT '17 出库明细与流水不符', COUNT(*)::INT, 0
      FROM "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail d
      JOIN "qixiao-apaas".t_auto_hltgq_water_stock_change_log l
        ON l.order_id = d.order_id AND l.material_id = d.material_id
     WHERE d.corp_code = 'hltgq' AND d.order_id LIKE 'cwck%'
       AND (d.quantity <> l.change_qty OR COALESCE(d.stock_before, -1) <> l.qty_before)
    UNION ALL
    SELECT '18 入库明细与流水不符', COUNT(*)::INT, 0
      FROM "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail d
      JOIN "qixiao-apaas".t_auto_hltgq_water_stock_change_log l
        ON l.order_id = d.order_id AND l.material_id = d.material_id
     WHERE d.corp_code = 'hltgq' AND d.order_id LIKE 'cwin%'
       AND d.quantity <> l.change_qty
    UNION ALL
    -- 19 台账安全库存快照应与目录一致
    SELECT '19 安全库存快照不符', COUNT(*)::INT, 0
      FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe t
      JOIN "qixiao-apaas".t_auto_hltgq_knc3g_slaifi s ON s.id = t.jzikio
     WHERE t.corp_code = 'hltgq' AND t.id LIKE 'cwled%'
       AND CAST(t.vhvhja AS BIGINT) <> CAST(TRIM(s.unqvqe) AS BIGINT)
    UNION ALL
    -- 20 库存状态快照：#1# 正常需严格大于安全库存，等于或低于为 #2# 偏低
    SELECT '20 库存状态快照不符', COUNT(*)::INT, 0
      FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe t
      JOIN "qixiao-apaas".t_auto_hltgq_knc3g_slaifi s ON s.id = t.jzikio
     WHERE t.corp_code = 'hltgq' AND t.id LIKE 'cwled%'
       AND t.mojktw <> CASE WHEN COALESCE(t.ejgxlq, 0) > CAST(NULLIF(TRIM(s.unqvqe), '') AS INTEGER)
                            THEN '#1#' ELSE '#2#' END
    UNION ALL
    SELECT '21 库存偏低物资', COUNT(*)::INT, 3 FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe
      WHERE corp_code = 'hltgq' AND id LIKE 'cwled%' AND mojktw = '#2#'
    UNION ALL
    SELECT '22 在用仓库', COUNT(*)::INT, 3 FROM "qixiao-apaas".t_auto_hltgq_knc3g_twufyx
      WHERE corp_code = 'hltgq' AND COALESCE(aeleiw, '') <> '#zlji#'
    UNION ALL
    SELECT '23 新建取号序列', COUNT(*)::INT, 19 FROM "qixiao-apaas".t_auto_hltgq_water_order_no_seq
      WHERE corp_code = 'hltgq' AND id LIKE 'cwsq%'
  ) x
 ORDER BY CAST(split_part(item, ' ', 1) AS INTEGER);
