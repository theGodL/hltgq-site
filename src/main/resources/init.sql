CREATE INDEX IF NOT EXISTS idx_gate_site_tm
    ON "qixiao-apaas"."t_auto_hltgq_water_gate" (site, gate_no, tm DESC);
CREATE INDEX IF NOT EXISTS idx_wt_site_tm
    ON "qixiao-apaas"."t_auto_hltgq_water_wt_nfo" (site, tm DESC);
CREATE INDEX IF NOT EXISTS idx_rain_stcd_tm
    ON "qixiao-apaas".t_auto_hltgq_water_rain_info (STCD, TM DESC);

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