package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.BaseRecordEntity;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型方案主表通用操作：状态查询、重命名、逻辑删除、历史方案列表。
 * <p>6 个模块的主表结构同构（status/del_flag/error_msg/scheme_name），统一在此处理。
 */
@Service
public class ModelRecordCommonService {

    /** 执行状态（纯值口径）：6 张预测主表平台未配字典、防洪抗旱任务由前端轮询判断，均为纯值。
     * 仅 moisture_record 平台配单选字典，用 # 编码常量（见 MoisturePredictService）。 */
    public static final String STATUS_CALCULATING = "calculating";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    /** 按主键查询，不存在抛 404 */
    public <T extends BaseRecordEntity> T require(String id, BaseMapper<T> mapper) {
        T record = mapper.selectById(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "方案不存在");
        }
        return record;
    }

    /** 轮询状态：返回 {id, status, errorMsg} */
    public <T extends BaseRecordEntity> Map<String, Object> status(String id, BaseMapper<T> mapper) {
        T record = require(id, mapper);
        Map<String, Object> result = new HashMap<>();
        result.put("id", record.getId());
        result.put("status", record.getStatus());
        result.put("errorMsg", record.getErrorMsg());
        return result;
    }

    /** 重命名方案 */
    public <T extends BaseRecordEntity> void rename(String id, String name, BaseMapper<T> mapper) {
        T record = require(id, mapper);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("方案名称不能为空");
        }
        record.setSchemeName(name.trim());
        record.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(record);
    }

    /** 逻辑删除：del_flag 置 #1# */
    public <T extends BaseRecordEntity> void softDelete(String id, BaseMapper<T> mapper) {
        T record = require(id, mapper);
        record.setDelFlag(BoolTextUtils.TRUE);
        record.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(record);
    }

    /** 历史方案列表：del_flag=#2#，按 created_at 倒序（全列查询，含 schemeName/status/createdAt 等前端展示字段） */
    public <T extends BaseRecordEntity> List<T> list(BaseMapper<T> mapper) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("\"del_flag\"", BoolTextUtils.FALSE)
                .orderByDesc("\"created_at\"");
        return mapper.selectList(wrapper);
    }
}
