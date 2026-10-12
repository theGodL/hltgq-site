-- ============================================================
-- 设备盘点功能建表脚本（独立执行版）
-- 对应《设备盘点功能设计方案》4.1~4.3；累积初始化脚本见 src/main/resources/init.sql
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- ============================================================
BEGIN;

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

COMMIT;

-- ============================================================
-- 执行后自查（应返回 3 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name IN ('t_auto_hltgq_water_stocktake_order',
--                       't_auto_hltgq_water_stocktake_order_detail',
--                       't_auto_hltgq_water_stocktake_attachment');
-- ============================================================
