CREATE INDEX IF NOT EXISTS idx_gate_site_tm
    ON "qixiao-apaas"."t_auto_hltgq_water_gate" (site, gate_no, tm DESC);
CREATE INDEX IF NOT EXISTS idx_wt_site_tm
    ON "qixiao-apaas"."t_auto_hltgq_water_wt_nfo" (site, tm DESC);
CREATE INDEX IF NOT EXISTS idx_rain_stcd_tm
    ON "qixiao-apaas".t_auto_hltgq_water_rain_info (STCD, TM DESC);
CREATE INDEX IF NOT EXISTS idx_river_stcd_tm
    ON "qixiao-apaas".t_auto_hltgq_water_river_info (STCD, TM DESC);
-- 闸站监测电压取数：vol_info 按站点取最新一条（DISTINCT ON (site) ORDER BY site, tm DESC），
-- 无该索引时电压子查询需全表扫描排序（实测约 350ms）；生产库建议以 CREATE INDEX CONCURRENTLY 人工执行
CREATE INDEX IF NOT EXISTS idx_vol_site_tm
    ON "qixiao-apaas".t_auto_hltgq_water_vol_info (site, tm DESC);

-- ========== 库容曲线表 ==========
CREATE UNIQUE INDEX uk_level_storage_water_level ON t_auto_hltgq_water_level_storage(water_level);

-- ========== 短期来水预测 ==========
CREATE INDEX idx_short_forecast_record_created ON t_auto_hltgq_water_short_forecast_record(created_at DESC);
CREATE INDEX idx_short_forecast_record_start_date ON t_auto_hltgq_water_short_forecast_record(start_date);
CREATE INDEX idx_short_forecast_daily_record_id ON t_auto_hltgq_water_short_forecast_daily(record_id);
CREATE UNIQUE INDEX uk_short_forecast_daily_record_date ON t_auto_hltgq_water_short_forecast_daily(record_id, forecast_date);
CREATE INDEX idx_short_forecast_daily_forecast_date ON t_auto_hltgq_water_short_forecast_daily(forecast_date);

-- ========== 中长期来水预测 ==========
CREATE INDEX idx_long_predict_record_created ON t_auto_hltgq_water_long_predict_record(created_at DESC);
CREATE INDEX idx_long_predict_record_scenario ON t_auto_hltgq_water_long_predict_record(scenario);
CREATE INDEX idx_long_predict_tenday_record_id ON t_auto_hltgq_water_long_predict_tenday(record_id);
CREATE INDEX idx_long_predict_tenday_predict_date ON t_auto_hltgq_water_long_predict_tenday(predict_date);
CREATE INDEX idx_long_predict_monthly_record_stat ON t_auto_hltgq_water_long_predict_monthly(record_id, stat_date);

-- ========== 需水预测 ==========
CREATE INDEX idx_demand_record_created ON t_auto_hltgq_water_demand_record(created_at DESC);
CREATE INDEX idx_demand_record_guarantee_created ON t_auto_hltgq_water_demand_record(guarantee_rate, created_at DESC);
CREATE INDEX idx_demand_branch_detail_record_branch ON t_auto_hltgq_water_demand_branch_detail(record_id, branch_name);
CREATE INDEX idx_demand_branch_detail_record_tenday ON t_auto_hltgq_water_demand_branch_detail(record_id, tenday_label);
CREATE INDEX idx_demand_branch_detail_record_district ON t_auto_hltgq_water_demand_branch_detail(record_id, district);
CREATE INDEX idx_demand_area_summary_record_type_name ON t_auto_hltgq_water_demand_area_summary(record_id, summary_type, area_name);

-- ========== 水量损失预测 ==========
CREATE INDEX idx_loss_record_created ON t_auto_hltgq_water_loss_record(created_at DESC);
CREATE INDEX idx_loss_record_mode ON t_auto_hltgq_water_loss_record(mode);
CREATE INDEX idx_loss_detail_record_date ON t_auto_hltgq_water_loss_detail(record_id, data_date);

-- ========== 水资源配置 ==========
CREATE INDEX idx_allocate_record_created ON t_auto_hltgq_water_allocate_record(created_at DESC);
CREATE INDEX idx_allocate_record_mode_created ON t_auto_hltgq_water_allocate_record(mode, created_at DESC);
CREATE INDEX idx_allocate_record_demand_id ON t_auto_hltgq_water_allocate_record(demand_record_id);
CREATE INDEX idx_allocate_tenday_record_sort ON t_auto_hltgq_water_allocate_tenday(record_id, sort_order);

-- ========== 配水调度预测 ==========
CREATE INDEX idx_decision_record_created ON t_auto_hltgq_water_decision_record(created_at DESC);
CREATE INDEX idx_decision_record_source_created ON t_auto_hltgq_water_decision_record(source, created_at DESC);
CREATE INDEX idx_decision_record_allocate_id ON t_auto_hltgq_water_decision_record(allocate_record_id);
CREATE INDEX idx_decision_record_demand_id ON t_auto_hltgq_water_decision_record(demand_record_id);
CREATE INDEX idx_decision_scale_factor_record_sort ON t_auto_hltgq_water_decision_scale_factor(record_id, sort_order);
CREATE INDEX idx_decision_branch_detail_record_branch ON t_auto_hltgq_water_decision_branch_detail(record_id, branch_name);
CREATE INDEX idx_decision_branch_detail_record_tenday ON t_auto_hltgq_water_decision_branch_detail(record_id, tenday_label);
CREATE INDEX idx_decision_branch_detail_record_satisfied ON t_auto_hltgq_water_decision_branch_detail(record_id, is_satisfied);

-- 短期来水预测
ALTER TABLE t_auto_hltgq_water_short_forecast_record ALTER COLUMN request_json TYPE TEXT;
ALTER TABLE t_auto_hltgq_water_short_forecast_record ALTER COLUMN rainfall_json TYPE TEXT;
ALTER TABLE t_auto_hltgq_water_short_forecast_record ALTER COLUMN custom_discharge_json TYPE TEXT;
-- 2026-09 小时尺度重构：/forecast V3 契约（end/start_level/三开关）
ALTER TABLE t_auto_hltgq_water_short_forecast_record ADD COLUMN IF NOT EXISTS end_date TIMESTAMP;
ALTER TABLE t_auto_hltgq_water_short_forecast_record ADD COLUMN IF NOT EXISTS start_level DOUBLE PRECISION;
ALTER TABLE t_auto_hltgq_water_short_forecast_record ADD COLUMN IF NOT EXISTS enable_power TEXT;
ALTER TABLE t_auto_hltgq_water_short_forecast_record ADD COLUMN IF NOT EXISTS enable_tunnel TEXT;
ALTER TABLE t_auto_hltgq_water_short_forecast_record ADD COLUMN IF NOT EXISTS enable_spillway TEXT;
CREATE INDEX IF NOT EXISTS idx_short_forecast_record_end_date ON t_auto_hltgq_water_short_forecast_record(end_date);

-- 中长期来水预测
ALTER TABLE t_auto_hltgq_water_long_predict_record ALTER COLUMN request_json TYPE TEXT;
-- 模型评估指标（val_metrics 全量采集：MAE/MSE/R2/SMAPE，NSE 模型不返回）
ALTER TABLE t_auto_hltgq_water_long_predict_record ADD COLUMN IF NOT EXISTS mae DOUBLE PRECISION;
ALTER TABLE t_auto_hltgq_water_long_predict_record ADD COLUMN IF NOT EXISTS mse DOUBLE PRECISION;
ALTER TABLE t_auto_hltgq_water_long_predict_record ADD COLUMN IF NOT EXISTS r2 DOUBLE PRECISION;
ALTER TABLE t_auto_hltgq_water_long_predict_record ADD COLUMN IF NOT EXISTS smape DOUBLE PRECISION;

-- 需水预测
ALTER TABLE t_auto_hltgq_water_demand_record ALTER COLUMN request_json TYPE TEXT;

-- 水量损失预测
ALTER TABLE t_auto_hltgq_water_loss_record ALTER COLUMN request_json TYPE TEXT;
ALTER TABLE t_auto_hltgq_water_loss_record ALTER COLUMN rainfall_json TYPE TEXT;

-- 水资源配置
ALTER TABLE t_auto_hltgq_water_allocate_record ALTER COLUMN request_json TYPE TEXT;

-- 配水调度预测
ALTER TABLE t_auto_hltgq_water_decision_record ALTER COLUMN request_json TYPE TEXT;
ALTER TABLE t_auto_hltgq_water_decision_record ALTER COLUMN tens TYPE TEXT;

-- ========== 墒情预测（2026-09 新增，模型 /moisture） ==========
-- 注意平台命名：moisture_detail 为方案主表（含 scheme_name），moisture_record 为逐小时明细（含 record_id）
CREATE TABLE IF NOT EXISTS t_auto_hltgq_water_moisture_detail (
    id VARCHAR(64) PRIMARY KEY,
    scheme_name VARCHAR(255),
    status VARCHAR(32),
    del_flag VARCHAR(8),
    error_msg TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    station_count DOUBLE PRECISION,
    request_json TEXT,
    corp_code VARCHAR(64),
    created_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);
CREATE TABLE IF NOT EXISTS t_auto_hltgq_water_moisture_record (
    id VARCHAR(64) PRIMARY KEY,
    record_id VARCHAR(64),
    site VARCHAR(64),
    tm TIMESTAMP,
    rainfall DOUBLE PRECISION,
    mten DOUBLE PRECISION,
    mtwenty DOUBLE PRECISION,
    mthirty DOUBLE PRECISION,
    g_value DOUBLE PRECISION,
    drought_level VARCHAR(32),
    corp_code VARCHAR(64),
    created_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_moisture_detail_created ON t_auto_hltgq_water_moisture_detail(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moisture_record_detail_tm ON t_auto_hltgq_water_moisture_record(record_id, site, tm);
-- 列兜底（平台已建表但缺 2026-09 新增列时补齐，幂等）
ALTER TABLE t_auto_hltgq_water_moisture_detail ADD COLUMN IF NOT EXISTS start_time TIMESTAMP;
ALTER TABLE t_auto_hltgq_water_moisture_detail ADD COLUMN IF NOT EXISTS end_time TIMESTAMP;
ALTER TABLE t_auto_hltgq_water_moisture_detail ADD COLUMN IF NOT EXISTS station_count DOUBLE PRECISION;
ALTER TABLE t_auto_hltgq_water_moisture_record ADD COLUMN IF NOT EXISTS g_value DOUBLE PRECISION;
ALTER TABLE t_auto_hltgq_water_moisture_record ADD COLUMN IF NOT EXISTS drought_level VARCHAR(32);

-- ========== H5 消息中心（2026-09 新增） ==========
-- 消息接收表：summary/page 接口被调用时按接收规则为当前登录人同步补建未读记录（幂等），
-- 阅读后 UPDATE 为已读；一人对一条消息仅一条记录（同步 INSERT 带 NOT EXISTS，不依赖唯一约束）
CREATE TABLE IF NOT EXISTS t_auto_hltgq_water_message_receive (
    id           VARCHAR(64) PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,     -- 收信人主键（t_apaas_uc_user.id）
    message_type VARCHAR(32) NOT NULL,     -- 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集
    message_id   VARCHAR(64) NOT NULL,     -- 对应业务表主键
    read_time    TIMESTAMP,                -- 已读时间（未读为 null）
    is_read      VARCHAR(8)  NOT NULL DEFAULT '#1#',  -- #1# 未读（发送时默认）/ #2# 已读（查看后 UPDATE）
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64),
    CONSTRAINT uk_message_receive UNIQUE (message_type, message_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_message_receive_user
    ON t_auto_hltgq_water_message_receive(user_id, message_type, is_read);

-- ========== H5 消息中心：模型计算消息（#5#，2026-09 新增） ==========
-- 7 张方案主表按 created_by 定位提交人（同步 UNION 各段过滤条件）
CREATE INDEX IF NOT EXISTS idx_short_forecast_record_by_status ON t_auto_hltgq_water_short_forecast_record(created_by, status);
CREATE INDEX IF NOT EXISTS idx_long_predict_record_by_status ON t_auto_hltgq_water_long_predict_record(created_by, status);
CREATE INDEX IF NOT EXISTS idx_loss_record_by_status ON t_auto_hltgq_water_loss_record(created_by, status);
CREATE INDEX IF NOT EXISTS idx_demand_record_by_status ON t_auto_hltgq_water_demand_record(created_by, status);
CREATE INDEX IF NOT EXISTS idx_moisture_detail_by_status ON t_auto_hltgq_water_moisture_detail(created_by, status);
CREATE INDEX IF NOT EXISTS idx_allocate_record_by_status ON t_auto_hltgq_water_allocate_record(created_by, status);
CREATE INDEX IF NOT EXISTS idx_decision_record_by_status ON t_auto_hltgq_water_decision_record(created_by, status);
-- 接收表分页第一段：按收信人+类型过滤、按同步写入的方案完成时间倒序
CREATE INDEX IF NOT EXISTS idx_message_receive_user_type_created ON t_auto_hltgq_water_message_receive(user_id, message_type, created_at DESC);

-- 消息接收规则表：message_type × 接收维度（部门/岗位/角色/人员编码，多个逗号分隔）；site_id 仅告警可用
-- 2026-10 消息发送设置：增加 site_id（站点范围，仅 #1# 告警生效；空=不限站点；命中多条规则合并范围）
CREATE TABLE IF NOT EXISTS t_auto_hltgq_water_message_rule (
    id           VARCHAR(64) PRIMARY KEY,
    message_type VARCHAR(32) NOT NULL,     -- 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集 / #4# 值班提醒 / #5# 模型计算
    target_type  VARCHAR(16) NOT NULL,     -- 接收维度：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员
    target_id    VARCHAR(1024) NOT NULL,   -- 目标编码，多个以英文逗号分隔
    site_id      VARCHAR,                  -- 站点范围（仅告警 #1# 生效）：站点档案主键，多个逗号分隔；空=不限站点
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64),
    CONSTRAINT uk_message_rule UNIQUE (message_type, target_type, target_id)
);
-- 列兜底（表已建但缺 2026-10 新增列时补齐，幂等；独立执行版见项目根 message-rule-ddl.sql）
ALTER TABLE t_auto_hltgq_water_message_rule ADD COLUMN IF NOT EXISTS site_id VARCHAR;

-- ========== 系统资源监控（2026-09 新增，页面 system-monitor.html） ==========
-- 故障记录：软件故障（GlobalExceptionHandler 上游依赖异常埋点）+ 系统故障（服务重启/依赖中断/资源越限）
-- 同键（category+fault_type+fault_source）在抑制窗口内只记一条，落库异步、失败不影响业务链路
CREATE TABLE IF NOT EXISTS t_auto_hltgq_sys_fault_record (
    id           VARCHAR(64) PRIMARY KEY,
    category     VARCHAR(16) NOT NULL,     -- soft 软件故障 / system 系统故障
    fault_type   VARCHAR(64) NOT NULL,     -- 类型：上游服务调用失败 / 服务启动 / 资源越限
    fault_source VARCHAR(255),             -- 来源：服务名（mq/device/archive/model/auth）或资源名（CPU/内存/磁盘挂载点/表空间名）
    fault_level  VARCHAR(16),              -- error / warn
    fault_desc   TEXT,                     -- 描述（含阈值与实际值等）
    occur_time   TIMESTAMP NOT NULL,       -- 发生时间
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_sys_fault_category_time ON t_auto_hltgq_sys_fault_record(category, occur_time DESC);
CREATE INDEX IF NOT EXISTS idx_sys_fault_time ON t_auto_hltgq_sys_fault_record(occur_time DESC);

-- 资源采样：定时采集快照落库（“监控信息保存”，供历史回溯与报警子系统取用），默认 5 分钟一行
CREATE TABLE IF NOT EXISTS t_auto_hltgq_sys_resource_sample (
    id               VARCHAR(64) PRIMARY KEY,
    sample_time      TIMESTAMP NOT NULL,
    scope            VARCHAR(16),          -- host 宿主机视角 / container 容器视角
    hostname         VARCHAR(128),
    cpu_percent      DOUBLE PRECISION,
    load1            DOUBLE PRECISION,
    load5            DOUBLE PRECISION,
    load15           DOUBLE PRECISION,
    mem_total        BIGINT,
    mem_used         BIGINT,
    mem_percent      DOUBLE PRECISION,
    swap_total       BIGINT,
    swap_used        BIGINT,
    swap_percent     DOUBLE PRECISION,
    disk_json        TEXT,                 -- 各磁盘分区快照（JSON 数组）
    db_json          TEXT,                 -- 数据库表空间快照（JSON 数组）
    corp_code        VARCHAR(64),
    created_at       TIMESTAMP,
    created_by       VARCHAR(64),
    updated_at       TIMESTAMP,
    updated_by       VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_sys_sample_time ON t_auto_hltgq_sys_resource_sample(sample_time DESC);

-- ========== 出入库管理（2026-10 新增，对应《出入库管理功能设计方案》3.0~3.5） ==========
-- 单据保存即生效、不可编辑删除；数量全部整数；类型枚举统一 #xx# 文本编码（与 message_receive 及平台字典风格一致）。
-- 平台既有表 twufyx(仓库)/slaifi(物资)/tzkioe(台账) 仅关联读写、不改结构；关联字段为单列（设计稿 xxx/xxxId 双列写法按平台事实收敛为单列）。

-- 3.0 单据编号序列表：按 (serial_type, serial_date) 行锁取号，编号 = RK/CK + yyyyMMdd + 4 位序号
-- 取号在独立事务提交，主单据事务失败时序号不回滚、跳号属预期；禁止 SELECT MAX+1。
-- KES 一步取号（推荐）：INSERT ... ON CONFLICT (serial_type, serial_date)
--     DO UPDATE SET current_seq = t_auto_hltgq_water_order_no_seq.current_seq + 1 RETURNING current_seq
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_order_no_seq (
    id          VARCHAR(64) PRIMARY KEY,
    serial_type VARCHAR(8)  NOT NULL,             -- 编号规则：RK 入库 / CK 出库
    serial_date VARCHAR(8)  NOT NULL,             -- 日期 yyyyMMdd（取号时刻系统日期，编号组成部分）
    current_seq INTEGER     NOT NULL DEFAULT 0,   -- 当日当前序号（每次取号 +1）
    corp_code   VARCHAR(64),
    created_at  TIMESTAMP,
    created_by  VARCHAR(64),
    updated_at  TIMESTAMP,                        -- 最近一次取号时间
    updated_by  VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_no_seq_type_date
    ON "qixiao-apaas".t_auto_hltgq_water_order_no_seq(serial_type, serial_date);

-- 3.1 入库单主表：保存即生效，无编辑/删除；order_no 系统生成只读
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_inbound_order (
    id            VARCHAR(64)  PRIMARY KEY,
    order_no      VARCHAR(32)  NOT NULL,          -- 单据编号：RK+yyyyMMdd+4位序号（唯一）
    inbound_type  VARCHAR(16)  NOT NULL,          -- 入库类型：#1# 采购入库 / #2# 调拨入库 / #3# 退料入库 / #4# 其他入库
    order_date    DATE         NOT NULL,          -- 单据日期（默认当天）
    warehouse_id  VARCHAR(64)  NOT NULL,          -- 仓库 ID（twufyx.id）
    source_unit   VARCHAR(255) NOT NULL,          -- 供应商/来源单位（调拨入库填来源仓库名称）
    department_id VARCHAR(64)  NOT NULL,          -- 归口科室 ID（默认取仓库 dosozz，可改）
    operator_id   VARCHAR(64)  NOT NULL,          -- 经办人 ID（默认取仓库管理员 ivplza，可改）
    operator_name VARCHAR(64),                    -- 经办人姓名快照
    remark        VARCHAR(500),                   -- 备注
    del_flag      VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- #1# 已删除 / #2# 正常（本期不提供删除，恒 #2#）
    corp_code     VARCHAR(64),
    created_at    TIMESTAMP,
    created_by    VARCHAR(64),
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_inbound_order_no
    ON "qixiao-apaas".t_auto_hltgq_water_inbound_order(order_no);
CREATE INDEX IF NOT EXISTS idx_inbound_order_date
    ON "qixiao-apaas".t_auto_hltgq_water_inbound_order(order_date DESC);

-- 3.2 入库单明细：逐行库存增加；快照字段固化物资要素，防目录后续修改影响历史单据
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail (
    id             VARCHAR(64)  PRIMARY KEY,
    order_id       VARCHAR(64)  NOT NULL,         -- 入库单 ID（inbound_order.id）
    material_id    VARCHAR(64)  NOT NULL,         -- 物资 ID（slaifi.id）
    material_code  VARCHAR(64)  NOT NULL,         -- 物资编号快照（slaifi.higsdd）
    material_name  VARCHAR(255) NOT NULL,         -- 物资名称快照（slaifi.avlsdv）
    material_brand VARCHAR(255),                  -- 品牌快照（slaifi.fqztqm，可空）
    material_spec  VARCHAR(255),                  -- 规格型号快照（默认 slaifi.iwnyuk，单据录入可手工覆写，可空）
    material_unit  VARCHAR(64),                   -- 计量单位快照（slaifi.boipik）
    quantity       INTEGER      NOT NULL,         -- 入库数量（正整数，应用层校验）
    corp_code      VARCHAR(64),
    created_at     TIMESTAMP,
    created_by     VARCHAR(64),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_inbound_order_detail_order
    ON "qixiao-apaas".t_auto_hltgq_water_inbound_order_detail(order_id);

-- 3.3 出库单主表：保存即生效，无编辑/删除；order_no 系统生成只读
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_outbound_order (
    id            VARCHAR(64)  PRIMARY KEY,
    order_no      VARCHAR(32)  NOT NULL,          -- 单据编号：CK+yyyyMMdd+4位序号（唯一）
    outbound_type VARCHAR(16)  NOT NULL,          -- 出库类型：#1# 工程领用 / #2# 调拨出库 / #3# 退料出库 / #4# 报废出库 / #5# 其他出库
    order_date    DATE         NOT NULL,          -- 单据日期（默认当天）
    warehouse_id  VARCHAR(64)  NOT NULL,          -- 仓库 ID（twufyx.id）
    receive_unit  VARCHAR(255) NOT NULL,          -- 领用单位/工程名称（调拨出库填目标仓库名称）
    department_id VARCHAR(64)  NOT NULL,          -- 归口科室 ID（默认取仓库 dosozz，可改）
    operator_id   VARCHAR(64)  NOT NULL,          -- 经办人 ID（默认取仓库管理员 ivplza，可改）
    operator_name VARCHAR(64),                    -- 经办人姓名快照
    remark        VARCHAR(500),                   -- 备注
    del_flag      VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- #1# 已删除 / #2# 正常（本期不提供删除，恒 #2#）
    corp_code     VARCHAR(64),
    created_at    TIMESTAMP,
    created_by    VARCHAR(64),
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_outbound_order_no
    ON "qixiao-apaas".t_auto_hltgq_water_outbound_order(order_no);
CREATE INDEX IF NOT EXISTS idx_outbound_order_date
    ON "qixiao-apaas".t_auto_hltgq_water_outbound_order(order_date DESC);

-- 3.4 出库单明细：台账乐观锁扣减（ejgxlq >= quantity）+ 影响行数兜底；stock_before 为扣减成功后的变动前值
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail (
    id             VARCHAR(64)  PRIMARY KEY,
    order_id       VARCHAR(64)  NOT NULL,         -- 出库单 ID（outbound_order.id）
    material_id    VARCHAR(64)  NOT NULL,         -- 物资 ID（slaifi.id，限当前仓库台账有库存的物资）
    material_code  VARCHAR(64)  NOT NULL,         -- 物资编号快照（slaifi.higsdd）
    material_name  VARCHAR(255) NOT NULL,         -- 物资名称快照（slaifi.avlsdv）
    material_brand VARCHAR(255),                  -- 品牌快照（slaifi.fqztqm，可空）
    material_spec  VARCHAR(255),                  -- 规格型号快照（默认台账行值，单据录入可手工覆写，可空）
    material_unit  VARCHAR(64),                   -- 计量单位快照（slaifi.boipik）
    quantity       INTEGER      NOT NULL,         -- 出库数量（正整数；前置校验 ≤ 可用库存，最终以台账 UPDATE 影响行数为准）
    stock_before   INTEGER,                       -- 变动前库存快照（台账 UPDATE 成功后回填，与流水 qty_before 一致）
    corp_code      VARCHAR(64),
    created_at     TIMESTAMP,
    created_by     VARCHAR(64),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_outbound_order_detail_order
    ON "qixiao-apaas".t_auto_hltgq_water_outbound_order_detail(order_id);

-- 3.5 库存变动流水：每行单据明细对应一条流水；台账页“出入库记录”按 created_at 倒序逐笔追溯
-- 盘点单（2026-10 设备盘点）启用预留枚举：change_type #3#盘盈/#4#盘亏 与 order_kind #3#盘点单
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_stock_change_log (
    id            VARCHAR(64)  PRIMARY KEY,
    ledger_id     VARCHAR(64)  NOT NULL,          -- 台账行 ID（tzkioe.id）
    material_id   VARCHAR(64)  NOT NULL,          -- 物资 ID（slaifi.id）
    warehouse_id  VARCHAR(64)  NOT NULL,          -- 仓库 ID（twufyx.id）
    change_type   VARCHAR(16)  NOT NULL,          -- 变动类型：#1# 入库 / #2# 出库 / #3# 盘盈 / #4# 盘亏
    order_kind    VARCHAR(16)  NOT NULL,          -- 单据类别：#1# 入库单 / #2# 出库单 / #3# 盘点单
    order_id      VARCHAR(64)  NOT NULL,          -- 单据 ID（inbound_order.id / outbound_order.id）
    order_no      VARCHAR(32)  NOT NULL,          -- 单据编号快照
    biz_type      VARCHAR(16)  NOT NULL,          -- 业务类型快照（入库/出库类型枚举值，按 order_kind 解释）
    change_qty    INTEGER      NOT NULL,          -- 变动数量（正整数，方向由 change_type 表达）
    qty_before    INTEGER      NOT NULL,          -- 变动前库存
    qty_after     INTEGER      NOT NULL,          -- 变动后库存
    counterparty  VARCHAR(255),                   -- 往来单位/领用人快照
    operator_name VARCHAR(64),                    -- 经手人快照
    order_date    DATE         NOT NULL,          -- 单据日期
    corp_code     VARCHAR(64),
    created_at    TIMESTAMP,                      -- 流水生成时间（“出入库记录”倒序依据，必填）
    created_by    VARCHAR(64),
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_stock_change_log_material_warehouse
    ON "qixiao-apaas".t_auto_hltgq_water_stock_change_log(material_id, warehouse_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_change_log_order
    ON "qixiao-apaas".t_auto_hltgq_water_stock_change_log(order_id);

-- 【可选】台账建账防重：并发首次入库同一物资+仓库时，防 tzkioe 建出两行台账
-- 应用层配合：INSERT ... ON CONFLICT DO NOTHING 后重试 UPDATE，或冲突则直接 UPDATE 既有行
-- 执行前核对无重复（当前 0 行，无风险）：
--   SELECT jzikio, oywydb, COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- 注意：tzkioe 属低代码平台表，平台侧若重建该表索引会丢失，需复核补建
CREATE UNIQUE INDEX IF NOT EXISTS uk_tzkioe_material_warehouse
    ON "qixiao-apaas".t_auto_hltgq_knc3g_tzkioe(jzikio, oywydb);

-- ========== 设备盘点（2026-10 新增，对应《设备盘点功能设计方案》3.x） ==========
-- 与出入库同底座：共用物资目录 slaifi / 台账 tzkioe / 编号表 order_no_seq / 流水 stock_change_log。
-- 保存即生效、不提供删除；编辑仅限备注/附件/盘点人（不触碰台账与流水）；编号 = PDD+yyyyMMdd+4 位序号（serial_type=PDD）。
-- 保存事务（方案 4.1）：校验 → 取号 → 主表/明细/附件 → 逐行账面一致性校验（不一致整单拒绝保存）→ 逐行绝对校正台账 → 仅差异行写盘盈/盘亏流水。

-- 4.1 盘点单主表
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_stocktake_order (
    id            VARCHAR(64)  PRIMARY KEY,
    order_no      VARCHAR(32)  NOT NULL,          -- 盘点单号：PDD+yyyyMMdd+4位序号（唯一）
    check_date    DATE         NOT NULL,          -- 盘点日期（默认当天，可改）
    warehouse_id  VARCHAR(64)  NOT NULL,          -- 盘点仓库 ID（twufyx.id）
    operator_id   VARCHAR(64)  NOT NULL,          -- 盘点人 ID（默认当前登录人，可改）
    operator_name VARCHAR(64),                    -- 盘点人姓名快照
    remark        VARCHAR(500),                   -- 备注（应用层限 50 字）
    del_flag      VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- #1# 已删除 / #2# 正常（本期不提供删除，恒 #2#）
    corp_code     VARCHAR(64),
    created_at    TIMESTAMP,
    created_by    VARCHAR(64),
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_stocktake_order_no
    ON "qixiao-apaas".t_auto_hltgq_water_stocktake_order(order_no);
CREATE INDEX IF NOT EXISTS idx_stocktake_order_date
    ON "qixiao-apaas".t_auto_hltgq_water_stocktake_order(check_date DESC);

-- 4.2 盘点单明细：每行 = 物资 × 盘点仓库；book_qty 为页面快照，保存时与台账实时值一致才允许保存
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail (
    id             VARCHAR(64)  PRIMARY KEY,
    order_id       VARCHAR(64)  NOT NULL,         -- 盘点单 ID（stocktake_order.id）
    material_id    VARCHAR(64)  NOT NULL,         -- 物资 ID（slaifi.id）
    material_code  VARCHAR(64)  NOT NULL,         -- 物资编号快照（slaifi.higsdd）
    material_name  VARCHAR(255) NOT NULL,         -- 物资名称快照（slaifi.avlsdv）
    material_brand VARCHAR(255),                  -- 品牌快照（slaifi.fqztqm，可空）
    material_spec  VARCHAR(255),                  -- 规格型号快照（slaifi.iwnyuk，可空）
    material_unit  VARCHAR(64),                   -- 计量单位快照（slaifi.boipik）
    book_qty       INTEGER      NOT NULL,         -- 账面库存快照（带出/保存时的台账值，一致性校验基准）
    check_qty      INTEGER      NOT NULL,         -- 实盘数量（≥0，应用层校验）
    profit_qty     INTEGER      NOT NULL,         -- 盘盈（check_qty - book_qty，>0 时）
    loss_qty       INTEGER      NOT NULL,         -- 盘亏（book_qty - check_qty，>0 时）
    corp_code      VARCHAR(64),
    created_at     TIMESTAMP,
    created_by     VARCHAR(64),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_stocktake_order_detail_order
    ON "qixiao-apaas".t_auto_hltgq_water_stocktake_order_detail(order_id);

-- 4.3 盘点单附件：上传时 order_id 为空，保存/编辑时回填；文件落盘 file-dir/stocktake/{yyyy-MM}/，移除时物理删文件+删行
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_stocktake_attachment (
    id         VARCHAR(64)  PRIMARY KEY,
    order_id   VARCHAR(64),                       -- 盘点单 ID（上传时为空，保存/编辑时回填）
    file_name  VARCHAR(255) NOT NULL,             -- 原始文件名（下载展示用）
    file_path  VARCHAR(500) NOT NULL,             -- 相对 file-dir 的存储路径（stocktake/yyyy-MM/uuid_原名）
    file_size  BIGINT,                            -- 文件大小（字节）
    file_ext   VARCHAR(16),                       -- 扩展名（小写，下载 Content-Type 判别）
    corp_code  VARCHAR(64),
    created_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_stocktake_attachment_order
    ON "qixiao-apaas".t_auto_hltgq_water_stocktake_attachment(order_id);

-- ========== 值班交接班：值班记录按人（2026-10 二次改造，替代首版「班维度签到」） ==========
-- 业务定义：值班记录 = 一个人的值班记录。每人签到各自生成一条 igahxz 记录（ahygpx = 本人，
-- 领导记录同时 atfzxl = 本人）；同一班排班 N 人签到 → N 条记录（报告各自独立互不覆盖）；
-- 一次交班 → 全班记录一并收口（同一实际交班时间 + 一条交接班记录）。
-- 变更：主表新增 ahygpx（值班人员，单关联用户；类型对齐平台单关联列 atfzxl/alidpq：varchar 无长度）；
-- 判定（我的在途记录）/签到幂等/报告归属/交班权限/收口范围均以 ahygpx 为准。
-- 历史数据：改造前记录 ahygpx 为 NULL（历史班记录，人员体现在 rel 表）；按人判定后历史在途记录参与者
-- 回到「待签到」各自签到生成自己的记录即可，无需回填；列表中历史记录的值班人员列自动回退显示 rel 聚合。
-- 独立执行版见根目录 duty-checkin-ddl.sql。
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz"
    ADD COLUMN IF NOT EXISTS ahygpx varchar;
-- 首版已废列（保留不删、新代码不再读写，兼容历史环境与旧版回滚；全新环境可不执行）：
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz"
    ADD COLUMN IF NOT EXISTS leader_checkin_at TIMESTAMP;
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz_ahygpx_rel"
    ADD COLUMN IF NOT EXISTS checkin_at TIMESTAMP;

-- ========== 灌区用水管理（2026-10 新增，对应《水资源调度系统 · 三页面设计方案》二、数据模型） ==========
-- 用水户管理 / 用水定额标准库 / 用水计划管理（主表+明细）四张表；独立执行版见根目录 water-usage-ddl.sql。
-- 字典字段统一 "#N#" 文本编码（后端常量维护、前端经接口获取不写死）；水量单位 m³（NUMERIC 2 位小数，
-- 展示层换算万m³ 1 位）；面积亩（2 位）；水价 元/m³（3 位）；编号（YH/YJ + 3 位序号）由应用层生成，
-- 唯一索引兜底并发（生成冲突重试）。与平台既有用水台账表 hjwwcb、水费预留体系 gfiidm/quaxiq 等无耦合。

-- 1.1 用水户：编号 YH+3位序号（YH001 起；系统自动生成默认值，可修改）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_user (
    id              VARCHAR(64)  PRIMARY KEY,
    user_no         VARCHAR(32)  NOT NULL,          -- 编号：YH + 3 位序号（全局唯一）
    name            VARCHAR(100) NOT NULL,          -- 名称（必填，全局唯一）
    user_type       VARCHAR(16)  NOT NULL,          -- 用水类型：#1# 农业 / #2# 工业 / #3# 生活 / #4# 生态
    contact_name    VARCHAR(50)  NOT NULL,          -- 联系人（必填）
    contact_phone   VARCHAR(20),                    -- 联系电话（可空；填写须 11 位数字，应用层校验）
    irrigation_area NUMERIC(12,2),                  -- 灌溉面积（亩；农业/生态必填，工业/生活可空）
    water_price     NUMERIC(10,3) NOT NULL,         -- 水价（元/m³，>0）
    calc_mode       VARCHAR(16)  NOT NULL,          -- 计算策略：#1# 指定闸口累加 / #2# 闸口差额计量
    gate_config     TEXT         NOT NULL,          -- 关联闸口（JSON，闸站 id + 名称快照）：
                                                    --   累加 {"mode":"SUM","gates":[{"id":"…","name":"…"},…]}
                                                    --   差额 {"mode":"DIFF","main":{…},"deduct":[{…},…]}
                                                    --   差额策略保存时校验：主闸口读数 − 扣减闸口读数合计 > 0
    settle_cycle    VARCHAR(16)  NOT NULL,          -- 统计周期：#1# 月度结算 / #2# 季度结算 / #3# 年度结算
    lng             NUMERIC(10,6),                  -- 位置经度（可选，-180~180）
    lat             NUMERIC(10,6),                  -- 位置纬度（可选，-90~90）
    annual_plan_water NUMERIC(16,2),                -- 年度计划水量（m³；可空=未设定；对外展示万m³）
    remark          VARCHAR(200),                   -- 备注
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 状态：#1# 正常 / #2# 停用
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_user_no
    ON "qixiao-apaas".t_auto_hltgq_water_user(user_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_user_name
    ON "qixiao-apaas".t_auto_hltgq_water_user(name);

-- 1.2 用水定额标准：作物种类 × 灌溉方式 → 定额标准（m³/亩）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_quota (
    id                VARCHAR(64)  PRIMARY KEY,
    crop_type         VARCHAR(100) NOT NULL,        -- 作物种类（水稻/冬小麦/…；亦含工业用水、城镇生活等行业类型）
    irrigation_method VARCHAR(16)  NOT NULL,        -- 灌溉方式（必填；字典型 #1#~#10#：淹灌/控制灌溉/畦灌/沟灌/喷灌/滴灌/膜下滴灌/微喷/泛水补水/计划直供）
    quota_value       NUMERIC(12,2) NOT NULL,       -- 定额标准（m³/亩，>0）
    remark            VARCHAR(200),                 -- 备注（如"泡田+生育期全周期定额"）
    corp_code         VARCHAR(64),
    created_at        TIMESTAMP,
    created_by        VARCHAR(64),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(64)
);

-- 1.3 用水计划主表：计划总水量为子表汇总（应用层维护）；编制中可删（主表+明细物理删除），其余状态终态保护
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_plan (
    id              VARCHAR(64)  PRIMARY KEY,
    plan_no         VARCHAR(32)  NOT NULL,          -- 编号：YJ + 3 位序号（YJ001 起，全局唯一）
    plan_name       VARCHAR(200) NOT NULL,          -- 计划名称（必填，全局唯一，不可自动生成；如"2026年度供水计划"）
    plan_type       VARCHAR(16)  NOT NULL,          -- 类型：#1# 年度 / #2# 月度 / #3# 旬度
    period_start    DATE         NOT NULL,          -- 计划周期起（按类型自动填充，不可修改）
    period_end      DATE         NOT NULL,          -- 计划周期止（年度=12-31；旬度按自然月末计算）
    total_water     NUMERIC(16,2) NOT NULL DEFAULT 0,  -- 计划总水量（m³，明细计划水量汇总，只读）
    scheduled_qty   NUMERIC(16,2) NOT NULL DEFAULT 0,  -- 已调度水量（m³，默认 0；调度模块后续回写）
    dispatch_ref_id VARCHAR(64),                    -- 调度方案关联引用（预留字段，本期不使用）
    remark          VARCHAR(200),                   -- 备注
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- #1# 编制中 / #2# 已生效 / #3# 已归档 / #4# 已作废
    void_by         VARCHAR(64),                    -- 作废人（作废时记录）
    void_at         TIMESTAMP,                      -- 作废时间
    void_reason     VARCHAR(200),                   -- 作废原因
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_plan_no
    ON "qixiao-apaas".t_auto_hltgq_water_plan(plan_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_plan_name
    ON "qixiao-apaas".t_auto_hltgq_water_plan(plan_name);
CREATE INDEX IF NOT EXISTS idx_water_plan_status
    ON "qixiao-apaas".t_auto_hltgq_water_plan(status);
CREATE INDEX IF NOT EXISTS idx_water_plan_type_period
    ON "qixiao-apaas".t_auto_hltgq_water_plan(plan_type, period_start, period_end);

-- 1.4 用水计划明细：行 = 用水户（同一用水户在同一计划中仅可出现一次，唯一索引 + 服务层校验双重保障）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_plan_item (
    id              VARCHAR(64)  PRIMARY KEY,
    plan_id         VARCHAR(64)  NOT NULL,          -- 计划主表 ID（water_plan.id）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（water_user.id）
    irrigation_area NUMERIC(12,2) NOT NULL,         -- 灌溉面积（亩，选用水户带出的快照，编制中可改，不回写用水户）
    quota_id        VARCHAR(64)  NOT NULL,          -- 适用定额 ID（water_quota.id）
    calc_water      NUMERIC(16,2) NOT NULL,         -- 测算水量（m³，= 面积 × 定额，只读自动计算）
    plan_water      NUMERIC(16,2) NOT NULL,         -- 计划水量（m³，核定填写，默认 = 测算水量）
    override_reason VARCHAR(200),                   -- 超测算说明（计划水量 > 测算水量时必填）
    stage_tag       VARCHAR(50),                    -- 阶段标签（春灌/夏灌等，可空）
    sort_order      INTEGER      NOT NULL DEFAULT 0, -- 明细行序（页面展示顺序）
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_plan_item_user
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(plan_id, water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_plan_item_plan
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(plan_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_water_plan_item_user
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_plan_item_quota
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(quota_id);

-- ========== 灌区用水 · 调度与水费四页面（2026-10 新增） ==========
-- 需水上报 / 调度方案 / 调度记录 / 水费账单四张表；独立执行版见根目录 water-dispatch-bill-ddl.sql。
-- 字典字段统一 "#N#" 文本编码（接口对外映射前端英文码 wait/pass/reject、pending/running/done/canceled、
-- up/down/adjust、generated/paid、month/quarter/year）；水量库内一律 m³（NUMERIC 2 位小数），
-- 四页面接口对外输出万m³（÷10000，4 位小数）；金额 = 计入水量(m³) × 水价(元/m³)，元（2 位）。
-- 计入水量口径（与流量监测/灌溉用水一致）：区间累计 = ttf(窗口内末行) − ttf(起点前最近一条 ttf 非空行)，
-- 起点前无基准按 0，哨兵值 -999/-9991 视为缺失；差额策略（calc_mode=#2#）扣减闸口方案取负值。
-- 编号由应用层 MAX+1 生成、唯一索引兜底并发：XS+4 位 / DD+4 位 / SF+期间码+'-'+户号+4 位；调度记录无编号。

-- 2.1 需水上报：编号 XS+4 位序号；审核态 待审核(#1#)→已通过(#2#)/已驳回(#3#)
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_demand (
    id              VARCHAR(64)  PRIMARY KEY,
    demand_no       VARCHAR(32)  NOT NULL,          -- 编号：XS + 4 位序号（全局唯一）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（water_user.id）
    gate_id         VARCHAR(64),                    -- 申报拟用闸口（可空；编制方案时预填，须属于该户闸口组）
    amount          NUMERIC(16,2) NOT NULL,         -- 申报计划水量（m³，>0；对外展示万m³）
    start_date      DATE         NOT NULL,          -- 用水时段起
    end_date        DATE         NOT NULL,          -- 用水时段止（>= start_date）
    usage_desc      VARCHAR(200),                   -- 用途（如 冬灌前补水）
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 审核状态：#1# 待审核 / #2# 已通过 / #3# 已驳回
    audit_opinion   VARCHAR(200),                   -- 审核意见（驳回必填）
    audit_by        VARCHAR(64),                    -- 审核人
    audit_at        TIMESTAMP,                      -- 审核时间
    remark          VARCHAR(200),                   -- 备注
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_demand_no
    ON "qixiao-apaas".t_auto_hltgq_water_demand(demand_no);
CREATE INDEX IF NOT EXISTS idx_water_demand_user
    ON "qixiao-apaas".t_auto_hltgq_water_demand(water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_demand_status
    ON "qixiao-apaas".t_auto_hltgq_water_demand(status);

-- 2.2 调度方案：编号 DD+4 位序号；执行态 待执行(#1#)→执行中(#2#)→已完成(#3#)；待执行/执行中→已取消(#4#)
--     行 = 闸口 × 时段的放水载体：计入水量 = 该闸口在 [start_time, end_time] 的实测累计流量（带符号）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_dispatch (
    id              VARCHAR(64)  PRIMARY KEY,
    dispatch_no     VARCHAR(32)  NOT NULL,          -- 编号：DD + 4 位序号（全局唯一）
    plan_id         VARCHAR(64),                    -- 关联用水计划（water_plan.id，可空；关联后受该计划该户配额约束）
    demand_id       VARCHAR(64),                    -- 关联需水申请（water_demand.id，可空；须为已通过且同户）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID
    gate_id         VARCHAR(64)  NOT NULL,          -- 调度闸口（须属于该户闸口组；计量取数锚点）
    plan_amount     NUMERIC(16,2) NOT NULL,         -- 计划放水量（m³，>0；对外展示万m³）
    start_time      TIMESTAMP    NOT NULL,          -- 执行时段起（计入水量取数窗口起点）
    end_time        TIMESTAMP    NOT NULL,          -- 执行时段止（>= start_time）
    owner           VARCHAR(50),                    -- 调度负责人
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 执行状态：#1# 待执行 / #2# 执行中 / #3# 已完成 / #4# 已取消
    bill_id         VARCHAR(64),                    -- 计费账单 ID（water_bill.id；非空=已计费，禁改禁删）
    billed_qty      NUMERIC(16,2),                  -- 出账时"计入水量"快照（m³，带符号；账单删除时清空）
    remark          VARCHAR(200),                   -- 备注
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_dispatch_no
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(dispatch_no);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_user
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_plan
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(plan_id);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_bill
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(bill_id);

-- 2.3 调度记录：闸口操作留痕（不参与水量与费用计算）；无编号
--     方案删除时 dispatch_id 置空（记录保留，历史留痕不随方案消失）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_dispatch_record (
    id              VARCHAR(64)  PRIMARY KEY,
    dispatch_id     VARCHAR(64),                    -- 关联调度方案（water_dispatch.id，可空）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（关联方案时须与方案一致）
    gate_id         VARCHAR(64)  NOT NULL,          -- 操作闸口（关联方案时须与方案一致）
    op_time         TIMESTAMP    NOT NULL,          -- 操作时间
    op_type         VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 操作类型：#1# 闸门提升 / #2# 闸门下降 / #3# 流量调整
    height          INTEGER,                        -- 升降高度（cm，0~999；可空）
    operator        VARCHAR(50)  NOT NULL,          -- 操作人 / 闸管员
    remark          VARCHAR(200),                   -- 备注（现场情况、指令依据等）
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_record_time
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch_record(op_time);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_record_gate
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch_record(gate_id);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_record_dispatch
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch_record(dispatch_id);

-- 2.4 水费账单：编号 SF+期间码+'-'+户号+4 位序号；状态 已生成(#1#)→已缴清(#2#)
--     账期按用水户统计周期分期：月（2026-10）/ 季（2026-Q3）/ 年（2026）；一份账单可含多方案（bill_id 反向挂接）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_bill (
    id              VARCHAR(64)  PRIMARY KEY,
    bill_no         VARCHAR(64)  NOT NULL,          -- 编号：SF + 期间码 + '-' + 户号 + 4 位序号（全局唯一）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（台账维度）
    settle_cycle    VARCHAR(16)  NOT NULL,          -- 统计周期快照：#1# 月度 / #2# 季度 / #3# 年度
    cycle_key       VARCHAR(16)  NOT NULL,          -- 分期 key：月 2026-10 / 季 2026-Q3 / 年 2026
    period_start    DATE         NOT NULL,          -- 账期区间起（cycle_key 推导）
    period_end      DATE         NOT NULL,          -- 账期区间止
    quantity        NUMERIC(16,2) NOT NULL,         -- 计费水量（m³，Σ方案计入水量快照，带符号；>0 才允许出账）
    price           NUMERIC(10,3) NOT NULL,         -- 水价快照（元/m³，出账时刻用水户配置）
    amount          NUMERIC(16,2) NOT NULL,         -- 应收金额（元 = quantity × price，2 位小数）
    paid            NUMERIC(16,2) NOT NULL DEFAULT 0,  -- 已收金额（元；确认收款时 = amount）
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 账单状态：#1# 已生成 / #2# 已缴清
    pay_time        TIMESTAMP,                      -- 收款时间
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_bill_no
    ON "qixiao-apaas".t_auto_hltgq_water_bill(bill_no);
CREATE INDEX IF NOT EXISTS idx_water_bill_user
    ON "qixiao-apaas".t_auto_hltgq_water_bill(water_user_id, cycle_key);

-- ========== 节水管理（2026-10 新增，对应《节水潜力开发实施方案》V1.4 §3 数据模型） ==========
-- 年度水量记录 / 节水方案 / 节水指标三张表；独立执行版见根目录 water-saving-ddl.sql。
-- 平台低代码台账（bqinjk 节水方案 / iiitnh 节水体系 为平台表单存储、字段不满足）保持不动，不迁移不读写；
-- water_saving_potential（潜力测算页专用）不复用。水量一律万m³（NUMERIC(18,6)；用水计划 total_water 为 m³，
-- 接口层 ÷10000 换算）；字典字段 "#N#" 文本编码；布尔语义用 "#1#/#2#" 文本（项目惯例）。
-- 核验作废（方案A）：年度记录水量被改 / 编辑换依据 / 刷新对比依据 → status 置 #4# 待复核、verify_* 保留、
-- verify_invalid=#1#、达标强制"待核验"；达标优先级链：待核验 > 待设置目标 > 待关联指标 > 无法计算 > 不达标 > 部分达标 > 达标。

-- 5.1 年度水量记录：行 = 一个潜力年度（potential_year 唯一）；潜力 = 实际 − 计划（可为负，不截断）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_saving_annual (
    id                 VARCHAR(64)  PRIMARY KEY,
    potential_year     INTEGER      NOT NULL,       -- 潜力年度（如 2026；全表唯一，被方案引用后禁止修改）
    basis_year         INTEGER      NOT NULL,       -- 依据年度（= potential_year − 1，自动带出只读）
    prev_plan_volume   NUMERIC(18,6),               -- 上一年计划放水量（万m³；自动带出或手工覆盖，非负）
    prev_actual_volume NUMERIC(18,6),               -- 上一年实际放水量（万m³；本期手工录入，非负）
    plan_source        VARCHAR(20),                 -- 计划来源：auto_plan 用水计划带出 / manual 手工录入
    plan_id            VARCHAR(64),                 -- 来源计划 id（plan_source=auto_plan 时留痕；water_plan.id）
    actual_source      VARCHAR(30),                 -- 实际来源：manual 手工（canal_head_meter 渠首自动=本期预留）
    is_demo            VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- 演示数据标记：#1# 虚拟演示 / #2# 正式数据（导出默认排除 #1#）
    corp_code          VARCHAR(64),
    created_at         TIMESTAMP,
    created_by         VARCHAR(64),
    updated_at         TIMESTAMP,
    updated_by         VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_saving_annual_year
    ON "qixiao-apaas".t_auto_hltgq_water_saving_annual(potential_year);

-- 5.2 节水方案：baseline 建方案时快照固化（不随年度记录改动更新）；核验数据保留 + verify_invalid 作废标记（方案A）
--     状态机：#1# 草稿 / #2# 实施中 —（核验 comparable=#1#）→ #3# 已完成；—（comparable=#2#）→ #4# 待复核
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_saving_scheme (
    id                  VARCHAR(64)  PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,      -- 方案名称（同一年度内唯一）
    "year"              INTEGER      NOT NULL,      -- 实施年度（= 关联记录的 potential_year；保留字，SQL 全程加引号）
    evidence_id         VARCHAR(64)  NOT NULL,      -- 关联年度水量记录（saving_annual.id；被引用记录禁止删除）
    baseline_volume     NUMERIC(18,6),              -- 实施前实际用水量快照（建方案时 = 记录.prev_actual_volume；仅"刷新对比依据"才更新）
    target_volume       NUMERIC(18,6) NOT NULL,     -- 节水目标（万m³；0 < 目标 ≤ 潜力）
    owner               VARCHAR(100) NOT NULL,      -- 负责人（如"工程管理处 / 张工"）
    measure             TEXT         NOT NULL,      -- 具体措施
    status              VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- #1# 草稿 / #2# 实施中 / #3# 已完成 / #4# 待复核
    indicator_ids       TEXT         NOT NULL DEFAULT '[]',   -- 关联评估指标 id 数组（JSON；核验时按公式自动计算）
    verify_after_volume NUMERIC(18,6),              -- 核验：实施后实际用水量（万m³；核验动作写入）
    verify_source       VARCHAR(200),               -- 核验资料来源（核验必填，如台账/报表编号）
    verify_note         TEXT,                       -- 核验说明（选填）
    verify_date         DATE,                       -- 核验日期（核验动作当天）
    verify_comparable   VARCHAR(8),                 -- 对比条件确认：#1# 已确认（计算指标）/ #2# 未确认（待复核、不计算）；NULL=未核验
    verify_invalid      VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- 核验作废标记（方案A）：#1# 已作废（数据保留、达标强制待核验）/ #2# 有效
    last_recalc_at      TIMESTAMP,                  -- 达标重算/核验作废最近时间（留痕）
    last_recalc_reason  VARCHAR(200),               -- 重算/作废原因（如"核验依据已变更"）
    is_demo             VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- 演示数据标记：#1# 虚拟演示 / #2# 正式数据
    attachment_name     VARCHAR(255),               -- 附件原始文件名（下载展示）
    attachment_url      VARCHAR(500),               -- 附件存储路径（file-dir 相对路径：saving/{yyyy-MM}/{uuid}_{原名}）
    corp_code           VARCHAR(64),
    created_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_saving_scheme_year_name
    ON "qixiao-apaas".t_auto_hltgq_water_saving_scheme("year", name);
CREATE INDEX IF NOT EXISTS idx_water_saving_scheme_evidence
    ON "qixiao-apaas".t_auto_hltgq_water_saving_scheme(evidence_id);
CREATE INDEX IF NOT EXISTS idx_water_saving_scheme_status
    ON "qixiao-apaas".t_auto_hltgq_water_saving_scheme(status);

-- 5.3 节水指标：计算方式固定三种（下拉选择后单位/公式自动确定，不可自由编辑）；名称全局唯一（含停用）
--     节水量(#1#)：万m³ = 实施前 − 实施后；节水率(#2#)：% = 节水量 ÷ 实施前 × 100；目标完成率(#3#)：% = 节水量 ÷ 方案目标 × 100
--     分母为 0 → 结果 null（展示"无法计算"，不参与判定）；已关联方案的指标计算方式锁定、不可删除
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_saving_indicator (
    id           VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,             -- 指标名称（全局唯一，含停用指标）
    method       VARCHAR(16)  NOT NULL,             -- 计算方式：#1# 节水量(万m³) / #2# 节水率(%) / #3# 目标完成率(%)
    target_value NUMERIC(18,6),                     -- 目标值（可空；未设目标 → 方案"待设置目标"、不出达标结论）
    purpose      TEXT,                              -- 指标用途（选填）
    enabled      VARCHAR(8)   NOT NULL DEFAULT '#1#',  -- 状态：#1# 启用 / #2# 停用（停用后新方案不可勾选，既有关联保留）
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_saving_indicator_name
    ON "qixiao-apaas".t_auto_hltgq_water_saving_indicator(name);