package com.qgyun.hltgq.hltgqsite.h5.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.h5.entity.MessageRule;
import com.qgyun.hltgq.hltgqsite.h5.mapper.MessageRuleMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleMetaVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleSaveVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * H5 消息发送设置（/message-rule）管理服务：页面元数据 / 规则分页 / 多选候选 / 规则 CRUD。
 * <p>规则语义（与 MessageService 同步判定一致）：
 * 前三类（告警/投诉/意见）命中规则即可见（该类无规则=全员可见）；
 * 值班/模型为附加接收人（该类无规则不影响原有定向：当班人员、方案提交人始终收到）；
 * 站点范围（site_id）仅告警生效：空=不限站点，命中多条规则合并范围、任一为空即不限。
 * <p>列表行的类型/维度中文名与目标/站点名称由本服务批量解析填充（页面不写死字典、少发 N+1 查询）。
 * <p>编辑锁定消息类型与接收维度（换类型/换维度 = 删除后重新新增），与阈值设置页同一约定。
 */
@Service
public class MessageRuleService {

    private static final Logger log = LoggerFactory.getLogger(MessageRuleService.class);

    /** 消息类型字典（编码 → 名称，顺序即页面 Tab 顺序） */
    private static final String[][] MESSAGE_TYPE_LABELS = {
            {"#1#", "告警"}, {"#2#", "举报投诉"}, {"#3#", "意见征集"}, {"#4#", "值班提醒"}, {"#5#", "模型计算"}
    };

    /** 接收维度字典（编码 → 名称） */
    private static final String[][] TARGET_TYPE_LABELS = {
            {"#org#", "部门"}, {"#position#", "岗位"}, {"#role#", "角色"}, {"#user#", "人员"}
    };

    /** 候选维度（options 入参合法值） */
    private static final List<String> OPTION_KINDS = Arrays.asList("org", "position", "role", "user", "site");

    /** 告警类型编码（唯一支持站点范围的类型） */
    private static final String TYPE_ALERT = "#1#";

    /** 附加接收人语义的消息类型（值班/模型：无规则不影响原有定向） */
    private static final List<String> EXTRA_RECEIVER_TYPES = Arrays.asList("#4#", "#5#");

    /** 各消息类型语义说明（页面提示文案） */
    private static final Map<String, String> TYPE_DESCS;

    static {
        Map<String, String> descs = new LinkedHashMap<>();
        descs.put("#1#", "命中规则的部门/岗位/角色/人员可见该类消息，可限定站点范围（未选择站点表示全部站点）；该类暂无规则时全员可见。");
        descs.put("#2#", "命中规则的部门/岗位/角色/人员可见该类消息；该类暂无规则时全员可见。");
        descs.put("#3#", "命中规则的部门/岗位/角色/人员可见该类消息；该类暂无规则时全员可见。");
        descs.put("#4#", "当班领导与值班人员默认收到；此处配置的对象为附加接收人，同步近两日处于值班中的排班提醒。");
        descs.put("#5#", "方案提交人默认收到；此处配置的对象为附加接收人，同步近 7 天完成的方案通知。");
        TYPE_DESCS = Collections.unmodifiableMap(descs);
    }

    private static final int PAGE_SIZE_DEFAULT = 20;

    private static final int PAGE_SIZE_MAX = 200;

    private final MessageRuleMapper ruleMapper;

    /** 企业编码兜底（会话缺失时使用，列 NOT NULL） */
    @Value("${hltgq.corp-code}")
    private String corpCode;

    /** 操作人兜底（会话缺失时使用，列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public MessageRuleService(MessageRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    // ==================== 元数据 ====================

    /** 页面元数据：消息类型（含站点支持/附加接收人语义与说明）与接收维度字典 */
    public MessageRuleMetaVO meta() {
        MessageRuleMetaVO vo = new MessageRuleMetaVO();
        List<MessageRuleMetaVO.MessageTypeMeta> types = new ArrayList<>();
        for (String[] pair : MESSAGE_TYPE_LABELS) {
            MessageRuleMetaVO.MessageTypeMeta item = new MessageRuleMetaVO.MessageTypeMeta();
            item.setValue(pair[0]);
            item.setLabel(pair[1]);
            item.setSiteSupported(TYPE_ALERT.equals(pair[0]));
            item.setExtraReceiver(EXTRA_RECEIVER_TYPES.contains(pair[0]));
            item.setDesc(TYPE_DESCS.get(pair[0]));
            types.add(item);
        }
        vo.setMessageTypes(types);
        List<MessageRuleMetaVO.TargetTypeMeta> targets = new ArrayList<>();
        for (String[] pair : TARGET_TYPE_LABELS) {
            MessageRuleMetaVO.TargetTypeMeta item = new MessageRuleMetaVO.TargetTypeMeta();
            item.setValue(pair[0]);
            item.setLabel(pair[1]);
            targets.add(item);
        }
        vo.setTargetTypes(targets);
        return vo;
    }

    // ==================== 分页 ====================

    /** 规则分页：messageType 可选过滤（传了必须合法）；行内名称批量解析填充 */
    public Map<String, Object> page(int page, int size, String messageType) {
        Page<MessageRuleVO> pager = new Page<>(page < 1 ? 1 : page, normalizeSize(size));
        IPage<MessageRuleVO> result = ruleMapper.selectRulePage(pager, optionalMessageType(messageType));
        decorateAll(result.getRecords());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", result.getTotal());
        out.put("page", result.getCurrent());
        out.put("size", result.getSize());
        out.put("pages", result.getPages());
        out.put("records", result.getRecords());
        return out;
    }

    // ==================== 候选 ====================

    /** 多选候选：kind = org/position/role/user/site，全量返回（数据量小：部门 32 / 岗位 47 / 角色 3 / 用户 150 / 站点 205） */
    public List<PickerOptionVO> options(String kind) {
        String code = trim(kind);
        if (code == null || !OPTION_KINDS.contains(code)) {
            throw new IllegalArgumentException("kind 仅支持 org / position / role / user / site");
        }
        switch (code) {
            case "org":
                return ruleMapper.selectOrgOptions(null);
            case "position":
                return ruleMapper.selectPositionOptions(null);
            case "role":
                return ruleMapper.selectRoleOptions(null);
            case "user":
                return ruleMapper.selectUserOptions(null);
            default:
                return ruleMapper.selectSiteOptions(null);
        }
    }

    // ==================== 新增 / 编辑 / 删除 ====================

    /** 新增规则：校验类型/维度/目标/站点，归一化（去重保序）后落库，返回完整行视图 */
    public MessageRuleVO create(MessageRuleSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String messageType = requireMessageType(req.getMessageType());
        String targetType = requireTargetType(req.getTargetType());
        String targetId = String.join(",", requireTargets(targetType, req.getTargetId()));
        String siteId = normalizeSites(messageType, req.getSiteId());

        if (ruleMapper.countSameRule(messageType, targetType, targetId, null) > 0) {
            throw new IllegalArgumentException("该类型该维度下已存在完全相同的规则，请直接编辑");
        }

        MessageRule entity = new MessageRule();
        entity.setMessageType(messageType);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setSiteId(siteId);
        applyCreateAudit(entity);
        ruleMapper.insert(entity);
        log.info("[消息发送设置] 新增规则：id={}，类型={}，维度={}，目标数={}，站点范围={}，操作人={}",
                entity.getId(), messageType, targetType, splitItems(targetId).size(),
                siteId == null ? "不限站点" : siteId, entity.getUpdatedBy());
        return detail(entity.getId());
    }

    /** 编辑规则：消息类型与维度锁定，仅允许改目标与站点范围，查重排除自身 */
    public MessageRuleVO update(String id, MessageRuleSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        MessageRule row = requireRow(id);
        // 锁定的类型/维度同样走合法性校验（防存量脏行被就地保存放大）
        String messageType = requireMessageType(row.getMessageType());
        String targetType = requireTargetType(row.getTargetType());
        String targetId = String.join(",", requireTargets(targetType, req.getTargetId()));
        String siteId = normalizeSites(messageType, req.getSiteId());

        if (ruleMapper.countSameRule(messageType, targetType, targetId, row.getId()) > 0) {
            throw new IllegalArgumentException("该类型该维度下已存在完全相同的规则");
        }

        String operator = currentOperatorId();
        UpdateWrapper<MessageRule> wrapper = new UpdateWrapper<>();
        wrapper.eq("\"id\"", row.getId());
        wrapper.set("\"target_id\"", targetId);
        // 站点范围允许清空（非告警恒为空），显式 set null（MyBatis-Plus 默认忽略 null 字段）
        wrapper.set("\"site_id\"", siteId);
        wrapper.set("\"updated_at\"", LocalDateTime.now());
        wrapper.set("\"updated_by\"", operator);
        ruleMapper.update(null, wrapper);
        log.info("[消息发送设置] 编辑规则：id={}，类型={}，维度={}，目标数={}，站点范围={}，操作人={}",
                row.getId(), messageType, targetType, splitItems(targetId).size(),
                siteId == null ? "不限站点" : siteId, operator);
        return detail(row.getId());
    }

    /** 删除规则（物理删除；接收记录与规则解耦，不受影响） */
    public void delete(String id) {
        MessageRule row = requireRow(id);
        ruleMapper.deleteById(row.getId());
        log.info("[消息发送设置] 删除规则：id={}，类型={}，维度={}，目标={}，操作人={}",
                row.getId(), row.getMessageType(), row.getTargetType(), row.getTargetId(), currentOperatorId());
    }

    /** 单行详情（新增/编辑回显用，名称已解析） */
    public MessageRuleVO detail(String id) {
        MessageRuleVO vo = toVO(requireRow(id));
        decorateAll(Collections.singletonList(vo));
        return vo;
    }

    // ==================== 校验与归一化 ====================

    /** 规则行存在性校验 */
    private MessageRule requireRow(String id) {
        String key = trim(id);
        if (key == null) {
            throw new IllegalArgumentException("规则 id 不能为空");
        }
        MessageRule row = ruleMapper.selectById(key);
        if (row == null) {
            throw new IllegalArgumentException("规则不存在或已删除");
        }
        return row;
    }

    /** 消息类型合法性校验 */
    private String requireMessageType(String raw) {
        String code = trim(raw);
        for (String[] pair : MESSAGE_TYPE_LABELS) {
            if (pair[0].equals(code)) {
                return code;
            }
        }
        throw new IllegalArgumentException("messageType 仅支持 #1# 告警 / #2# 举报投诉 / #3# 意见征集 / #4# 值班提醒 / #5# 模型计算");
    }

    /** 分页过滤参数：空=不过滤；传了必须合法 */
    private String optionalMessageType(String raw) {
        String code = trim(raw);
        return code == null ? null : requireMessageType(code);
    }

    /** 接收维度合法性校验 */
    private String requireTargetType(String raw) {
        String code = trim(raw);
        for (String[] pair : TARGET_TYPE_LABELS) {
            if (pair[0].equals(code)) {
                return code;
            }
        }
        throw new IllegalArgumentException("targetType 仅支持 #org# 部门 / #position# 岗位 / #role# 角色 / #user# 人员");
    }

    /** 目标编码校验：非空、去重保序、逐项须存在于对应档案（缺失项列出报错） */
    private List<String> requireTargets(String targetType, String raw) {
        List<String> items = splitItems(raw);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个接收对象");
        }
        Set<String> exists = new LinkedHashSet<>();
        for (PickerOptionVO option : queryTargets(targetType, items)) {
            exists.add(option.getValue());
        }
        List<String> missing = new ArrayList<>();
        for (String item : items) {
            if (!exists.contains(item)) {
                missing.add(item);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("以下" + targetTypeName(targetType) + "不存在：" + String.join("、", missing));
        }
        return items;
    }

    /** 站点范围归一化：仅告警保留；空=不限站点（NULL）；非告警恒为空；站点须存在于站点档案 */
    private String normalizeSites(String messageType, String raw) {
        if (!TYPE_ALERT.equals(messageType)) {
            return null;
        }
        List<String> items = splitItems(raw);
        if (items.isEmpty()) {
            return null;
        }
        Set<String> exists = new LinkedHashSet<>();
        for (PickerOptionVO option : ruleMapper.selectSiteOptions(items)) {
            exists.add(option.getValue());
        }
        List<String> missing = new ArrayList<>();
        for (String item : items) {
            if (!exists.contains(item)) {
                missing.add(item);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("以下站点不存在：" + String.join("、", missing));
        }
        return String.join(",", items);
    }

    // ==================== 视图翻译 ====================

    /** 实体 → 基础视图（名称字段留给 decorateAll 批量填充） */
    private MessageRuleVO toVO(MessageRule row) {
        MessageRuleVO vo = new MessageRuleVO();
        vo.setId(row.getId());
        vo.setMessageType(row.getMessageType());
        vo.setTargetType(row.getTargetType());
        vo.setTargetId(row.getTargetId());
        vo.setSiteId(row.getSiteId());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }

    /**
     * 批量填充行内名称：类型/维度中文名，目标与站点名称按维度合并去重后一次解析，
     * 回填时保持行内原始顺序；档案缺失项原样回退编码。
     */
    private void decorateAll(List<MessageRuleVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, Set<String>> targetsByType = new HashMap<>();
        Set<String> siteIds = new LinkedHashSet<>();
        for (MessageRuleVO row : rows) {
            row.setMessageTypeLabel(label(row.getMessageType(), MESSAGE_TYPE_LABELS));
            row.setTargetTypeLabel(label(row.getTargetType(), TARGET_TYPE_LABELS));
            Set<String> bucket = targetsByType.computeIfAbsent(row.getTargetType(), k -> new LinkedHashSet<>());
            bucket.addAll(splitItems(row.getTargetId()));
            siteIds.addAll(splitItems(row.getSiteId()));
        }
        Map<String, Map<String, String>> namesByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : targetsByType.entrySet()) {
            namesByType.put(entry.getKey(),
                    toNameMap(queryTargets(entry.getKey(), new ArrayList<>(entry.getValue()))));
        }
        Map<String, String> siteNames = siteIds.isEmpty()
                ? Collections.emptyMap() : toNameMap(ruleMapper.selectSiteOptions(new ArrayList<>(siteIds)));
        for (MessageRuleVO row : rows) {
            row.setTargetNames(joinNames(splitItems(row.getTargetId()),
                    namesByType.getOrDefault(row.getTargetType(), Collections.emptyMap())));
            row.setSiteNames(joinNames(splitItems(row.getSiteId()), siteNames));
        }
    }

    /** 按维度查询档案（目标解析 / 存在性校验共用） */
    private List<PickerOptionVO> queryTargets(String targetType, List<String> values) {
        if (targetType == null) {
            return new ArrayList<>();
        }
        switch (targetType) {
            case "#org#":
                return ruleMapper.selectOrgOptions(values);
            case "#position#":
                return ruleMapper.selectPositionOptions(values);
            case "#role#":
                return ruleMapper.selectRoleOptions(values);
            case "#user#":
                return ruleMapper.selectUserOptions(values);
            default:
                return new ArrayList<>();
        }
    }

    /** 维度名称（错误文案用） */
    private static String targetTypeName(String targetType) {
        String name = label(targetType, TARGET_TYPE_LABELS);
        return name != null ? name : targetType;
    }

    // ==================== 审计 ====================

    /** 新增审计字段（列均 NOT NULL，会话缺失时用配置兜底） */
    private void applyCreateAudit(MessageRule entity) {
        UserContext user = UserContextHolder.currentUser();
        String operator = user != null && trim(user.getUserId()) != null
                ? user.getUserId() : fallbackOperator;
        String corp = user != null && trim(user.getCorpCode()) != null
                ? user.getCorpCode() : corpCode;
        LocalDateTime now = LocalDateTime.now();
        entity.setCorpCode(corp);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        if (user == null) {
            log.warn("[消息发送设置] 当前请求无用户上下文，审计字段使用兜底值：operator={}, corp={}", operator, corp);
        }
    }

    /** 当前操作人（更新审计字段用） */
    private String currentOperatorId() {
        UserContext user = UserContextHolder.currentUser();
        if (user != null && trim(user.getUserId()) != null) {
            return user.getUserId();
        }
        log.warn("[消息发送设置] 当前请求无用户上下文，updated_by 使用兜底值：{}", fallbackOperator);
        return fallbackOperator;
    }

    // ==================== 工具方法 ====================

    /** 逗号拆分：去空白、去空段、去重保序 */
    private static List<String> splitItems(String raw) {
        List<String> items = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return items;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !items.contains(trimmed)) {
                items.add(trimmed);
            }
        }
        return items;
    }

    /** 选项列表 → value→label 映射 */
    private static Map<String, String> toNameMap(List<PickerOptionVO> options) {
        Map<String, String> map = new HashMap<>();
        for (PickerOptionVO option : options) {
            if (option.getValue() != null) {
                map.put(option.getValue(), option.getLabel());
            }
        }
        return map;
    }

    /** 编码列表 → 名称拼接（顿号分隔；档案缺失项回退编码） */
    private static String joinNames(List<String> items, Map<String, String> nameMap) {
        if (items.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String item : items) {
            String name = nameMap.get(item);
            names.add(name != null ? name : item);
        }
        return String.join("、", names);
    }

    private static String label(String code, String[][] mapping) {
        if (code == null) {
            return null;
        }
        for (String[] pair : mapping) {
            if (pair[0].equals(code)) {
                return pair[1];
            }
        }
        return code;
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return PAGE_SIZE_DEFAULT;
        }
        return Math.min(size, PAGE_SIZE_MAX);
    }

    /** 去首尾空白，空串按 null 处理 */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
