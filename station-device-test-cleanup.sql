-- ============================================================================
-- 站点 / 设备测试数据清理 v2（2026-10-28）
--
-- 背景：站点档案表与设备表中查到 2 个测试站点、2 台测试设备，均为页面测试时
--       随手创建（名称含「测试」、站号 12345 / 334455、位置「只需」「嗯嗯」、
--       传输方式「sdasf」），与真实档案混在一起影响观感。
--
-- 影响范围（执行前实测）：
--   站点 t_auto_hltgq_5nw74_vnqqef   删 2 行（227 → 225）
--   设备 t_auto_hltgq_water_device   删 2 行（450 → 448）
--
-- 关联核查结论：监测数据（12 类）、告警、工单、问题、巡检、阈值、站点排序、
--   巡查范围、消息规则、视频巡检 —— 对这两个站点和两台设备全部 0 条引用；
--   唯一关联是 2 台测试设备挂在「导入测试」站点下，先删设备再删站点即无悬挂。
--
-- 不动的数据：
--   1. 平台自动版本备份表（bak_device_20261022、*_bak_v*）由平台管理，不清；
--   2. 12 个无监测类型的记录是组织层级节点（望江县管理局、宿松县管理所、
--      太湖县管理所、市直管理、怀宁县管理所、骨干工程、水库上/下等），真实档案；
--   3. 31 个无站号站点、227 台无编码设备是 2026-10 导入的真实数据，非测试数据。
--
-- 安全机制（四层）：
--   1. 删除前断言：目标数据必须恰好 2 站点 + 2 设备，否则抛异常终止；
--   2. 精准匹配：id + 名称 / 编码 双重比对，且叠加 10 项业务引用兜底（NOT EXISTS）；
--   3. 删除后断言：残留、悬挂、21 项引用逐项复查，任一不为 0 立即抛异常 ——
--      PostgreSQL 事务进入 aborted 状态后 COMMIT 会自动变成 ROLLBACK，数据不会落库；
--   4. 事务包裹：BEGIN … COMMIT，中途任何报错整体回滚。
--
-- 执行方式：整段执行（必须同一连接）。
--   · 看到 NOTICE「清理成功」+ 两条 DELETE 各返回 2 行 → 提交成功；
--   · 看到 ERROR「失败：…」→ 已自动回滚，按提示排查后重新执行；
--   · 末尾核对查询 15 项中带 expected 的应为 OK，03/04 为 INFO（仅展示总数）。
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. 删除前断言：目标数据必须恰好 2 站点 / 2 设备，否则终止
-- ---------------------------------------------------------------------------
DO $cleanup$
DECLARE
    v_site INT;
    v_dev  INT;
BEGIN
    SELECT COUNT(*) INTO v_site
      FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
     WHERE id IN ('0eSleksLz6nERDfjyNIVON', 'lx3fuoLlU72UHqvY4Pj')
       AND zzkaec IN ('导入测试', 'zj_测试');
    IF v_site <> 2 THEN
        RAISE EXCEPTION '终止：待删站点应为 2 条，实际 % 条（可能有其他人在同步改数据，请人工核验）', v_site;
    END IF;

    SELECT COUNT(*) INTO v_dev
      FROM "qixiao-apaas".t_auto_hltgq_water_device
     WHERE id IN ('8JDNH8Ef4ptnEetulqX', '29Nz5eb7q8L39qzf6BT')
       AND code IN ('123', '345');
    IF v_dev <> 2 THEN
        RAISE EXCEPTION '终止：待删设备应为 2 台，实际 % 台（请人工核验）', v_dev;
    END IF;

    RAISE NOTICE '删除前检查通过：2 个测试站点、2 台测试设备';
END
$cleanup$;

-- ---------------------------------------------------------------------------
-- 2. 删除测试设备（2 台）：先删设备，避免站点删除后设备 site 悬挂
--    type：#3# 流量（测试设备1）、#7# 墒情（测试设备2）
--    兜底：不被工单/告警/问题/巡检记录引用
-- ---------------------------------------------------------------------------
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_device d
 WHERE ((d.id = '8JDNH8Ef4ptnEetulqX' AND d.name = '测试设备1' AND d.code = '123')
     OR (d.id = '29Nz5eb7q8L39qzf6BT' AND d.name = '测试设备2' AND d.code = '345'))
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_work_order x
                    WHERE x.device = d.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_alert x
                    WHERE x.device = d.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_knc3g_bpzjoh x
                    WHERE x.device = d.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_inspection_record x
                    WHERE x."_d_10497_device" = d.id)
RETURNING d.id, d.code, d.name;

-- ---------------------------------------------------------------------------
-- 3. 删除测试站点（2 个）
--    「导入测试」上级管理单位为空，站号 12345，视频类型，离线；
--    「zj_测试」上级管理单位借用了真实站点「老鼠沟」（MthSite0000000000019），
--    删除本站不影响「老鼠沟」本身。
--    兜底：本站名下不能再有设备、不能有下级站点、不能被业务表 / 巡查范围 /
--          消息规则引用（这四类是删除后会直接产生视觉异常或逻辑断链的）
-- ---------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
 WHERE ((s.id = '0eSleksLz6nERDfjyNIVON' AND s.zzkaec = '导入测试')
     OR (s.id = 'lx3fuoLlU72UHqvY4Pj' AND s.zzkaec = 'zj_测试'))
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_device x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" x
                    WHERE x.ahieto = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_alert x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_work_order x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_knc3g_bpzjoh x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_inspection_record x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_threshold x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_station_sort x
                    WHERE x.site = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_knc3g_nlbdju_tkwovk_rel x
                    WHERE x.rel_id = s.id)
   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_message_rule x
                    WHERE x.site_id LIKE '%' || s.id || '%')
RETURNING s.id, s.iofhpi, s.zzkaec;

-- ---------------------------------------------------------------------------
-- 4. 删除后断言：残留 / 悬挂 / 引用必须全部为 0，否则抛异常自动回滚
-- ---------------------------------------------------------------------------
DO $cleanup$
DECLARE
    v_cnt BIGINT;
    v_bad TEXT;
BEGIN
    -- 4.1 目标行必须已删除
    SELECT COUNT(*) INTO v_cnt FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
     WHERE id IN ('0eSleksLz6nERDfjyNIVON', 'lx3fuoLlU72UHqvY4Pj');
    IF v_cnt > 0 THEN
        RAISE EXCEPTION '失败：测试站点仍残留 % 条（删除条件被兜底拦截），已整体回滚', v_cnt;
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM "qixiao-apaas".t_auto_hltgq_water_device
     WHERE id IN ('8JDNH8Ef4ptnEetulqX', '29Nz5eb7q8L39qzf6BT');
    IF v_cnt > 0 THEN
        RAISE EXCEPTION '失败：测试设备仍残留 % 台（删除条件被兜底拦截），已整体回滚', v_cnt;
    END IF;

    -- 4.2 全库一致性：不得有设备 / 下级站点指向不存在的站点
    SELECT COUNT(*) INTO v_cnt
      FROM "qixiao-apaas".t_auto_hltgq_water_device d
     WHERE COALESCE(TRIM(d.site), '') <> ''
       AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
                        WHERE s.id = d.site);
    IF v_cnt > 0 THEN
        RAISE EXCEPTION '失败：全库有 % 台设备指向不存在的站点，已整体回滚', v_cnt;
    END IF;

    SELECT COUNT(*) INTO v_cnt
      FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
     WHERE COALESCE(TRIM(s.ahieto), '') <> ''
       AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" u
                        WHERE u.id = s.ahieto);
    IF v_cnt > 0 THEN
        RAISE EXCEPTION '失败：全库有 % 个站点上级管理单位悬挂，已整体回滚', v_cnt;
    END IF;

    -- 4.3 业务表与监测数据不得留任何引用（逐表统计，一次报出全部非零项）
    SELECT string_agg(item || ' ' || cnt::TEXT || ' 条', '；') INTO v_bad
      FROM (
            SELECT '告警' AS item, COUNT(*) AS cnt
              FROM "qixiao-apaas".t_auto_hltgq_water_alert
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                OR device IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT')
      UNION ALL SELECT '工单', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_work_order
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                OR device IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT')
      UNION ALL SELECT '问题记录', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_knc3g_bpzjoh
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                OR device IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT')
      UNION ALL SELECT '巡检记录', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_inspection_record
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                OR "_d_10497_device" IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT')
      UNION ALL SELECT '阈值', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_threshold
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '站点排序', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_station_sort
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '巡查范围', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_knc3g_nlbdju_tkwovk_rel
             WHERE rel_id IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '消息规则', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_message_rule
             WHERE site_id LIKE '%0eSleksLz6nERDfjyNIVON%'
                OR site_id LIKE '%lx3fuoLlU72UHqvY4Pj%'
      UNION ALL SELECT '视频巡检', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_video_patrol
             WHERE TRIM(channel_code) IN ('123','345')
      UNION ALL SELECT '闸门数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_gate
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '雨量数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_rain_info
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '水位数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_river_info
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '流量数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_vol_info
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '墒情数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_soil_data
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '含水率', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_moisture_record
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '水质数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_nmisp_info
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '降水数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_pcp_info
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '报文数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_msg_info
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '泄流数据', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_sluice_discharge
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '雨量订正', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_rain_adjust
             WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
      UNION ALL SELECT '日统计', COUNT(*)
              FROM "qixiao-apaas".t_auto_hltgq_water_stats_daily
             WHERE stcd IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
           ) t
     WHERE cnt > 0;
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION '失败：仍有残留引用 -> %，已整体回滚', v_bad;
    END IF;

    RAISE NOTICE '清理成功：2 个测试站点、2 台测试设备已删除；全库无悬挂、无残留引用';
END
$cleanup$;

-- 走到这里说明上面所有断言都已通过。
-- 若任一断言报了 ERROR，事务已进入 aborted 状态，下面的 COMMIT 会自动变成
-- ROLLBACK，数据不会落库；如需主动放弃，把 COMMIT 换成 ROLLBACK 执行即可。
COMMIT;

-- ---------------------------------------------------------------------------
-- 5. 核对查询（COMMIT 后只读执行，独立于事务，仅用于留档）
--    expected 为 NULL 的两项只展示总数；其余 13 项应全部 OK
-- ---------------------------------------------------------------------------
SELECT item, actual, expected,
       CASE WHEN expected IS NULL THEN 'INFO'
            WHEN actual = expected THEN 'OK'
            ELSE 'CHECK' END AS result
  FROM (
        SELECT '01 残留测试站点' AS item,
               (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
                 WHERE id IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))::BIGINT AS actual,
               0::BIGINT AS expected
  UNION ALL
        SELECT '02 残留测试设备',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_device
                 WHERE id IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '03 站点总数（参考 225）',
               (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef")::BIGINT,
               NULL::BIGINT
  UNION ALL
        SELECT '04 设备总数（参考 448）',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_device)::BIGINT,
               NULL::BIGINT
  UNION ALL
        SELECT '05 设备悬挂',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_device d
                 WHERE COALESCE(TRIM(d.site),'') <> ''
                   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
                                    WHERE s.id = d.site))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '06 站点上级悬挂',
               (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
                 WHERE COALESCE(TRIM(s.ahieto),'') <> ''
                   AND NOT EXISTS (SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" u
                                    WHERE u.id = s.ahieto))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '07 告警引用',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_alert
                 WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                    OR device IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '08 工单引用',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_work_order
                 WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                    OR device IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '09 问题记录引用',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_knc3g_bpzjoh
                 WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                    OR device IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '10 巡检记录引用',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_inspection_record
                 WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')
                    OR "_d_10497_device" IN ('8JDNH8Ef4ptnEetulqX','29Nz5eb7q8L39qzf6BT'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '11 阈值与站点排序引用',
               ((SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_threshold
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_station_sort
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '12 巡查范围与消息规则引用',
               ((SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_knc3g_nlbdju_tkwovk_rel
                  WHERE rel_id IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_message_rule
                  WHERE site_id LIKE '%0eSleksLz6nERDfjyNIVON%'
                     OR site_id LIKE '%lx3fuoLlU72UHqvY4Pj%'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '13 视频巡检通道码引用',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_video_patrol
                 WHERE TRIM(channel_code) IN ('123','345'))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '14 监测数据引用（site 口径）',
               ((SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_gate
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_rain_info
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_river_info
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_vol_info
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_soil_data
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_moisture_record
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_nmisp_info
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_pcp_info
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_msg_info
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_sluice_discharge
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))
              + (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_rain_adjust
                  WHERE site IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj')))::BIGINT,
               0::BIGINT
  UNION ALL
        SELECT '15 监测数据引用（stcd 口径·日统计）',
               (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_stats_daily
                 WHERE stcd IN ('0eSleksLz6nERDfjyNIVON','lx3fuoLlU72UHqvY4Pj'))::BIGINT,
               0::BIGINT
  ) x
 ORDER BY CAST(split_part(item, ' ', 1) AS INTEGER);
