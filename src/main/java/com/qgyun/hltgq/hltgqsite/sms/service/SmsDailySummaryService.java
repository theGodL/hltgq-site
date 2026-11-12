package com.qgyun.hltgq.hltgqsite.sms.service;

import com.qgyun.hltgq.hltgqsite.sms.SmsProperties;
import com.qgyun.hltgq.hltgqsite.sms.mapper.SmsRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 水文日汇总短信（模板 SMS_489685013）：每日 08:01 发送昨日水文日汇总。
 * <p>口径（业主 2026-11 定稿）：
 * <ul>
 *   <li>雨量 = 坝上、坝下、周家河<b>三站平均</b>（各站取 TM &lt;= 今日 08:00 最新一条的 DRP，
 *       即上一水文日 [昨日08:00, 今日08:00] 完整累计；某站缺数据按 0 计，日志打三站原始值）；</li>
 *   <li>水位 = 三站 TM &lt;= 今日 08:00 最新一条 Z（绝不取 08:00 之后的数据——08:00 后属于下一水文日）；</li>
 *   <li>time = 昨日日期（水文日标签），hour 固定 8；</li>
 *   <li>接收人 = 原运维 msg.number8 的 30 人清单（配置 sms.receivers.daily）。</li>
 * </ul>
 * <p>缺数兜底：模板变量不允许空值（阿里云报参数非法），取不到的数据统一填"暂无"。
 */
@Service
public class SmsDailySummaryService {

    private static final Logger log = LoggerFactory.getLogger(SmsDailySummaryService.class);

    @Autowired
    private SmsProperties properties;

    @Autowired
    private SmsRecordMapper recordMapper;

    @Autowired
    private SmsSendService smsSendService;

    /**
     * 执行日汇总：组装三站雨量/水位并发送给日汇总接收人。
     * <p>定时任务 08:01 触发；失败（异常）只记日志，不抛给调度器（避免任务中断）。
     */
    public void runDailySummary() {
        if (!properties.isEnabled()) {
            log.info("短信总开关关闭（sms.enabled=false），跳过日汇总短信");
            return;
        }
        LocalDateTime endTm = LocalDate.now().atTime(8, 0);

        // 三站雨量（水文日累计）
        BigDecimal rainBashang = readRain(properties.getStationBashang(), endTm);
        BigDecimal rainBaxia = readRain(properties.getStationBaxia(), endTm);
        BigDecimal rainZhoujiahe = readRain(properties.getStationZhoujiahe(), endTm);
        String rainAvg = averageRain(rainBashang, rainBaxia, rainZhoujiahe);

        // 三站 08:00 前最新水位
        String levelBashang = readLevel(properties.getStationBashang(), endTm);
        String levelBaxia = readLevel(properties.getStationBaxia(), endTm);
        String levelZhoujiahe = readLevel(properties.getStationZhoujiahe(), endTm);

        Map<String, String> params = new HashMap<>();
        params.put("time", LocalDate.now().minusDays(1).toString());
        params.put("hour", "8");
        params.put("rain", rainAvg);
        params.put("bashang", levelBashang);
        params.put("baxia", levelBaxia);
        params.put("zhoujiahe", levelZhoujiahe);

        log.info("日汇总短信取数 endTm={} 雨量[坝上={}, 坝下={}, 周家河={}] 平均={} 水位[坝上={}, 坝下={}, 周家河={}]",
                endTm, rainBashang, rainBaxia, rainZhoujiahe, rainAvg,
                levelBashang, levelBaxia, levelZhoujiahe);

        smsSendService.sendToPhones(properties.getReceiversDaily(), "daily",
                properties.getTemplateDaily(), params, null, null);
    }

    /** 单站雨量（水文日累计）：TM &lt;= endTm 最新一条且属于该水文日；缺数据返回 null（按 0 计） */
    private BigDecimal readRain(String stcd, LocalDateTime endTm) {
        Map<String, Object> row = recordMapper.selectRainLatestUpTo(stcd, endTm);
        if (row == null) {
            log.warn("日汇总雨量缺数据：站号={} 截止时间={}（该水文日无上报行，按 0 计）", stcd, endTm);
            return null;
        }
        Object drp = row.get("drp");
        Object tm = row.get("tm");
        log.info("日汇总雨量：站号={} 实际取到 TM={} DRP={}", stcd, tm, drp);
        return drp == null ? null : new BigDecimal(String.valueOf(drp));
    }

    /** 单站水位：TM &lt;= endTm 最新一条；缺数据返回"暂无" */
    private String readLevel(String stcd, LocalDateTime endTm) {
        Map<String, Object> row = recordMapper.selectRiverLatestUpTo(stcd, endTm);
        if (row == null || row.get("z") == null) {
            log.warn("日汇总水位缺数据：站号={} 截止时间={}", stcd, endTm);
            return SmsSendService.EMPTY_TEXT;
        }
        Object tm = row.get("tm");
        BigDecimal z = new BigDecimal(String.valueOf(row.get("z"))).setScale(2, RoundingMode.HALF_UP);
        log.info("日汇总水位：站号={} 实际取到 TM={} Z={}", stcd, tm, z);
        return z.toPlainString();
    }

    /** 三站平均雨量（缺站按 0 计，分母固定 3）；三站全缺返回"暂无" */
    private String averageRain(BigDecimal bashang, BigDecimal baxia, BigDecimal zhoujiahe) {
        if (bashang == null && baxia == null && zhoujiahe == null) {
            return SmsSendService.EMPTY_TEXT;
        }
        BigDecimal sum = BigDecimal.ZERO;
        if (bashang != null) {
            sum = sum.add(bashang);
        }
        if (baxia != null) {
            sum = sum.add(baxia);
        }
        if (zhoujiahe != null) {
            sum = sum.add(zhoujiahe);
        }
        return sum.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP).toPlainString();
    }
}
