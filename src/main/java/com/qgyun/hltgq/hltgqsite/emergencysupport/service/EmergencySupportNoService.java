package com.qgyun.hltgq.hltgqsite.emergencysupport.service;

import com.qgyun.hltgq.hltgqsite.emergencysupport.EmergencySupportDict;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.currentOperatorId;

/**
 * 应急保障编号取号（方案口径：YJBZ-YYYYMMDD，自动生成、只读、唯一）。
 * <p>实际编号 = {@code YJBZ-yyyyMMdd-NNN}（NNN 为当日序号，3 位左补零，超 999 自然进位）：
 * 纯日期格式当日多单会重号，序号是唯一性的必要补充。
 * <p><b>独立事务（REQUIRES_NEW）</b>：序号在主事务失败时不回滚，跳号不重排不回收（预期行为）；
 * 复用出入库取号表 water_order_no_seq（serial_type=YJBZ），KES 一步式 ON CONFLICT 自增 + RETURNING。
 */
@Service
public class EmergencySupportNoService {

    private static final Logger log = LoggerFactory.getLogger(EmergencySupportNoService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 编号前缀（与库内历史数据 YJBZ-2026-001 前缀一致） */
    private static final String PREFIX = "YJBZ-";

    /** 当日序号格式化：不足 3 位左补零 */
    private static final String SEQ_FORMAT = "%03d";

    private final InventoryLedgerMapper ledgerMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public EmergencySupportNoService(InventoryLedgerMapper ledgerMapper, ShortIdGenerator shortIdGenerator) {
        this.ledgerMapper = ledgerMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    /**
     * 取下一个应急保障编号并独立提交。
     *
     * @return 完整编号，如 YJBZ-20261026-001
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextCode() {
        LocalDateTime now = LocalDateTime.now();
        String serialDate = now.format(DATE);
        String operator = currentOperatorId(fallbackOperator);
        Integer seq = ledgerMapper.upsertOrderNoSeq(
                shortIdGenerator.nextUUID(null), EmergencySupportDict.SERIAL_TYPE, serialDate, operator, now);
        if (seq == null) {
            throw new IllegalStateException("应急保障编号生成失败，请重试");
        }
        String code = PREFIX + serialDate + "-" + String.format(SEQ_FORMAT, seq);
        log.info("[应急保障] 取号：{}（操作人={}）", code, operator);
        return code;
    }
}
