package com.qgyun.hltgq.hltgqsite.model.util;

import com.qgyun.hltgq.hltgqsite.entity.LevelStorage;
import com.qgyun.hltgq.hltgqsite.mapper.LevelStorageMapper;
import org.springframework.stereotype.Service;

/**
 * 库容曲线查询服务（t_auto_hltgq_water_level_storage）。
 * <p>根据水位精确匹配库容；无精确匹配时用相邻水位线性插值；
 * 单侧越界取最近值；无任何曲线数据返回 null。
 */
@Service
public class StorageCurveService {

    private final LevelStorageMapper levelStorageMapper;

    public StorageCurveService(LevelStorageMapper levelStorageMapper) {
        this.levelStorageMapper = levelStorageMapper;
    }

    /**
     * 水位（m）→ 库容（万方）。
     *
     * @param waterLevel 水位，null 返回 null
     */
    public Double getStorageByLevel(Double waterLevel) {
        if (waterLevel == null) {
            return null;
        }
        // 1. 精确匹配
        Double exact = levelStorageMapper.selectStorageByLevel(waterLevel);
        if (exact != null) {
            return exact;
        }
        // 2. 相邻水位线性插值
        LevelStorage lower = levelStorageMapper.selectNearestLower(waterLevel);
        LevelStorage upper = levelStorageMapper.selectNearestUpper(waterLevel);
        if (lower != null && upper != null && upper.getWaterLevel() != null
                && lower.getWaterLevel() != null && upper.getWaterLevel() > lower.getWaterLevel()) {
            double ratio = (waterLevel - lower.getWaterLevel())
                    / (upper.getWaterLevel() - lower.getWaterLevel());
            return lower.getStorage() + (upper.getStorage() - lower.getStorage()) * ratio;
        }
        // 3. 单侧越界取最近值
        if (lower != null) {
            return lower.getStorage();
        }
        if (upper != null) {
            return upper.getStorage();
        }
        return null;
    }
}
