package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库容曲线表（t_auto_hltgq_water_level_storage）：水位-库容映射。
 * <p>短期来水逐日明细的 storage 字段依赖此表（精确匹配或线性插值）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_level_storage\"")
public class LevelStorage extends BaseWaterEntity {

    /** 水位(m)，唯一键 */
    @TableField("\"water_level\"")
    private Double waterLevel;

    /** 库容(万方) */
    @TableField("\"storage\"")
    private Double storage;
}
