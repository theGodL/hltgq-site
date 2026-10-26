-- ============================================================================
-- 设备型号回填 v2（evkmpl）
-- 依据：D:\花凉亭清单（按建筑物改）-1.xlsx + D:\设备型号统计.xlsx
--       设备型号统计.xlsx 的 G 列「设备型号」为该站配置的真实型号
-- 生成：2026-10-26  预计影响 267 行（设备表共 459 台）
--
-- 口径（2026-10-26 确认）：
--   ★ 只处理「平台已有该设备」且「表格给了该型号」的交集，两条缺一即不写：
--     无型号的不填；有型号但平台无该设备（设备还没接）的不填。
--   1) 型号按「站点 × 设备类型」落到 t_auto_hltgq_water_device.evkmpl，按设备 id 定位；
--      旧版 station-device-model-update.sql 是拆分前的设备 id，已全部失效，本文件重新生成。
--   2) 型号 → 设备类型：
--      #1# 水位计  精波PWRD81HB-CANF
--      #3# 流量计  精波PWFL432-CAP8MV（雷达波）/ 智旭FUC660-2M-AR（ADCP）/
--                  四信F-ADCP100（渡槽ADCP）/ 精波PWFL432-CAP8MVNF+精波PWRV430-CAPIN（虎形潭矩阵）
--      #2# 雨量    中水三立SUNJZD-05
--      #7# 墒情    新普惠XFX-1
--      #8# 水质    中水三立SUNXZO-M
--      #5# 视频    大华DH-IPC-HFW34XYZM-ABCDE（枪机）/ 大华DH-SD-6CABCDEF-XYZ-XY（球机）
--      #4# 闸门    中水三立SUNYZM-GS / SUNYZM-GZ / SUNCLX（Excel 只在青阳斗渠、矮农边涵给，
--                  这两站平台无 #4# 设备 → 按口径不写，故本文件无 #4# 语句）
--   3) 同站多路视频（上游/下游/闸室内）Excel 只给一个视频型号，统一写入该站全部 #5# 设备；
--   4) 利旧站（太宿干渠进水闸/毕岭节制闸/汪元节制闸）Excel 只给视频型号，只写视频；
--   5) 按口径本轮不写（均属「有型号、平台无对应设备」，设备未接入，不留占位值）：
--      · 水位计 精波PWRD81HB-CANF：Excel 22 站声明，只写双庙湖渡槽/夏家湖渡槽 2 站；
--        其余 20 站平台无 #1# 水位计——水工段 13 站：集岭电站进水闸/望牛墩节制闸/
--        武昌支渠进水闸/长岭跌水节制闸/袁畈泄洪闸/郝大屋泄洪闸/郝梓屋泄洪闸/荞麦岭泄洪河/
--        段龙节制闸/王塘节制闸/滴露节制闸/王塘泄洪闸/王家桥节制闸；工情段 7 站：
--        北干渠进水闸/南干渠进水闸/太怀干渠进水闸/南山寺节制闸/渠首电站防洪闸/
--        双庙湖节制闸/渠首进水闸
--      · 一体化闸门 3 型号：青阳斗渠、矮农边涵平台无 #4# 设备
--      · 缺对应类型设备的站：灌子洞节制闸/铁寨支渠进水闸/张屋1#直灌涵/腊皖分干渠节制闸（无 #3#），
--        渠首电站防洪闸/段龙节制闸/孙家埠节制闸/双塘泄洪闸（无 #5#）
--      · 平台无任何设备的 5 站：北干渠枫岭段、曹大支渠、梅老屋斗渠、瓷岭斗渠、花园斗渠
--      · 平台有设备但 Excel 未给型号（留空待补）：#4# 闸门 67 台、虎形潭渡槽 #1# 水位计、
--        13 台河道站水位计及部分视频设备
--
-- 执行前备份：
-- CREATE TABLE "qixiao-apaas"."t_auto_hltgq_water_device_bak_model2_20261026"
--   AS SELECT * FROM "qixiao-apaas"."t_auto_hltgq_water_device";
-- ============================================================================
BEGIN;


-- ---------- 2#渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'Q4M3QMIV7WTrFRMUQAL6';  -- 2#渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'zMnI6Ps2emrV8eMDK73W';  -- 二号渡槽_1 [#5#]

-- ---------- 一号渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '9P0fStPdqzLOOO10X3Wo';  -- 一号渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'ERP6zGzBEyEv8tlFlajW';  -- 一号渡槽_1 [#5#]

-- ---------- 一安支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '2AMOtKIB5AllsCaUuSm9';  -- 一安支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '681eHZLcunk8qq89gTlK';  -- 一安支渠进水闸_1 [#5#]

-- ---------- 三联支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'zPOfZxmqIGsW3bbtregC';  -- 三联支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'HpJuosB1ZJQWd7lGJDQw';  -- 三联支渠进水闸_1 [#5#]

-- ---------- 下仓节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'GuyJTdiJueY20eFvYOrh';  -- 下仓节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'fwmWqk5uGC3soWjBbDJW';  -- 下仓节制闸_1 [#5#]

-- ---------- 下湾边涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'qnwCQbnUpgdi2bEa8y0b';  -- 下湾边涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'zBDfdtuXUIF0RmxLti3i';  -- 下湾边涵_1 [#5#]

-- ---------- 东升 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '2yJYovBfMQlMuz7APSoW';  -- 东升流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'D5F7vqy21GGOqbd8J8V3';  -- 东升支渠_1 [#5#]

-- ---------- 东晓边涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'YTQ18P1JxUbJBLJ2MPA8';  -- 东晓边涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '08ujil73geH7a3M3w6xw';  -- 东晓边涵_1 [#5#]

-- ---------- 中心斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'ldu2dR6ea2csUXiyPwDs';  -- 中心斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'o44yWgtAiKDQq3R1aMcS';  -- 中心斗渠_1 [#5#]

-- ---------- 中檀支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'r7Zx2rOrA0pUCjBbIy3r';  -- 中檀支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'gZ8Lndbc11vjWbLSkJw9';  -- 中潭支渠_1 [#5#]

-- ---------- 中沟支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 't5nCWQZlq5kj4irZ7ijV';  -- 中沟支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'xxRLMKxEK9UVVFGBD3un';  -- 中沟支渠进水闸 [#5#]

-- ---------- 乔木分干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'K6zSJ0FGU4SREFGOZiO0';  -- 乔木分干渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'fbNFQjTBrLIWvXiV4qfN';  -- 乔木分干渠进水闸_1 [#5#]

-- ---------- 云集支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'VRVZK6vpPcyYogWr7dG4';  -- 云集支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'xnRKkNipV49lwNJUYGtP';  -- 云集支渠进水闸_1 [#5#]

-- ---------- 何龙斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'Iw4lkZGNrJTOwyRQ5MAz';  -- 何龙斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'yDlPmb0Zkx2d41D3wYD9';  -- 何龙斗渠_1 [#5#]

-- ---------- 余龙节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'wPaGmKhuCbwgrmEUUELh';  -- 余龙节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'uYslctwb3IuyckIAo2eq';  -- 余龙支渠_1 [#5#]

-- ---------- 刘大斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'tjXQeFkP9egGQvDBzmAk';  -- 刘大斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'vGjOrJv7B9J2gDkNvvx7';  -- 刘大斗渠_1 [#5#]

-- ---------- 刘明支渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'qJYreFVnC1zOi0Frcmxi';  -- 刘明支渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'BRcks5QFVQmINsc5EFge';  -- 刘明支渠_1 [#5#]

-- ---------- 前进斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'XkLUtsJWIndPpwdPnE1C';  -- 前进斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'LbdyPHYlpLdiPlItgNKk';  -- 前进斗渠_1 [#5#]

-- ---------- 北干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'W82ZeXXvJ6UWtNmzj03u';  -- 北干进水闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'vgrVjY6JsaXOgGcsjJcx';  -- 北干进水闸下游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'oyiO1dKgzWILwlDOtpXm';  -- 望江北干 [#5#]

-- ---------- 十里支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'ywl2l8ndwsxkfS2L5JQD';  -- 十里支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'C3lZd3RYRNrkDNBgcCB1';  -- 十里支渠进水闸_1 [#5#]

-- ---------- 南山寺节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '8kvbCS8iv1P6zitJttwx';  -- 南山寺节制闸 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'YycD4GttU29CuNhd6ckN';  -- 南山寺节制闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'wyPRpJEIZguDlGaUZJpd';  -- 南山寺节制闸下游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '33bCqp71lOFosFJgFXvj';  -- 南山寺闸室内 [#5#]

-- ---------- 南岭支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'g9kSnknK2WiBJ76Afazl';  -- 南岭支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'kZNc19A048qkrLSQWnpZ';  -- 南岭支渠进水闸_1 [#5#]

-- ---------- 南川支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'BfrXy0897RujT9M2ANV3';  -- 南川支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'rCgmPj0PmZmRT65lkO0B';  -- 南川支渠_1 [#5#]

-- ---------- 南干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'hRJd9MFG35q9FdMP6KYI';  -- 南干进水闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '8WyISEgRRp28KYMPLF6Q';  -- 南干进水闸下游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'AkihejNk8gVkG7n5Ss7O';  -- 望江南干进水闸 [#5#]

-- ---------- 南干进水闸水质站 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNXZO-M' WHERE id = 'fuDnUlLIGE4XaHKDAS5S';  -- 南干进水闸水质站水质监测仪# [#8#]

-- ---------- 南畈支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'R94wSLVLvhIqYc2GGMoj';  -- 南畈支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '6Wi7OUQGgtrhRD4fwDSL';  -- 南畈支渠进水闸_1 [#5#]

-- ---------- 双塘泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'PgVRCvooaLOo1a8t4p0y';  -- 双塘泄洪闸流量计# [#3#]

-- ---------- 双庙湖渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWRD81HB-CANF' WHERE id = 'fj2w748fRb1DqE8o1UeJ';  -- 双庙湖渡槽水位计# [#1#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '四信F-ADCP100' WHERE id = 'U3ReNoiudbIo8a3NIU91';  -- 双庙湖渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'Fsdz507m0JZFCxe2PkhT';  -- 双庙湖渡槽_1 [#5#]

-- ---------- 双庙湖管理所 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNJZD-05' WHERE id = 'tGqeclCKrc40BKuA72Oc';  -- 双庙湖管理所雨量计# [#2#]

-- ---------- 双庙湖节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'UdcIOrBN6DKeexoZjYqC';  -- 双庙湖节制闸 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'c7StvumZfuRm8x7K9E7y';  -- 双庙湖节制闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'P77BOt68wAnRpJpFdhnt';  -- 双庙湖节制闸下游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'RifgkBjPqOUij2xS8DNT';  -- 双庙湖闸室内 [#5#]

-- ---------- 向阳斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'FHjEJMajySF0ASQ39fvP';  -- 向阳斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'UZqqCOUG0eWbmp8iVijl';  -- 向阳斗渠_1 [#5#]

-- ---------- 团山支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'FNB925lMuBTvrSDOcAgA';  -- 团山支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'SLLP1L5bPoajHe3FK3Ba';  -- 团山支渠进水闸_1 [#5#]

-- ---------- 团结斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'YgmuFJOz8SY2m9ndBk8t';  -- 团结斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'qIUwwzybOveRozAlqt0s';  -- 团结斗渠_1 [#5#]

-- ---------- 夏家湖渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWRD81HB-CANF' WHERE id = 'TSOAI3Csv8PQgmP3xJGz';  -- 夏家湖渡槽水位计# [#1#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '四信F-ADCP100' WHERE id = 'CkiqsQAKhyOlgE7hu0cO';  -- 夏家湖渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'K0BSe5QIDghOFYlJJ8CL';  -- 夏家湖渡槽_1 [#5#]

-- ---------- 大桥支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'YlgbcWRq58unwjHZkGjP';  -- 大桥支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '8mAtCPcjgIF1E6XpGepv';  -- 大桥支渠进水闸_1 [#5#]

-- ---------- 太宿干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '14ggvuUPraRqJu4CBH2W';  -- 太宿进水闸 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'g5mxJEqJgsodKiuSRSia';  -- 太宿进水闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '7qtHhZTPJjwhuSqqz9Z8';  -- 太宿进水闸上游_2 [#5#]

-- ---------- 太怀干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'Qt9s3NccbfYHv4VyfI72';  -- 太怀进水闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'zyIALMzppq2zTUUUBMbU';  -- 太怀进水闸下游 [#5#]

-- ---------- 太湖毕岭 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '新普惠XFX-1' WHERE id = 'KbAI8RPAYSM056JNPQlz';  -- 太湖毕岭墒情探测# [#7#]

-- ---------- 姚圩支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'a8itefhSMOEGipUpQrgx';  -- 姚圩支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'Z0fuHsLaxMydF2NElSRQ';  -- 姚圩支渠进水闸_1 [#5#]

-- ---------- 孙家埠渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'eE6mftVbVc7ukFGZfPZK';  -- 孙家埠渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'N3SZciOVLGe6sacZM5gW';  -- 孙家埠渡槽_1 [#5#]

-- ---------- 孙家埠节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'gvQjNqEYcCf8ScvAHbPi';  -- 孙家埠节制闸流量计# [#3#]

-- ---------- 孟垅直灌涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'lsFjbexIliM35ELUNUY4';  -- 孟垅直灌涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'BRBo54RPzXOb6LFLoKlL';  -- 孟垅直灌涵 [#5#]

-- ---------- 宿松 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'PPXB2793Vg9sIVWJH1lx';  -- 枫林支渠_1 [#5#]  Excel:枫林支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '新普惠XFX-1' WHERE id = 'LVVl4XVE22yT61F16ABh';  -- 宿松墒情探测# [#7#]

-- ---------- 山湖节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'sKQ5MexW0i6zBiWGP2st';  -- 山湖节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'Ai1JfYmMLdmJ5n2CexrD';  -- 山湖节制闸_1 [#5#]

-- ---------- 建设支渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'VZ4VQXSlDVslLuN30vph';  -- 建设支渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'CSPYvBvBpwS3E9B4SM2i';  -- 建设支渠_1 [#5#]

-- ---------- 张屋1#直灌涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'hZM0o1nldOvQRBFS25tK';  -- 张屋1#直灌涵_1 [#5#]

-- ---------- 张岭斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'F3LhtHtvYPOW7IbtTt6d';  -- 张岭斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'GbjKw60NbPYMs7UBcVKI';  -- 张岭斗渠_1 [#5#]

-- ---------- 彭桥支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'AB5HgqzRhLCq0dNlhoCj';  -- 彭桥支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'vO9G5Jy7pPqubFXoI2I9';  -- 彭桥支渠进水闸_1 [#5#]

-- ---------- 怀宁麻塘湖 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '新普惠XFX-1' WHERE id = 'QFMOGtktW5P1KcjnP2Ok';  -- 怀宁麻塘湖墒情探测# [#7#]

-- ---------- 新发支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'qRzrkea7vghrLsKNFxf3';  -- 新发支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '7gdajKW2vbFQZtu9mIXy';  -- 新发支渠进水闸_1 [#5#]

-- ---------- 新坝支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'Otz4eOhEWahNLQy4z5bP';  -- 新坝支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'Yygja16G2yrj14SCxSlO';  -- 新坝支渠进水闸_1 [#5#]

-- ---------- 望江 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'KudZ9WZeEmgLw9zQImsp';  -- 枫塘斗渠联盟_1 [#5#]  Excel:枫塘斗渠联盟
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '新普惠XFX-1' WHERE id = 'dv4VRW2fPmxZz9Z5Xw0P';  -- 望江墒情探测# [#7#]

-- ---------- 望牛墩节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'U9RPIIs9EiQgnx1HN2wm';  -- 望牛墩上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'uPsPaW1zeUwnQ5QWEk68';  -- 望牛墩下游_1 [#5#]

-- ---------- 朝阳支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'D04hMqizp6Y8uiqw7iTk';  -- 朝阳支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'OKWhQVFIomNP6xcIsBvs';  -- 朝阳支渠_1 [#5#]

-- ---------- 杨林分干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'faDjrmVG9v6UGSubAHu8';  -- 杨林分干渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'znjzmnYFz91iWgvMUJSw';  -- 杨林支渠进水闸_1 [#5#]

-- ---------- 杨林分干节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '0Ti2olFSKGMKXFXUf4lp';  -- 杨林分干节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '6qxl2cCc2VsYAa8gjyiW';  -- 杨林分干节制闸_1 [#5#]

-- ---------- 杨树支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '0EfJaim2eHfbSDoUWJ9z';  -- 杨树支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'rxJLt8TrxHQ9iDZfLPkn';  -- 杨树支渠进水闸_1 [#5#]

-- ---------- 杨辛2#直灌涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'CKG4IQ1Q61CghOlVkGwW';  -- 杨辛2#直灌涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'fyczYOxUqoWMnMDwCTnL';  -- 杨辛2#直灌涵_1 [#5#]

-- ---------- 板桥边涵（杨屋2#边涵） ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '9kyWMa3Mr4AmePVHKkbH';  -- 板桥边涵（杨屋2#边涵）流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'eJV8mVbbht1Yk4CFAgJ0';  -- 板桥边涵_1 [#5#]

-- ---------- 枫塘斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'tjsI0dYFJbBUT85KSrMa';  -- 枫塘斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'F3jH87EZtEqRsUjkEbGy';  -- 枫塘斗渠_1 [#5#]

-- ---------- 桥坊支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'ncoIhJsHiyujFVEBvAZq';  -- 桥坊支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'nVCSamwzV8kMeYJZb4gO';  -- 桥坊支渠节制闸_1 [#5#]

-- ---------- 横山支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'mYP1ueE987WFfxItzcg2';  -- 横山支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'YV1ClQugDX7S6QqgXZxh';  -- 横山支渠进水闸_1 [#5#]

-- ---------- 武昌支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'XF91gnrjYQkosoafb7uX';  -- 武昌支渠上游_1 [#5#]  Excel:武昌支渠进水闸（原灌子洞节制闸）
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '6CN6WW1hOhpgTsMsYeG4';  -- 武昌支渠下游_1 [#5#]  Excel:武昌支渠进水闸（原灌子洞节制闸）

-- ---------- 段垅泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'nIltsWdeFOmkxjxCB94v';  -- 段垅泄洪闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'gNrfvn9EdNF2oxW95uUv';  -- 段垅泄洪闸_1 [#5#]

-- ---------- 毕岭支渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'pCHm86xjTnfTk82DSFny';  -- 毕岭支渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'qqigWO5jLSXYBOJfV8Bh';  -- 毕岭支渠_1 [#5#]

-- ---------- 毕岭管理所 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNJZD-05' WHERE id = 'NjDP4nGBG7Dh5usfwByP';  -- 毕岭管理所雨量计# [#2#]

-- ---------- 毕岭节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'MDrYat3SndmK0XehVrR7';  -- 毕岭节制闸 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '5jlGATv3Cbr7UsJzBgnW';  -- 毕岭节制闸下游 [#5#]

-- ---------- 永兴支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'QXVrxcl4hkxCymfDJiK2';  -- 永兴支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'Q0XzH3GxkQlXHKBkoALA';  -- 永兴支渠进水闸_1 [#5#]

-- ---------- 汪元渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'msPcXCKAiMnorC9vva9V';  -- 汪元渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'QXklpkQXPxQmQp1PD6K7';  -- 汪元渡槽 [#5#]

-- ---------- 汪元管理所 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNJZD-05' WHERE id = '93M5gtYv5uHtUb8PPYIB';  -- 汪元管理所雨量计# [#2#]

-- ---------- 汪元节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '8zwdCWUz8OH8uq7HeEgD';  -- 汪元节制闸 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'SNucIdYvbIjh1WJfxx28';  -- 汪元节制闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'vO75rcB9kUCnNaPCkZ1M';  -- 汪元节制闸下游 [#5#]

-- ---------- 汪屋灌溉涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '智旭FUC660-2M-AR' WHERE id = '51oVcSSpn3q44crNWEH7';  -- 汪屋灌溉涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'xYhWJVeApn3Wg8dSJun9';  -- 汪屋涵_1 [#5#]

-- ---------- 汪岭边涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'bCXGROPqHJnsdrIvCx8S';  -- 汪岭边涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'ORi7tyClwX1AIVRlVCrL';  -- 汪岭边涵_1 [#5#]

-- ---------- 洋普支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'kzKO7TJvoOC53PloN3Jp';  -- 洋普支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'p1aiBmr25XfZM06OTnXl';  -- 洋普支渠进水闸_1 [#5#]

-- ---------- 洪龙泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'wDBMJleuqJylO2nQa7Ke';  -- 洪龙泄洪闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'QSyGP2oGeKz6H3ITtErS';  -- 洪龙泄洪闸_1 [#5#]

-- ---------- 海棠洼节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '4ZrjRYeRg0wwICBJ1al5';  -- 海棠洼节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'Yiy80hvrO7HornxY8ppD';  -- 海棠洼节制闸_1 [#5#]

-- ---------- 渠首管理所 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNJZD-05' WHERE id = 'WnJTNIieuUvPLWiZKbXL';  -- 渠首管理所雨量计# [#2#]

-- ---------- 渠首进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '46kDkhaJmKMTsizTrnSc';  -- 渠首进水闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'bPXN3fmdxlOh8aNWZTtZ';  -- 渠首进水闸下游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'HJSSfPeaFbrcaV9U3MhE';  -- 渠首进水闸室内 [#5#]

-- ---------- 滴露节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'zwmn9HK0l7babcNHfZBe';  -- 滴露节制闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'MXds8KpNM63jkdDS8O1f';  -- 滴露节制闸下游_1 [#5#]

-- ---------- 灌子洞节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '6FU1B6kKmpQpqVqLXxoQ';  -- 灌子洞节制闸_1 [#5#]  Excel:灌子洞节制闸（武昌支渠进水闸）

-- ---------- 父子岭分水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '0M2yPU7UAizaZbKSHZdK';  -- 父子岭分水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'TMmQMjrpIg8YDofVbaif';  -- 父子岭分水闸_1 [#5#]

-- ---------- 父子岭渡槽分水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'rF4CD8f7UuEkdFQtBVln';  -- 父子岭渡槽分水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'otGa7UnsrpnPfCSadJHe';  -- 父子岭渡槽_1 [#5#]

-- ---------- 牛山咀斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'fZ3MfzbYjEFtghhhTLEP';  -- 牛山咀斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '7Dh4TdIE73vUZ1DsJyd0';  -- 牛山咀斗渠_1 [#5#]

-- ---------- 猪娘塘斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '智旭FUC660-2M-AR' WHERE id = 'zwVLMJ516rB1lVfOMgTy';  -- 猪娘塘斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'N4OxkSca2nqvzUE6Kezl';  -- 猪娘塘斗渠_1 [#5#]

-- ---------- 王卓屋泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'QXESZjd16UOmAgEjTNFX';  -- 王卓屋泄洪闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'tVyb3UGmWowaUYBmjf2Y';  -- 王卓屋泄洪闸_1 [#5#]

-- ---------- 王塘泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'jIGd7dhV1yoeytY85gb3';  -- 王塘泄洪闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'j3AkmfZ4PVALaIlqeihG';  -- 王塘泄洪闸下游_1 [#5#]

-- ---------- 王塘节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'z0KByE5zEty7Sv5KDrHE';  -- 王塘节制闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '9o7elDwXQpP17alcBHqE';  -- 王塘节制闸下游_1 [#5#]

-- ---------- 王家桥节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'TWIcen3fXAwTXuPQb2ha';  -- 王家桥上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'OTa7pI1MlszBmISgZWVB';  -- 王家桥下游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '63NCnM5R7j1gWz01Gy6n';  -- 王家桥节制闸 [#5#]

-- ---------- 王居山支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'B5hq2y3RCcuXMJlAOXaB';  -- 王居山支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'sqPPI0EtJtAQCfxTOSCK';  -- 王居山支渠进水闸_1 [#5#]

-- ---------- 王岭 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'VCTIca6rOHH3mo9KoDL2';  -- 王岭流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'oAYLUlUACkU5RqrY3FGx';  -- 王岭支渠_1 [#5#]

-- ---------- 甘河洲渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'Mz8bxrgCizZiiid9jLu7';  -- 甘河洲渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'vnkbb1P35CWNoUalwVbf';  -- 甘河洲渡槽_1 [#5#]

-- ---------- 盘龙斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '5gRc72cuGsYc3R0zeqeO';  -- 盘龙斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'x7vFylMFF1TTX7y7hgCu';  -- 盘龙斗渠_1 [#5#]

-- ---------- 矮脚支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'NgBAYLKqCHhm3zWJZEzB';  -- 矮脚支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'v5RkqDJKlcdGs9CDSVUu';  -- 矮脚支渠进水闸_1 [#5#]

-- ---------- 碎石支渠节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'BoTSNvnM6qOlKL3tZvdS';  -- 碎石支渠节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'BpCHzTU5hF6LOXXxNN2a';  -- 碎石支渠节制闸_1 [#5#]

-- ---------- 程岭支渠节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'MXMKDwMr54gncCFty6dx';  -- 程岭支渠节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'cfQcivTJ4TCPDK55TJYW';  -- 程岭支渠节制闸_1 [#5#]

-- ---------- 程岭节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'TZeu8gMAcNaCy6FFkFlA';  -- 程岭节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'Ubf9Utoov8P9P53JSXrD';  -- 程岭节制闸_1 [#5#]

-- ---------- 稳石支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'xL8oQIdue2K2vx1gB5mx';  -- 稳石支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'nWC1BJ7HIXetC9kHcdDC';  -- 稳石支渠_1 [#5#]

-- ---------- 立新支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'V4zlCIxqo7knPmswAlQS';  -- 立新支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '7fmNEJGPdjxV0J5wbC8n';  -- 立新支渠进水闸_1 [#5#]

-- ---------- 竹山斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '6CtmGp0ZcDCONNYOo9ns';  -- 竹山斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'MtvUCBSNvbD176bm4QPp';  -- 竹山斗渠_1 [#5#]

-- ---------- 红岭支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'subQIAUXRtP76aKibvAe';  -- 红岭支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'a00J9scotb6Qj0snDAWq';  -- 红岭支渠进水闸_1 [#5#]

-- ---------- 红旗支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'iddSF96aH72H4UXg6I4M';  -- 红旗支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'V0KR0GSY2d0ECcH0TDSz';  -- 红旗支渠进水闸_1 [#5#]

-- ---------- 红旗斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '7Z1TRyQLHCX16Ygn7ghd';  -- 红旗斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'vLPCdcJA4k8HgyEsaquH';  -- 红旗斗渠_1 [#5#]

-- ---------- 红星支渠节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'FLfhxjivFuyBduDa0Rb4';  -- 红星支渠节制闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'rNM8o2eLu0X9uzH2LSUj';  -- 红星支渠节制闸_1 [#5#]

-- ---------- 联合支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'FjCyU1kW0Z5qx9x8hHCW';  -- 联合支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'xfF5lCm8Yg9jWXEBigAz';  -- 联合支渠进水闸_1 [#5#]

-- ---------- 肖家咀斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'zYuVuIE8ySBIiXDi6CtC';  -- 肖家咀斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'CfhtCEvuyyhyzuJmXx39';  -- 肖家咀斗渠_1 [#5#]

-- ---------- 肖弄支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '2fPcuEkrFMXBQsvQn74j';  -- 肖弄支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'p3AFaLEgCXXuRZsM6Swn';  -- 肖弄支渠进水闸_1 [#5#]

-- ---------- 胜利支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'CAR1M16hkWgDHQxtN4C5';  -- 胜利支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '2iHx8nro7vqMVg3b6UD4';  -- 胜利支渠进水闸_1 [#5#]

-- ---------- 胜天支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'kc8G95pFWr3l5iQBD0IJ';  -- 胜天支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'yRqjwxEzQSkhMg1iAcXx';  -- 胜天支渠进水闸_1 [#5#]

-- ---------- 腊皖分干渠节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'tPzIGEpd6vm9xCHkabSV';  -- 腊皖分干渠节制闸_1 [#5#]

-- ---------- 花咀边涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'y5nzAwJfXXnKAcYLOEee';  -- 花咀边涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'E1XmviO4wuQPD4c5fDDk';  -- 花咀边涵_1 [#5#]

-- ---------- 荆安支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '7VwsinU01fxBb9X1oDou';  -- 荆安支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'lnykuYOwvdhf03PNrLrI';  -- 荆安支渠进水闸_1 [#5#]

-- ---------- 荞麦岭泄洪河 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'hKHaF1L98Ur5bW6U4F99';  -- 荞麦岭泄洪闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'wkkNUryB8oo3D0bsF9IP';  -- 荞麦岭泄洪闸下游_1 [#5#]

-- ---------- 虎塘支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '98kSDWiXLOmPQc0lMjhi';  -- 虎塘支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '8yJoMbnqn1mW18btkdOa';  -- 虎塘支渠进水闸_1 [#5#]

-- ---------- 虎形潭渡槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MVNF+精波PWRV430-CAPIN' WHERE id = 'BJ8VPeQtqCK1cSgrArYE';  -- 虎形潭渡槽流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'nYfH32nanGRVhjn7atNN';  -- 虎形潭渡槽 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'gMBaHm3GOMyvAZYIQD96';  -- 虎形潭渡槽_2 [#5#]

-- ---------- 袁畈泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '22MK1kxH9lBk6WzXw2nf';  -- 袁畈泄洪闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'HVVK1EJkl4vQCcdkD7sa';  -- 袁畈泄洪闸下游_1 [#5#]

-- ---------- 许岭泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'nnHUKL7qY04ifN7t1gNJ';  -- 许岭泄洪闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '6rqroS5zDLESn6Ea2SYS';  -- 许岭泄洪闸_1 [#5#]

-- ---------- 郝大屋泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'lFFGjJzLA2ahDr0xYvIn';  -- 郝大屋泄洪闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'eUZ8LiGGfuAiY84Jj5gt';  -- 郝大屋泄洪闸下游_1 [#5#]

-- ---------- 郝梓屋泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'koUtSFOaZIvHm46DRdlL';  -- 郝梓屋泄洪闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = '2dzFgzeYuTZwwTODxq3q';  -- 郝梓屋泄洪闸下游_1 [#5#]

-- ---------- 郭塘支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'Xt19dvTrIRveBKnIZ9UW';  -- 郭塘支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'EnRB3jrzSrECSYOt2qR9';  -- 郭塘支渠进水闸_1 [#5#]

-- ---------- 金山斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'leQp1oeCQbuYA7PaiASS';  -- 金山斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'YAzz0wIcbNORhFhbQqnV';  -- 金山斗渠_1 [#5#]

-- ---------- 金桥斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'kztZdlA4qPS2n6YhM0B2';  -- 金桥斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'dko6mtIRZt1xrHjdrKHb';  -- 金桥斗渠_1 [#5#]

-- ---------- 铁寨支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'XTfJZiMrMf7ZfFXYkOiT';  -- 铁寨支渠_1 [#5#]

-- ---------- 长喉槽 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNJZD-05' WHERE id = '0o6oif2XnypkGqhzdU0G';  -- 长喉槽雨量计# [#2#]

-- ---------- 长岭跌水节制闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'G4MCY4XfNiqQmYBM75f5';  -- 长岭跌水节制闸上游_1 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'I2DQdh3GMpIhZ7IQQ9yh';  -- 长岭跌水节制闸下游_1 [#5#]

-- ---------- 长马分干渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'Dxj2aeTFiAfN9nwnOyla';  -- 长马分干渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'pufKei7C8mRzV3T1yXcr';  -- 长马分干渠进水闸_1 [#5#]

-- ---------- 门岭涵（右岸） ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'fy1snUh2oIQlQulRWHTs';  -- 门岭涵（右岸）流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'soQSomxeysDLuViBOKca';  -- 门岭涵_1 [#5#]

-- ---------- 集岭电站进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'Z4sNjeRpNBoz2lBBENq0';  -- 集岭电站进水闸上游 [#5#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-IPC-HFW34XYZM-ABCDE' WHERE id = 'mC8Eh5qp8kKuGwcse3XS';  -- 集岭电站进水闸下游 [#5#]

-- ---------- 集岭管理所 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '中水三立SUNJZD-05' WHERE id = 'jpffFuEVnfA34SDDfAAt';  -- 集岭管理所雨量计# [#2#]

-- ---------- 青阳斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'yVFHNo4qa7tAJETkweTE';  -- 青阳斗渠_1 [#5#]

-- ---------- 马丰泄洪闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'FaLQzHcbGm21CXvCfxnM';  -- 马丰泄洪闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'yT06RJG9BQmaaT9gBUHI';  -- 马丰泄洪闸_1 [#5#]

-- ---------- 鲁元支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = '2QHgZC2lDpbjyAPeE2L7';  -- 鲁元支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '0J2HWL3WUI6TGrtIK6Fp';  -- 鲁元支渠_1 [#5#]

-- ---------- 黄河斗渠 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'emhnxFtmIwgvD64i5H4u';  -- 黄河斗渠流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'FqtwsrTQTxyXsOk29d4d';  -- 黄河斗渠_1 [#5#]

-- ---------- 黄花庙直灌涵 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '智旭FUC660-2M-AR' WHERE id = 'mS5BvnRRbH31M33awh2Q';  -- 黄花庙直灌涵流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'lcuskz5UEcPTEhtWNnD6';  -- 黄花庙_1 [#5#]

-- ---------- 龙堰 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'HFSUQiDWAUOiZQ1R8BjN';  -- 龙堰流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = 'NPOxK49XE6wF8oX9MhB9';  -- 龙堰支渠_1 [#5#]

-- ---------- 龙王山支渠进水闸 ----------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '精波PWFL432-CAP8MV' WHERE id = 'aMN2v8tIDdrfPtGXCm9l';  -- 龙王山支渠进水闸流量计# [#3#]
UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" SET evkmpl = '大华DH-SD-6CABCDEF-XYZ-XY' WHERE id = '8KJhnkbzEzsuE7K6yTc5';  -- 红益支渠进水闸 [#5#]

COMMIT;

-- ============================================================================
-- 校验 1：按型号统计（预期 10 种取值）
-- SELECT COALESCE(NULLIF(trim(evkmpl),''),'(空)') AS 型号, count(*) AS 台数
--   FROM "qixiao-apaas"."t_auto_hltgq_water_device" GROUP BY 1 ORDER BY 2 DESC;
--
-- 校验 2：按设备类型 × 型号交叉
-- SELECT d.type AS 类型, d.evkmpl AS 型号, count(*) AS 台数
--   FROM "qixiao-apaas"."t_auto_hltgq_water_device" d
--  WHERE d.evkmpl IS NOT NULL AND trim(d.evkmpl) <> '' GROUP BY 1,2 ORDER BY 1,3 DESC;
--
-- 校验 3：确认无站点归属错写（型号为空或型号属别的站）
-- SELECT d.id, d.name, d.type, d.evkmpl, s.zzkaec
--   FROM "qixiao-apaas"."t_auto_hltgq_water_device" d
--   LEFT JOIN "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s ON s.id = d.site
--  WHERE d.evkmpl IS NOT NULL AND trim(d.evkmpl) <> '' ORDER BY s.zzkaec, d.type;
--
-- 回滚（执行前全表只有 1 台有值：导入测试/测试设备1 = 123，其余 458 台 evkmpl 均为空，
--         因此不必依赖备份表，直接置空即可；若当时建了备份表则用下方注释掉的还原语句）：
-- UPDATE "qixiao-apaas"."t_auto_hltgq_water_device"
--    SET evkmpl = NULL
--  WHERE trim(COALESCE(evkmpl,'')) <> '' AND COALESCE(evkmpl,'') <> '123';
--
-- 若已建备份表 t_auto_hltgq_water_device_bak_model2_20261026，用备份还原：
-- UPDATE "qixiao-apaas"."t_auto_hltgq_water_device" d SET evkmpl = b.evkmpl
--   FROM "qixiao-apaas"."t_auto_hltgq_water_device_bak_model2_20261026" b WHERE b.id = d.id;
-- ============================================================================
