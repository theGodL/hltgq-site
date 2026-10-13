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

-- 消息接收规则表：message_type × 接收维度（部门/岗位/角色/人员编码，多个逗号分隔）
CREATE TABLE IF NOT EXISTS t_auto_hltgq_water_message_rule (
    id           VARCHAR(64) PRIMARY KEY,
    message_type VARCHAR(32) NOT NULL,     -- 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集
    target_type  VARCHAR(16) NOT NULL,     -- 接收维度：#org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员
    target_id    VARCHAR(1024) NOT NULL,   -- 目标编码，多个以英文逗号分隔
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64),
    CONSTRAINT uk_message_rule UNIQUE (message_type, target_type, target_id)
);

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

-- ========== 值班交接班：每人独立签到（2026-10 改造） ==========
-- 原实现为班维度一次性签到（首人签到即全员「值班中」）；本改造签到按人落库、状态按人判定：
-- 未签到 → 待签到（可去签到），已签到 → 值班中。带班领导签到落主表（领导不在人员关系表中），
-- 值班人员签到落各自 rel 行；null=未签到，按人幂等（首签建记录、其余补签）。
-- 独立执行版见根目录 duty-checkin-ddl.sql；历史数据说明：改造前在途 #2# 记录签到列为 NULL，
-- 参与者各自补签一次即可，已交班（#3#）记录不受影响。
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz"
    ADD COLUMN IF NOT EXISTS leader_checkin_at TIMESTAMP;
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz_ahygpx_rel"
    ADD COLUMN IF NOT EXISTS checkin_at TIMESTAMP;