package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDispatchMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDispatchRecordMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DispatchOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度记录服务（列表/详情/新增/编辑/删除）。
 * <p>闸口操作留痕，不参与水量与费用计算；记录为留痕数据宽进管理（编辑/删除不限状态），
 * 仅做存在性与关联一致性校验：关联方案（dispatchId）填写时用水户与操作闸口须与方案一致。
 * <p>操作类型对外英文码 up/down/adjust（空按 up）；升降高度 0~999 cm 选填。
 */
@Service
public class WaterDispatchRecordService {

    private final WaterDispatchRecordMapper recordMapper;

    private final WaterDispatchMapper dispatchMapper;

    private final WaterUserMapper userMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final PlatformUserService platformUsers;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterDispatchRecordService(WaterDispatchRecordMapper recordMapper,
                                      WaterDispatchMapper dispatchMapper,
                                      WaterUserMapper userMapper,
                                      ShortIdGenerator shortIdGenerator,
                                      PlatformUserService platformUsers) {
        this.recordMapper = recordMapper;
        this.dispatchMapper = dispatchMapper;
        this.userMapper = userMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.platformUsers = platformUsers;
    }

    // ==================== 元数据 / 列表 / 详情 ====================

    /** 表单元数据（操作类型字典 + 操作人候选（平台用户）+ 当天日期） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("opTypes", WaterUsageDict.recordTypeOptions());
        out.put("operators", platformUsers.options());
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 列表分页（关键字匹配操作人/备注/方案编号） */
    public Map<String, Object> page(int page, int size, String keyword, String waterUserId, String gateId) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String uid = WaterUsageSupport.trim(waterUserId);
        String gid = WaterUsageSupport.trim(gateId);
        long total = recordMapper.countRecords(kw, uid, gid);
        List<WaterDispatchRecordVO> records = recordMapper.selectRecordPage(kw, uid, gid, s, (long) (p - 1) * s);
        for (WaterDispatchRecordVO row : records) {
            decorate(row);
        }
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情 */
    public WaterDispatchRecordVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "记录 id 不能为空");
        WaterDispatchRecordVO vo = recordMapper.selectRecordById(key);
        if (vo == null) {
            throw new IllegalArgumentException("调度记录不存在");
        }
        decorate(vo);
        return vo;
    }

    /** 「关联调度方案」下拉（未取消全部，编号倒序） */
    public List<DispatchOptionVO> planOptions(String keyword) {
        List<DispatchOptionVO> rows = dispatchMapper.selectActivePlanOptions(WaterUsageSupport.trim(keyword));
        for (DispatchOptionVO row : rows) {
            row.setStatus(WaterUsageDict.dispatchStatusCodeOf(row.getStatus()));
        }
        return rows;
    }

    // ==================== 新增 / 编辑 / 删除 ====================

    /** 新增（关联方案选填；填写时用水户/闸口须与方案一致） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchRecordVO create(WaterDispatchRecordSaveVO req) {
        Draft d = validate(req);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("dispatch_id", d.dispatchId);
        row.put("water_user_id", d.userId);
        row.put("gate_id", d.gateId);
        row.put("op_time", d.opTime);
        row.put("op_type", d.opType);
        row.put("height", d.height);
        row.put("operator", d.operator);
        row.put("remark", d.remark);
        row.put("now", now);
        row.put("auditor", operator);
        recordMapper.insertRecord(row);
        return detail(id);
    }

    /** 编辑（留痕数据宽进：全字段可改，关联一致性同新增） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchRecordVO update(String id, WaterDispatchRecordSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "记录 id 不能为空");
        WaterDispatchRecordVO current = recordMapper.selectRecordById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度记录不存在");
        }
        Draft d = validate(req);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key);
        row.put("dispatch_id", d.dispatchId);
        row.put("water_user_id", d.userId);
        row.put("gate_id", d.gateId);
        row.put("op_time", d.opTime);
        row.put("op_type", d.opType);
        row.put("height", d.height);
        row.put("operator", d.operator);
        row.put("remark", d.remark);
        row.put("now", now);
        row.put("auditor", operator);
        if (recordMapper.updateRecord(row) == 0) {
            throw new IllegalArgumentException("调度记录不存在，请刷新后重试");
        }
        return detail(key);
    }

    /** 删除（留痕数据宽进：不限状态） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "记录 id 不能为空");
        WaterDispatchRecordVO current = recordMapper.selectRecordById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度记录不存在");
        }
        recordMapper.deleteRecord(key);
    }

    // ==================== 内部 ====================

    /** 校验产物：已归一化的入库值（LocalDateTime / #N# 编码 / trim 文本） */
    private static final class Draft {
        String dispatchId;
        String userId;
        String gateId;
        LocalDateTime opTime;
        String opType;
        Integer height;
        String operator;
        String remark;
    }

    /** 新增/编辑共用校验：户/闸口存在、方案关联一致性、时间/类型/高度/文本 */
    private Draft validate(WaterDispatchRecordSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Draft d = new Draft();
        d.userId = WaterUsageSupport.requireText(req.getWaterUserId(), "请选择用水户");
        WaterUserVO user = userMapper.selectUserById(d.userId);
        if (user == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        d.gateId = WaterUsageSupport.requireText(req.getGateId(), "请选择操作闸口");
        if (userMapper.selectGateNamesByIds(Collections.singletonList(d.gateId)).isEmpty()) {
            throw new IllegalArgumentException("操作闸口不存在或已停用");
        }
        d.dispatchId = WaterUsageSupport.trim(req.getDispatchId());
        if (d.dispatchId != null) {
            WaterDispatchVO dispatch = dispatchMapper.selectDispatchById(d.dispatchId);
            if (dispatch == null) {
                throw new IllegalArgumentException("关联的调度方案不存在");
            }
            if (!d.userId.equals(dispatch.getWaterUserId())) {
                throw new IllegalArgumentException("关联方案的用水户与所选用水户不一致");
            }
            if (!d.gateId.equals(dispatch.getGateId())) {
                throw new IllegalArgumentException("关联方案的操作闸口与所选闸口不一致");
            }
        }
        d.opTime = WaterUsageSupport.parseDateTime(req.getOpTime(), "操作时间");
        String opUnit = WaterUsageSupport.trim(req.getOpType());
        d.opType = opUnit == null ? WaterUsageDict.RECORD_TYPE_UP : WaterUsageDict.recordTypeOfCode(opUnit);
        if (d.opType == null) {
            throw new IllegalArgumentException("操作类型参数不正确");
        }
        Integer height = req.getHeight();
        if (height != null && (height < 0 || height > 999)) {
            throw new IllegalArgumentException("升降高度需在 0~999 cm 之间");
        }
        d.height = height;
        // 操作人必填：传 operatorUserId 时按平台用户回查固化姓名（不信任前端文本）；
        // 仅回传姓名（未换人/存量手填的闸管员）时按文本入库，不阻塞留痕
        d.operator = platformUsers.resolveName(
                req.getOperatorUserId(), req.getOperator(), "操作人 / 闸管员", true);
        d.remark = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getRemark()), 200, "备注");
        return d;
    }

    /** 出口装饰：操作类型英文码 + 中文名 */
    private void decorate(WaterDispatchRecordVO vo) {
        if (vo == null) {
            return;
        }
        String raw = vo.getOpType();
        vo.setOpTypeName(WaterUsageDict.recordTypeName(raw));
        vo.setOpType(WaterUsageDict.recordTypeCodeOf(raw));
    }
}
