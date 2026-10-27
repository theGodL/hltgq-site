package com.qgyun.hltgq.hltgqsite.watersaving.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.LocalFileStorageUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.model.vo.FileDownloadVO;
import com.qgyun.hltgq.hltgqsite.waterusage.service.PlatformUserService;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingDict;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingSupport;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingAnnualMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingIndicatorMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.SavingSchemeMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingAnnualVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorResultVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingSchemeSaveVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingSchemeVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingVerifyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节水方案服务（方案与措施主流程：列表 / 新增 / 编辑 / 核验 / 刷新对比依据 / 附件 / 导出 / 措施文档）。
 * <p>口径（方案 V1.4 §2.2/§2.4/§6.1）：
 * <ul>
 *   <li>方案年度 = 依据记录的潜力年度（不单独选择）；baseline 建方案时快照固化，仅"刷新对比依据"更新；</li>
 *   <li>达标优先级链（服务端派生，不落库）：待核验 &gt; 待设置目标 &gt; 待关联指标 &gt; 无法计算(待复核)
 *       &gt; 不达标 &gt; 部分达标 &gt; 达标；核验有效 = 曾核验 且 对比条件已确认 且 未作废；</li>
 *   <li>方案A核验作废：年度记录水量变更 / 编辑换依据 / 刷新对比依据 → status '#4#'、verify_* 保留、
 *       verify_invalid '#1#'、达标强制"待核验"、last_recalc 留痕；</li>
 *   <li>状态机：#1# 草稿 / #2# 实施中（用户维护）—核验→ #3# 已完成（对比条件确认）/ #4# 待复核（未确认或作废）；</li>
 *   <li>附件：≤2MB，file-dir/saving/{yyyy-MM}/{短id}_{原名}，上传即替换（旧文件提交后清理）；
 *       文件 IO 走 LocalFileStorageUtils 唯一实现点。</li>
 * </ul>
 */
@Service
public class SavingSchemeService {

    private static final Logger log = LoggerFactory.getLogger(SavingSchemeService.class);

    /** 方案名称长度上限（DB VARCHAR(200)） */
    private static final int NAME_MAX_LENGTH = 200;

    /** 具体措施长度上限（TEXT，防御性） */
    private static final int MEASURE_MAX_LENGTH = 2000;

    /** 核验资料来源长度上限（DB VARCHAR(200)） */
    private static final int VERIFY_SOURCE_MAX_LENGTH = 200;

    /** 核验说明长度上限（TEXT，防御性） */
    private static final int VERIFY_NOTE_MAX_LENGTH = 500;

    /** 导出文件名时间戳 */
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 文档生成时间戳 */
    private static final DateTimeFormatter DOC_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SavingSchemeMapper schemeMapper;

    private final SavingAnnualMapper annualMapper;

    private final SavingIndicatorMapper indicatorMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final ObjectMapper objectMapper;

    /** 平台用户选人（负责人候选 + 保存回查固化姓名，复用用水模块唯一实现） */
    private final PlatformUserService platformUsers;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    /** 附件本地存储根目录（宿主机挂载 ./file-dir） */
    @Value("${hltgq.file-dir:./file-dir}")
    private String fileDir;

    public SavingSchemeService(SavingSchemeMapper schemeMapper,
                               SavingAnnualMapper annualMapper,
                               SavingIndicatorMapper indicatorMapper,
                               ShortIdGenerator shortIdGenerator,
                               ObjectMapper objectMapper,
                               PlatformUserService platformUsers) {
        this.schemeMapper = schemeMapper;
        this.annualMapper = annualMapper;
        this.indicatorMapper = indicatorMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
        this.platformUsers = platformUsers;
    }

    // ==================== 查询 ====================

    /** 分页列表（装配指标计算结果行 + 依据摘要 + 达标链；筛选：年度/名称/负责人/状态） */
    public Map<String, Object> page(int page, int size, Integer year, String name, String owner, String status) {
        int p = SavingSupport.clampPage(page);
        int s = SavingSupport.clampSize(size);
        String nameKw = SavingSupport.trim(name);
        String ownerKw = SavingSupport.trim(owner);
        String statusCode = SavingSupport.trim(status);
        long total = schemeMapper.countPage(year, nameKw, ownerKw, statusCode);
        List<SavingSchemeVO> rows = total == 0
                ? new ArrayList<>()
                : schemeMapper.selectPage(year, nameKw, ownerKw, statusCode, s, (long) (p - 1) * s);
        assemble(rows);
        return SavingSupport.pageResult(total, p, s, rows);
    }

    /** 详情（装配完整：指标结果行 / 依据摘要 / 达标链 / 待复核原因） */
    public SavingSchemeVO detail(String id) {
        SavingSchemeVO vo = requireScheme(id);
        assemble(Collections.singletonList(vo));
        return vo;
    }

    // ==================== 新增 / 编辑 ====================

    /**
     * 新增：名称年度内唯一；目标 0 &lt; 目标 ≤ 潜力（潜力 ≤ 0 不可建方案）；指标 ≥1 且全部启用；
     * 初始状态仅 #1# 草稿 / #2# 实施中（默认草稿）；baseline 快照 = 依据记录实际放水量。
     */
    @Transactional(rollbackFor = Exception.class)
    public SavingSchemeVO create(SavingSchemeSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = SavingSupport.maxLength(
                SavingSupport.requireText(req.getName(), "请填写方案名称"), NAME_MAX_LENGTH, "方案名称");
        // 负责人：传 ownerUserId 时按平台用户回查固化姓名快照（不信任前端文本）；仅回传姓名（未换人/存量值）时按文本入库
        String owner = platformUsers.resolveName(req.getOwnerUserId(), req.getOwner(), "负责人", true);
        String measure = SavingSupport.maxLength(
                SavingSupport.requireText(req.getMeasure(), "请填写具体措施"), MEASURE_MAX_LENGTH, "具体措施");
        BigDecimal target = SavingSupport.requirePositive(req.getTargetVolume(), "节水目标");
        String status = normalizeCreateStatus(req.getStatus());
        String isDemo = SavingDict.normalizeFlag(req.getIsDemo());

        SavingAnnualVO evidence = requireEvidence(req.getEvidenceId());
        BigDecimal potential = requireUsablePotential(evidence);
        if (target.compareTo(potential) > 0) {
            throw new IllegalArgumentException("节水目标不能超过潜力（" + plain(potential) + " 万m³）");
        }
        int year = evidence.getPotentialYear();
        if (schemeMapper.countByName(year, name, null) > 0) {
            throw new IllegalArgumentException(year + " 年度已存在同名方案，请更换名称");
        }
        List<String> indicatorIds = normalizeIndicatorIds(req.getIndicatorIds());
        Map<String, SavingIndicatorVO> defs = loadIndicators(indicatorIds);
        for (String iid : indicatorIds) {
            SavingIndicatorVO def = defs.get(iid);
            if (!SavingDict.YES.equals(def.getEnabled())) {
                throw new IllegalArgumentException("指标「" + def.getName() + "」已停用，不可勾选");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("year", year);
        row.put("evidence_id", evidence.getId());
        row.put("baseline_volume", evidence.getPrevActualVolume());
        row.put("target_volume", target);
        row.put("owner", owner);
        row.put("measure", measure);
        row.put("status", status);
        row.put("indicator_ids", toJson(indicatorIds));
        row.put("is_demo", isDemo);
        row.put("now", now);
        row.put("operator", operator);
        schemeMapper.insert(row);
        log.info("[节水管理] 方案新增：{}（{} 年度，目标={} 万m³，{} 个指标），操作人={}",
                name, year, plain(target), indicatorIds.size(), operator);
        return detail(id);
    }

    /**
     * 编辑：名称年度内唯一；目标 0 &lt; 目标 ≤ 潜力；换依据 → baseline 取新记录实放 + 曾核验作废（方案A）；
     * 未核验方案状态可在 #1#/#2# 间调整；已核验方案状态与核验数据由核验动作驱动（提交值忽略）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SavingSchemeVO update(String id, SavingSchemeSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        SavingSchemeVO exist = requireScheme(id);
        String name = SavingSupport.maxLength(
                SavingSupport.requireText(req.getName(), "请填写方案名称"), NAME_MAX_LENGTH, "方案名称");
        // 负责人：传 ownerUserId 时按平台用户回查固化姓名快照（不信任前端文本）；仅回传姓名（未换人/存量值）时按文本入库
        String owner = platformUsers.resolveName(req.getOwnerUserId(), req.getOwner(), "负责人", true);
        String measure = SavingSupport.maxLength(
                SavingSupport.requireText(req.getMeasure(), "请填写具体措施"), MEASURE_MAX_LENGTH, "具体措施");
        BigDecimal target = SavingSupport.requirePositive(req.getTargetVolume(), "节水目标");
        String isDemo = SavingDict.normalizeFlag(req.getIsDemo());

        SavingAnnualVO evidence = requireEvidence(req.getEvidenceId());
        BigDecimal potential = requireUsablePotential(evidence);
        if (target.compareTo(potential) > 0) {
            throw new IllegalArgumentException("节水目标不能超过潜力（" + plain(potential) + " 万m³）");
        }
        int year = evidence.getPotentialYear();
        if (schemeMapper.countByName(year, name, exist.getId()) > 0) {
            throw new IllegalArgumentException(year + " 年度已存在同名方案，请更换名称");
        }

        boolean hadVerify = exist.getVerifyAfterVolume() != null;
        boolean evidenceChanged = !evidence.getId().equals(exist.getEvidenceId());
        // 状态：已核验方案提交值忽略（核验动作驱动）；未核验方案仅可在草稿/实施中之间调整
        String status = exist.getStatus();
        String statusIn = SavingSupport.trim(req.getStatus());
        if (!hadVerify && statusIn != null && !statusIn.equals(exist.getStatus())) {
            if (!SavingDict.SCHEME_STATUS_DRAFT.equals(statusIn) && !SavingDict.SCHEME_STATUS_DOING.equals(statusIn)) {
                throw new IllegalArgumentException("状态仅可选：草稿 / 实施中（已完成与待复核由核验动作驱动）");
            }
            status = statusIn;
        }

        List<String> indicatorIds = normalizeIndicatorIds(req.getIndicatorIds());
        Map<String, SavingIndicatorVO> defs = loadIndicators(indicatorIds);
        Set<String> oldIds = new HashSet<>(parseIndicatorIds(exist));
        for (String iid : indicatorIds) {
            if (!oldIds.contains(iid) && !SavingDict.YES.equals(defs.get(iid).getEnabled())) {
                throw new IllegalArgumentException("指标「" + defs.get(iid).getName() + "」已停用，不可勾选");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", exist.getId());
        row.put("name", name);
        row.put("year", year);
        row.put("evidence_id", evidence.getId());
        // 换依据 → baseline 取新记录实放；未换 → 保持快照（拉齐走"刷新对比依据"）
        row.put("baseline_volume", evidenceChanged ? evidence.getPrevActualVolume() : exist.getBaselineVolume());
        row.put("target_volume", target);
        row.put("owner", owner);
        row.put("measure", measure);
        row.put("indicator_ids", toJson(indicatorIds));
        row.put("is_demo", isDemo);
        row.put("now", now);
        row.put("operator", operator);
        schemeMapper.updateBase(row);
        if (!status.equals(exist.getStatus())) {
            if (schemeMapper.updateStatus(exist.getId(), exist.getStatus(), status, now, operator) == 0) {
                throw new IllegalArgumentException("方案状态已被其他操作变更，请刷新后重试");
            }
        }
        if (evidenceChanged && hadVerify) {
            schemeMapper.invalidateVerifyById(exist.getId(), SavingDict.RECHECK_REASON_INVALIDATED, now, operator);
            log.info("[节水管理] 方案换依据（id={}），已核验数据作废，操作人={}", exist.getId(), operator);
        }
        log.info("[节水管理] 方案编辑：{}（id={}），目标={} 万m³，{} 个指标，操作人={}",
                name, exist.getId(), plain(target), indicatorIds.size(), operator);
        return detail(exist.getId());
    }

    // ==================== 核验 / 刷新对比依据 ====================

    /**
     * 核验：写入 verify_*（含核验日期）+ 复位作废标记；对比条件确认 → 已完成 + 按公式计算；
     * 未确认 → 待复核（不计算指标）。草稿方案不可核验。
     */
    @Transactional(rollbackFor = Exception.class)
    public SavingSchemeVO verify(SavingVerifyVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        SavingSchemeVO exist = requireScheme(req.getId());
        if (SavingDict.SCHEME_STATUS_DRAFT.equals(exist.getStatus())) {
            throw new IllegalArgumentException("草稿方案不可核验，请先将状态改为实施中");
        }
        BigDecimal afterVolume = SavingSupport.requireNonNegative(req.getAfterVolume(), "实施后实际用水量");
        String source = SavingSupport.maxLength(
                SavingSupport.requireText(req.getSource(), "请填写核验资料来源"),
                VERIFY_SOURCE_MAX_LENGTH, "核验资料来源");
        String note = SavingSupport.maxLength(SavingSupport.trim(req.getNote()), VERIFY_NOTE_MAX_LENGTH, "核验说明");
        String comparable = SavingSupport.requireText(req.getComparable(), "请选择对比条件确认情况");
        if (!SavingDict.YES.equals(comparable) && !SavingDict.NO.equals(comparable)) {
            throw new IllegalArgumentException("对比条件确认取值非法");
        }
        String toStatus = SavingDict.YES.equals(comparable)
                ? SavingDict.SCHEME_STATUS_DONE : SavingDict.SCHEME_STATUS_RECHECK;
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        schemeMapper.updateVerify(exist.getId(), afterVolume, source, note, SavingSupport.today(),
                comparable, toStatus, "核验数据更新", now, operator);
        log.info("[节水管理] 方案核验：{}（id={}），对比条件{}，状态→{}，操作人={}",
                exist.getName(), exist.getId(),
                SavingDict.YES.equals(comparable) ? "已确认" : "未确认",
                SavingDict.schemeStatusName(toStatus), operator);
        return detail(exist.getId());
    }

    /**
     * 刷新对比依据：baseline 拉齐为当前记录实际值；曾核验 → 同时作废核验（方案A）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SavingSchemeVO refreshBasis(String id) {
        SavingSchemeVO exist = requireScheme(id);
        String evidenceId = SavingSupport.requireText(exist.getEvidenceId(), "方案缺少对比依据");
        SavingAnnualVO evidence = annualMapper.selectById(evidenceId);
        if (evidence == null) {
            throw new IllegalArgumentException("对比依据记录不存在或已删除");
        }
        BigDecimal baseline = evidence.getPrevActualVolume();
        if (baseline == null) {
            throw new IllegalArgumentException("依据记录的实际放水量缺失，无法刷新对比依据");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        schemeMapper.refreshBaseline(exist.getId(), baseline, SavingDict.RECHECK_REASON_INVALIDATED, now, operator);
        log.info("[节水管理] 刷新对比依据：{}（id={}），基线→{} 万m³，操作人={}",
                exist.getName(), exist.getId(), plain(baseline), operator);
        return detail(exist.getId());
    }

    // ==================== 删除 ====================

    /** 物理删除（附件文件事务提交后清理） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SavingSchemeVO exist = requireScheme(id);
        String attachmentPath = SavingSupport.trim(schemeMapper.selectAttachmentUrl(exist.getId()));
        schemeMapper.delete(exist.getId());
        if (attachmentPath != null) {
            LocalFileStorageUtils.deleteAfterCommit(fileDirPath(), Collections.singletonList(attachmentPath));
        }
        log.info("[节水管理] 方案删除：{}（id={}）", exist.getName(), exist.getId());
    }

    // ==================== 措施文档 ====================

    /** 生成措施文档（服务端拼装纯文本，前端下载 .txt） */
    public Map<String, Object> document(String id) {
        SavingSchemeVO vo = detail(id);
        StringBuilder sb = new StringBuilder();
        sb.append("节水方案与措施\r\n");
        sb.append("====================\r\n\r\n");
        sb.append("一、基本信息\r\n");
        sb.append("方案名称：").append(nvl(vo.getName())).append("\r\n");
        sb.append("实施年度：").append(vo.getYear() == null ? "" : vo.getYear()).append("\r\n");
        sb.append("负责人：").append(nvl(vo.getOwner())).append("\r\n");
        sb.append("方案状态：").append(nvl(vo.getStatusName())).append("\r\n");
        sb.append("节水目标：").append(plain(vo.getTargetVolume())).append(" 万m³\r\n");
        sb.append("\r\n二、对比依据\r\n");
        sb.append("依据年度：").append(vo.getEvidenceBasisYear() == null ? "" : vo.getEvidenceBasisYear()).append("\r\n");
        sb.append("上一年计划放水量：").append(plain(vo.getEvidencePlanVolume())).append(" 万m³\r\n");
        sb.append("上一年实际放水量：").append(plain(vo.getEvidenceActualVolume())).append(" 万m³\r\n");
        sb.append("节水潜力：").append(plain(vo.getEvidencePotential())).append(" 万m³\r\n");
        sb.append("\r\n三、评估指标\r\n");
        List<SavingIndicatorResultVO> indicators = vo.getIndicators();
        if (indicators == null || indicators.isEmpty()) {
            sb.append("（未关联评估指标）\r\n");
        } else {
            int index = 1;
            for (SavingIndicatorResultVO row : indicators) {
                sb.append(index++).append(". ").append(nvl(row.getName())).append("\r\n");
                sb.append("   计算方式：").append(nvl(row.getMethodName()))
                        .append("（").append(nvl(row.getUnit())).append("），公式：")
                        .append(nvl(row.getFormula())).append("\r\n");
                sb.append("   指标目标值：").append(plain(row.getTargetValue()))
                        .append(" ").append(nvl(row.getUnit())).append("\r\n");
                sb.append("   计算结果：").append(plain(row.getResult()))
                        .append(row.getResult() == null ? "" : " " + nvl(row.getUnit())).append("\r\n");
                sb.append("   达标状态：").append(nvl(row.getStateName())).append("\r\n");
            }
        }
        sb.append("\r\n四、具体措施\r\n");
        sb.append(nvl(vo.getMeasure())).append("\r\n");
        sb.append("\r\n五、核验信息\r\n");
        if (vo.getVerifyAfterVolume() == null) {
            sb.append("尚未核验\r\n");
        } else {
            sb.append("核验日期：").append(nvl(vo.getVerifyDate())).append("\r\n");
            sb.append("实施后实际用水量：").append(plain(vo.getVerifyAfterVolume())).append(" 万m³\r\n");
            sb.append("对比条件确认：").append(SavingDict.YES.equals(vo.getVerifyComparable()) ? "已确认" : "未确认")
                    .append(SavingDict.YES.equals(vo.getVerifyInvalid()) ? "（核验已作废，数据保留）" : "").append("\r\n");
            sb.append("核验资料来源：").append(nvl(vo.getVerifySource())).append("\r\n");
            sb.append("核验说明：").append(nvl(vo.getVerifyNote())).append("\r\n");
        }
        sb.append("\r\n生成时间：").append(LocalDateTime.now().format(DOC_STAMP)).append("\r\n");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileName", documentFileName(vo.getName()));
        out.put("content", sb.toString());
        return out;
    }

    // ==================== 附件（上传 / 下载 / 移除） ====================

    /** 上传附件（上传即替换：≤2MB、白名单、saving/yyyy-MM/短id_原名；旧文件提交后清理） */
    public SavingSchemeVO uploadAttachment(String id, MultipartFile file) {
        SavingSchemeVO exist = requireScheme(id);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > SavingSupport.ATTACHMENT_MAX_BYTES) {
            throw new IllegalArgumentException("附件大小不能超过 2MB");
        }
        String originalName = LocalFileStorageUtils.sanitizeFileName(file.getOriginalFilename());
        String ext = LocalFileStorageUtils.extOf(originalName);
        if (!LocalFileStorageUtils.isAllowedExt(ext)) {
            throw new IllegalArgumentException("不支持的文件类型（仅支持图片、PDF、Word、Excel）：" + originalName);
        }
        String oldPath = SavingSupport.trim(schemeMapper.selectAttachmentUrl(exist.getId()));
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        String relPath = LocalFileStorageUtils.relPath(SavingSupport.SAVING_DIR, now, shortIdGenerator.nextUUID(null), originalName);
        LocalFileStorageUtils.write(fileDirPath(), relPath, file);
        try {
            schemeMapper.updateAttachment(exist.getId(), originalName, relPath, now, operator);
        } catch (RuntimeException e) {
            // 行写入失败：清理已落盘文件，避免孤儿文件
            LocalFileStorageUtils.deleteQuietly(fileDirPath(), relPath);
            throw e;
        }
        if (oldPath != null && !oldPath.equals(relPath)) {
            LocalFileStorageUtils.deleteAfterCommit(fileDirPath(), Collections.singletonList(oldPath));
        }
        log.info("[节水管理] 方案附件上传：{}（{} 字节），方案={}（id={}），操作人={}",
                originalName, file.getSize(), exist.getName(), exist.getId(), operator);
        return detail(exist.getId());
    }

    /** 下载附件（路径穿越校验；文件缺失提示已被清理） */
    public FileDownloadVO downloadAttachment(String id) {
        SavingSchemeVO exist = requireScheme(id);
        String relPath = SavingSupport.requireText(schemeMapper.selectAttachmentUrl(exist.getId()), "该方案没有附件");
        byte[] bytes = LocalFileStorageUtils.readBytes(fileDirPath(), relPath);
        FileDownloadVO vo = new FileDownloadVO();
        vo.setContent(bytes);
        String name = SavingSupport.trim(exist.getAttachmentName());
        vo.setFileName(name == null ? Paths.get(relPath).getFileName().toString() : name);
        vo.setFileExt(LocalFileStorageUtils.extOf(vo.getFileName()));
        return vo;
    }

    /** 移除附件（清空列 + 物理删文件 best-effort） */
    @Transactional(rollbackFor = Exception.class)
    public SavingSchemeVO removeAttachment(String id) {
        SavingSchemeVO exist = requireScheme(id);
        String relPath = SavingSupport.trim(schemeMapper.selectAttachmentUrl(exist.getId()));
        if (relPath == null) {
            throw new IllegalArgumentException("该方案没有附件");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = SavingSupport.currentOperatorId(fallbackOperator);
        schemeMapper.updateAttachment(exist.getId(), null, null, now, operator);
        LocalFileStorageUtils.deleteQuietly(fileDirPath(), relPath);
        log.info("[节水管理] 方案附件移除：方案={}（id={}），文件={}", exist.getName(), exist.getId(), relPath);
        return detail(exist.getId());
    }

    // ==================== 导出 ====================

    /**
     * 导出 CSV（方案 × 指标展开为多行；UTF-8 BOM）。
     * <p>includeDemo=false（默认）排除虚拟演示数据；"方案达标状态"列 = 等级（+说明）。
     */
    public FileDownloadVO exportCsv(boolean includeDemo) {
        List<SavingSchemeVO> rows = schemeMapper.selectForExport(includeDemo);
        assemble(rows);
        StringBuilder sb = new StringBuilder();
        String[] header = {"年度", "方案名称", "指标", "计算公式", "结果", "单位", "指标目标值",
                "指标达标状态", "方案达标状态", "资料来源", "虚拟演示"};
        sb.append(String.join(",", header)).append("\r\n");
        for (SavingSchemeVO vo : rows) {
            String attainText = attainText(vo);
            List<SavingIndicatorResultVO> indicators = vo.getIndicators();
            if (indicators == null || indicators.isEmpty()) {
                appendCsvRow(sb, vo, null, attainText);
            } else {
                for (SavingIndicatorResultVO row : indicators) {
                    appendCsvRow(sb, vo, row, attainText);
                }
            }
        }
        byte[] bytes = ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
        FileDownloadVO out = new FileDownloadVO();
        out.setContent(bytes);
        out.setFileName("节水方案列表_" + LocalDateTime.now().format(FILE_STAMP) + ".csv");
        out.setFileExt("csv");
        return out;
    }

    // ==================== 装配（指标结果行 / 依据摘要 / 达标链） ====================

    /** 批量装配（列表/导出）：解析 indicator_ids → 批量取指标与依据 → 逐行达标计算 */
    private void assemble(List<SavingSchemeVO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Set<String> indicatorIds = new LinkedHashSet<>();
        Set<String> evidenceIds = new LinkedHashSet<>();
        for (SavingSchemeVO vo : rows) {
            vo.setIndicatorIds(parseIndicatorIds(vo));
            indicatorIds.addAll(vo.getIndicatorIds());
            String evidenceId = SavingSupport.trim(vo.getEvidenceId());
            if (evidenceId != null) {
                evidenceIds.add(evidenceId);
            }
        }
        Map<String, SavingIndicatorVO> defs = new HashMap<>();
        if (!indicatorIds.isEmpty()) {
            for (SavingIndicatorVO def : indicatorMapper.selectByIds(new ArrayList<>(indicatorIds))) {
                defs.put(def.getId(), def);
            }
        }
        Map<String, SavingAnnualVO> evidences = new HashMap<>();
        if (!evidenceIds.isEmpty()) {
            for (SavingAnnualVO annual : annualMapper.selectByIds(new ArrayList<>(evidenceIds))) {
                evidences.put(annual.getId(), annual);
            }
        }
        for (SavingSchemeVO vo : rows) {
            List<SavingIndicatorVO> rowDefs = new ArrayList<>();
            for (String iid : vo.getIndicatorIds()) {
                SavingIndicatorVO def = defs.get(iid);
                if (def != null) {
                    rowDefs.add(def);
                }
            }
            decorate(vo, rowDefs);
            SavingAnnualVO evidence = evidences.get(vo.getEvidenceId());
            if (evidence != null) {
                vo.setEvidenceBasisYear(evidence.getBasisYear());
                vo.setEvidencePlanVolume(evidence.getPrevPlanVolume());
                vo.setEvidenceActualVolume(evidence.getPrevActualVolume());
                vo.setEvidencePotential(SavingDict.potential(evidence.getPrevPlanVolume(), evidence.getPrevActualVolume()));
            }
        }
    }

    /**
     * 单行达标计算（§2.2 优先级链，从高到低）：
     * 待核验 &gt; 待设置目标 &gt; 待关联指标 &gt; 无法计算(待复核) &gt; 不达标 &gt; 部分达标 &gt; 达标。
     */
    private void decorate(SavingSchemeVO vo, List<SavingIndicatorVO> defs) {
        vo.setStatusName(SavingDict.schemeStatusName(vo.getStatus()));
        // 核验有效 = 曾核验 且 对比条件已确认 且 未作废（作废/未确认/未核验 → 全部按"待核验"处理）
        boolean valid = vo.getVerifyAfterVolume() != null
                && SavingDict.YES.equals(vo.getVerifyComparable())
                && !SavingDict.YES.equals(vo.getVerifyInvalid());

        List<SavingIndicatorResultVO> resultRows = new ArrayList<>();
        boolean hasNoTarget = false;
        boolean hasUncomputable = false;
        boolean hasPass = false;
        boolean hasFail = false;
        for (SavingIndicatorVO def : defs) {
            SavingIndicatorResultVO row = new SavingIndicatorResultVO();
            row.setId(def.getId());
            row.setName(def.getName());
            row.setMethod(def.getMethod());
            row.setMethodName(SavingDict.methodName(def.getMethod()));
            row.setUnit(SavingDict.methodUnit(def.getMethod()));
            row.setFormula(SavingDict.methodFormula(def.getMethod()));
            row.setTargetValue(def.getTargetValue());
            String state;
            BigDecimal result = null;
            if (!valid) {
                state = SavingDict.ROW_PENDING;
            } else if (def.getTargetValue() == null) {
                state = SavingDict.ROW_NO_TARGET;
                hasNoTarget = true;
            } else {
                result = SavingDict.methodResult(def.getMethod(), vo.getBaselineVolume(),
                        vo.getVerifyAfterVolume(), vo.getTargetVolume());
                if (result == null) {
                    state = SavingDict.ROW_UNCOMPUTABLE;
                    hasUncomputable = true;
                } else if (result.compareTo(def.getTargetValue()) >= 0) {
                    state = SavingDict.ROW_PASS;
                    hasPass = true;
                } else {
                    state = SavingDict.ROW_FAIL;
                    hasFail = true;
                }
            }
            row.setResult(result);
            row.setState(state);
            row.setStateName(SavingDict.rowLabel(state));
            resultRows.add(row);
        }
        vo.setIndicators(resultRows);

        String level;
        String reason = null;
        if (!valid) {
            level = SavingDict.ATTAIN_PENDING_VERIFY;
        } else if (hasNoTarget) {
            level = SavingDict.ATTAIN_PENDING_TARGET;
        } else if (resultRows.isEmpty()) {
            level = SavingDict.ATTAIN_PENDING_INDICATOR;
        } else if (hasUncomputable) {
            level = SavingDict.ATTAIN_UNCOMPUTABLE;
            reason = SavingDict.RECHECK_REASON_DIV_ZERO;
        } else if (!hasPass) {
            level = SavingDict.ATTAIN_FAIL;
        } else if (hasFail) {
            level = SavingDict.ATTAIN_PARTIAL;
        } else {
            level = SavingDict.ATTAIN_PASS;
        }
        vo.setAttainLevel(level);
        vo.setAttainLabel(SavingDict.attainLabel(level));
        vo.setAttainReason(reason);
        // 待复核原因（status = #4#：作废 →"核验依据已变更"；否则"对比条件未确认"）
        vo.setStatusReason(SavingDict.SCHEME_STATUS_RECHECK.equals(vo.getStatus())
                ? (SavingDict.YES.equals(vo.getVerifyInvalid())
                        ? SavingDict.RECHECK_REASON_INVALIDATED : SavingDict.RECHECK_REASON_UNCONFIRMED)
                : null);
    }

    // ==================== 校验与内部工具 ====================

    private SavingSchemeVO requireScheme(String id) {
        String key = SavingSupport.requireText(id, "方案 id 不能为空");
        SavingSchemeVO vo = schemeMapper.selectById(key);
        if (vo == null) {
            throw new IllegalArgumentException("方案不存在或已删除");
        }
        return vo;
    }

    /** 依据记录必填且存在 */
    private SavingAnnualVO requireEvidence(String evidenceId) {
        String key = SavingSupport.requireText(evidenceId, "请选择对比依据（年度水量记录）");
        SavingAnnualVO evidence = annualMapper.selectById(key);
        if (evidence == null) {
            throw new IllegalArgumentException("对比依据记录不存在或已删除");
        }
        return evidence;
    }

    /** 依据记录潜力可用（计划/实放齐全，潜力 > 0，否则不可建方案） */
    private static BigDecimal requireUsablePotential(SavingAnnualVO evidence) {
        BigDecimal potential = SavingDict.potential(evidence.getPrevPlanVolume(), evidence.getPrevActualVolume());
        if (potential == null) {
            throw new IllegalArgumentException("该年度记录水量不完整（计划与实放均需填写），无法计算节水潜力");
        }
        if (potential.signum() <= 0) {
            throw new IllegalArgumentException("该年度节水潜力为 " + plain(potential)
                    + " 万m³（不高于 0），不具备建立节水方案条件");
        }
        return potential;
    }

    /** 新增初始状态：仅 #1#/#2#（默认草稿） */
    private static String normalizeCreateStatus(String raw) {
        String code = SavingSupport.trim(raw);
        if (code == null) {
            return SavingDict.SCHEME_STATUS_DRAFT;
        }
        if (SavingDict.SCHEME_STATUS_DRAFT.equals(code) || SavingDict.SCHEME_STATUS_DOING.equals(code)) {
            return code;
        }
        throw new IllegalArgumentException("初始状态仅可选：草稿 / 实施中（已完成与待复核由核验动作驱动）");
    }

    /** 指标 id 列表归一（去空、去重保序；不允许为空） */
    private static List<String> normalizeIndicatorIds(List<String> raw) {
        List<String> out = normalizeQuietly(raw);
        if (out.isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一个评估指标");
        }
        return out;
    }

    /** 批量取指标并校验全部存在 */
    private Map<String, SavingIndicatorVO> loadIndicators(List<String> ids) {
        Map<String, SavingIndicatorVO> map = new HashMap<>();
        for (SavingIndicatorVO def : indicatorMapper.selectByIds(ids)) {
            map.put(def.getId(), def);
        }
        for (String id : ids) {
            if (!map.containsKey(id)) {
                throw new IllegalArgumentException("勾选的指标不存在或已删除，请刷新后重新勾选");
            }
        }
        return map;
    }

    /** 库列 indicator_ids（JSON 数组文本）→ id 列表（去空去重保序；解析失败按空处理并告警） */
    private List<String> parseIndicatorIds(SavingSchemeVO vo) {
        String json = SavingSupport.trim(vo.getIndicatorIdsJson());
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return normalizeQuietly(raw);
        } catch (Exception e) {
            log.warn("[节水管理] 方案指标 ids 解析失败（id={}）：{}", vo.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<String> normalizeQuietly(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String item : raw) {
                String id = SavingSupport.trim(item);
                if (id != null && !out.contains(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private String toJson(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("指标数据序列化失败: " + e.getMessage());
        }
    }

    /** 导出"方案达标状态"列：等级（+说明，优先无法计算说明，其次待复核原因） */
    private static String attainText(SavingSchemeVO vo) {
        String base = nvl(vo.getAttainLabel());
        String reason = SavingSupport.trim(vo.getAttainReason());
        if (reason == null) {
            reason = SavingSupport.trim(vo.getStatusReason());
        }
        return reason == null ? base : base + "（" + reason + "）";
    }

    private static void appendCsvRow(StringBuilder sb, SavingSchemeVO vo,
                                     SavingIndicatorResultVO row, String attainText) {
        List<String> cells = new ArrayList<>(11);
        cells.add(vo.getYear() == null ? "" : String.valueOf(vo.getYear()));
        cells.add(nvl(vo.getName()));
        cells.add(row == null ? "" : nvl(row.getName()));
        cells.add(row == null ? "" : nvl(row.getFormula()));
        cells.add(row == null ? "" : plain(row.getResult()));
        cells.add(row == null ? "" : nvl(row.getUnit()));
        cells.add(row == null ? "" : plain(row.getTargetValue()));
        cells.add(row == null ? "" : nvl(row.getStateName()));
        cells.add(nvl(attainText));
        cells.add(nvl(vo.getVerifySource()));
        cells.add(SavingDict.YES.equals(vo.getIsDemo()) ? "是" : "否");
        List<String> escaped = new ArrayList<>(cells.size());
        for (String cell : cells) {
            escaped.add(csvCell(cell));
        }
        sb.append(String.join(",", escaped)).append("\r\n");
    }

    /** CSV 单元格转义（含逗号/引号/换行时双引号包裹 + 引号翻倍） */
    private static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** 文档文件名（清洗非法字符） */
    private static String documentFileName(String schemeName) {
        String name = schemeName == null ? "" : schemeName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) {
            name = "节水方案";
        }
        return name + "_节水方案措施.txt";
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** 数值展示（去尾零，避免科学计数） */
    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    // ==================== 附件路径工具（文件操作走 LocalFileStorageUtils 唯一实现点） ====================

    /** 附件存储根目录（绑定配置；解析/落盘/读取/删除/文件名清洗见 LocalFileStorageUtils） */
    private Path fileDirPath() {
        return LocalFileStorageUtils.root(fileDir);
    }
}
