package com.qgyun.hltgq.hltgqsite.inventoryorder.service;

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
 * 单据编号取号（设计方案 3.0 / 5.7）。
 * <p>编号 = 来源（RK/CK）+ yyyyMMdd（<b>取号时刻</b>系统日期，与业务日期解耦）+ 4 位序号。
 * <p><b>独立事务（REQUIRES_NEW）</b>：序号在主事务失败时不回滚，跳号不重排不回收（预期行为）；
 * 调用必须跨 bean（OrderService → 本类），保证 Spring 事务代理生效；
 * KES 一步式 ON CONFLICT 自增 + RETURNING 在 Mapper 层完成，禁止 SELECT MAX+1。
 */
@Service
public class InventoryOrderNoService {

    private static final Logger log = LoggerFactory.getLogger(InventoryOrderNoService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 序号格式化：不足 4 位左补零（超 9999 自然进位为 5 位，不截断） */
    private static final String SEQ_FORMAT = "%04d";

    private final InventoryLedgerMapper ledgerMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public InventoryOrderNoService(InventoryLedgerMapper ledgerMapper, ShortIdGenerator shortIdGenerator) {
        this.ledgerMapper = ledgerMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    /**
     * 取下一个单据编号并独立提交。
     *
     * @param serialType RK 入库 / CK 出库
     * @return 完整编号，如 RK202610120001
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextOrderNo(String serialType) {
        LocalDateTime now = LocalDateTime.now();
        String serialDate = now.format(DATE);
        String operator = currentOperatorId(fallbackOperator);
        Integer seq = ledgerMapper.upsertOrderNoSeq(
                shortIdGenerator.nextUUID(null), serialType, serialDate, operator, now);
        if (seq == null) {
            throw new IllegalStateException("单据编号生成失败，请重试");
        }
        String orderNo = serialType + serialDate + String.format(SEQ_FORMAT, seq);
        log.info("[出入库] 取号：{}（操作人={}）", orderNo, operator);
        return orderNo;
    }
}
