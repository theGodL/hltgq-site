package com.qgyun.hltgq.hltgqsite.sms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
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
import java.util.List;
import java.util.Map;

/**
 * 水文日汇总短信（模板 SMS_489685013）：每日 08:01 发送昨日水文日汇总。
 * <p>口径（2026-11-13 依据业主老系统"时段雨情"导出数据逐行对账后定稿，完全复刻老系统公式）：
 * <ul>
 *   <li>雨量 = 320640 系列<b>全部 12 个雨量站</b>（档案监测类型含 #2#）上一水文日雨量的算术平均；
 *       单站雨量 = DYP 正向增量累加 Σmax(0, DYP_i − DYP_{i−1})（该表 DRP 实测恒为 0，不可用；
 *       坝下站 320640000A 档案无雨量监测，本来就不参与）；</li>
 *   <li><b>平均 = 全站日雨量之和 ÷ 12，四舍五入保留两位</b>——老系统实证：页面行平均 2.0/12=0.1667 显示
 *       0.17、4.5/12=0.375 显示 0.38（四舍五入而非截断）；缺报文站老系统按 0 参与、分母固定 12 站不变；
 *       对账实例：11-12 水文日 12 站合计 56.0，56.0/12=4.6667→4.67（老系统短信 4.63 = 55.5/12 的
 *       四舍五入结果，因老系统 08:01 结算时 13 日 08:00 整点报文尚缺一条 0.5，数据到齐后应同为 4.67）；</li>
 *   <li>水位 = 坝上/坝下/周家河三站 TM &lt;= 今日 08:00 最新一条 Z（绝不取 08:00 之后的数据——08:00 后属于下一水文日）；</li>
 *   <li>time = 昨日日期（水文日标签），hour 固定 8；</li>
 *   <li>接收人 = 原运维 msg.number8 的 30 人清单 + 项目经理 + 研发负责人（配置 sms.receivers.daily）。</li>
 * </ul>
 * <p><b>取数策略：宁可少发，绝不发错误短信</b>——短信变量不允许出现"暂无"：
 * 12 站 08:00 整点报文全缺（数据链路异常，此时"0.00"是假数据）、到报不足半数（缺站按 0 平均严重偏低）、
 * 或任一站水位缺数时不发送，落 skipped 记录（response_code=NO_DATA）供运维排查；
 * 缺站按 0 参与平均仅适用于"多数站已到报文"的老系统口径（此时其余站数据可信）。
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

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 调度入口（08:01 触发）：<b>先等 08:00 整点报文到齐（最迟等到 08:15），再组装发送</b>。
     * <p>为什么等：2026-11-13 实测 12 站 08:00 整点报文从 08:00:03 陆续入库到 08:06:29，
     * 老系统 08:02~08:05 结算时缺一条 0.5（4.63=55.5/12）；等齐后发送 = 数据完整值，与老系统
     * 公式一致而数值更准，且避免我们 08:01 结算时缺更多（实测 08:01 仅 4 站到库）。
     * <p>失败（异常）只记日志，不抛给调度器（避免任务中断）。
     */
    public void runDailySummary() {
        if (!properties.isEnabled()) {
            log.info("短信总开关关闭（sms.enabled=false），跳过日汇总短信");
            return;
        }
        LocalDateTime endTm = LocalDate.now().atTime(8, 0);
        int ready = waitHourlyReportsReady(endTm);
        sendDailySummary(endTm, ready);
    }

    /**
     * 手动入口（POST /sms/daily/send）：不等待报文，立即按当前库内数据执行（部署验证/补发用）。
     * <p>同样执行"取数不全不发送"校验（12 站整点报文全缺或水位缺数时拒发）。
     */
    public void sendDailySummaryNow() {
        if (!properties.isEnabled()) {
            log.info("短信总开关关闭（sms.enabled=false），跳过日汇总短信");
            return;
        }
        LocalDateTime endTm = LocalDate.now().atTime(8, 0);
        sendDailySummary(endTm, recordMapper.countRainStationsWithReportAt(endTm));
    }

    /**
     * 等待 08:00 整点报文齐：每 20 秒查一次"已入库整点报文的雨量站数"，齐了立即返回；
     * 最迟等到 endTm + 15 分钟（含超时日志，缺站后续按 0 参与平均，不阻断发送）。
     * <p>若当前时间已过截止点（服务重启补跑等），不等待直接返回。
     *
     * @return 已入库整点报文的雨量站数（0 = 数据链路完全异常，调用方据此拒发防假 0）
     */
    private int waitHourlyReportsReady(LocalDateTime endTm) {
        int expected = recordMapper.countRainStations();
        if (expected <= 0) {
            log.warn("日汇总等待报文：雨量站档案为空，跳过等待");
            return 0;
        }
        LocalDateTime deadline = endTm.plusMinutes(15);
        int ready = recordMapper.countRainStationsWithReportAt(endTm);
        while (ready < expected && LocalDateTime.now().isBefore(deadline)) {
            log.info("日汇总等待 08:00 整点报文：已到 {}/{} 站，20 秒后重查", ready, expected);
            try {
                Thread.sleep(20_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("日汇总等待报文被中断，按当前数据直接发送");
                return ready;
            }
            ready = recordMapper.countRainStationsWithReportAt(endTm);
        }
        if (ready >= expected) {
            log.info("日汇总 08:00 整点报文已齐（{}/{} 站），开始取数发送", ready, expected);
        } else {
            log.warn("日汇总等待报文超时（截止 {}），仍有 {}/{} 站未入库，缺站按 0 参与平均", deadline, expected - ready, expected);
        }
        return ready;
    }

    /**
     * 组装"平均雨量 + 三站 08:00 水位"并发送给日汇总接收人。
     * <p><b>取数不全一律不发送</b>（宁可少发，绝不发错误短信）：
     * ready=0（12 站整点报文全缺，雨量"0.00"必为假数据）或任一站水位缺数 → 落 skipped 记录（NO_DATA）。
     */
    private void sendDailySummary(LocalDateTime endTm, int ready) {
        String dayLabel = LocalDate.now().minusDays(1).toString();
        if (ready <= 0) {
            log.error("日汇总短信不发送：12 站今日 08:00 整点报文全缺（数据链路异常），雨量不可信 endTm={}", endTm);
            saveNoDataSkipRecord(dayLabel, "12 站 08:00 整点报文全缺，雨量不可信，未发送");
            return;
        }
        // 到报不足半数同样拒发：少量站到报文时其余站按 0 参与平均会严重偏低（如 3/12 站到报文，
        // 均值只剩真实值的 1/4），属于"公式对但数据错"的错误短信——宁可少发不误发。
        // 正常日 08:00 报文在 08:06 前 12/12 齐，不会触发此分支。
        int expectedStations = recordMapper.countRainStations();
        if (expectedStations > 0 && ready * 2 < expectedStations) {
            log.error("日汇总短信不发送：08:00 整点报文仅到 {}/{} 站（不足半数，缺站按 0 平均会严重失真），宁可少发不误发",
                    ready, expectedStations);
            saveNoDataSkipRecord(dayLabel, "到报站数不足半数（" + ready + "/" + expectedStations + "），未发送");
            return;
        }
        LocalDateTime baseTm = endTm.minusDays(1);

        // 雨量：320640 全雨量站 DYP 差值平均（对齐业主老系统"平均雨量"口径）
        String rainAvg = averageHydroDayRain(baseTm, endTm);

        // 三站 08:00 前最新水位
        String levelBashang = readLevel(properties.getStationBashang(), endTm);
        String levelBaxia = readLevel(properties.getStationBaxia(), endTm);
        String levelZhoujiahe = readLevel(properties.getStationZhoujiahe(), endTm);

        if (rainAvg == null || levelBashang == null || levelBaxia == null || levelZhoujiahe == null) {
            log.error("日汇总短信不发送：取数不全（雨量={} 坝上={} 坝下={} 周家河={}），宁可少发不误发",
                    rainAvg, levelBashang, levelBaxia, levelZhoujiahe);
            saveNoDataSkipRecord(dayLabel, "取数不全未发送（雨量=" + rainAvg + " 坝上=" + levelBashang
                    + " 坝下=" + levelBaxia + " 周家河=" + levelZhoujiahe + "）");
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("time", dayLabel);
        params.put("hour", "8");
        params.put("rain", rainAvg);
        params.put("bashang", levelBashang);
        params.put("baxia", levelBaxia);
        params.put("zhoujiahe", levelZhoujiahe);

        log.info("日汇总短信取数 endTm={} 到报站数={} 雨量(全站DYP差值平均)={} 水位[坝上={}, 坝下={}, 周家河={}]",
                endTm, ready, rainAvg, levelBashang, levelBaxia, levelZhoujiahe);

        smsSendService.sendToPhones(properties.getReceiversDaily(), "daily",
                properties.getTemplateDaily(), params, null, null);
    }

    /**
     * 未发送落痕（send_status='skipped'，phone 占位 '-'）：让运维在发送记录页能看到
     * "某天为什么没收到日汇总短信"，response_code=NO_DATA 标识取数不全。
     */
    private void saveNoDataSkipRecord(String dayLabel, String reason) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> row = new HashMap<>();
            row.put("id", shortIdGenerator.nextUUID(null));
            row.put("biz_type", "daily");
            row.put("alert_id", null);
            row.put("silence_key", null);
            row.put("phone", "-");
            row.put("template_code", properties.getTemplateDaily());
            String paramsJson;
            try {
                paramsJson = objectMapper.writeValueAsString(
                        java.util.Collections.singletonMap("skipDay", dayLabel));
            } catch (Exception e) {
                paramsJson = "{\"skipDay\":\"" + dayLabel + "\"}";
            }
            row.put("template_params", paramsJson);
            row.put("send_status", "skipped");
            row.put("request_id", null);
            row.put("biz_id", null);
            row.put("response_code", "NO_DATA");
            row.put("response_message", reason);
            row.put("sent_at", null);
            row.put("now", now);
            recordMapper.insertRecord(row);
        } catch (Exception e) {
            log.error("日汇总未发送记录落库失败 day={} err={}", dayLabel, e.toString());
        }
    }

    /**
     * 上一水文日平均雨量（完全复刻老系统公式）：320640 全雨量站逐站"正向增量累加"
     * Σ max(0, DYP_i − DYP_{i−1})（基线行 = 昨日 08:00 前最新一条）；
     * 平均 = 全站之和 ÷ 站数（分母固定为查询返回的 12 站，缺报文站按 0 参与——老系统页面空值同样按 0 计），
     * 结果四舍五入保留两位（老系统实证为四舍五入：0.1667→0.17、0.375→0.38）；
     * 全站无行（档案查询为空）返回 null（调用方拒发）。
     */
    private String averageHydroDayRain(LocalDateTime baseTm, LocalDateTime endTm) {
        List<Map<String, Object>> rows = recordMapper.selectHydroDayRainAllStations(baseTm, endTm);
        BigDecimal sum = BigDecimal.ZERO;
        int stations = 0;
        for (Map<String, Object> row : rows) {
            Object stcd = getIgnoreCase(row, "stcd");
            Object stnm = getIgnoreCase(row, "stnm");
            BigDecimal rain = toDecimal(getIgnoreCase(row, "rain"));
            if (rain == null) {
                // 老系统口径：缺报文站按 0 参与平均，分母不剔除
                log.warn("日汇总雨量缺数：站号={} 站名={}（按 0 参与平均，分母不变）", stcd, stnm);
                rain = BigDecimal.ZERO;
            }
            sum = sum.add(rain);
            stations++;
            log.info("日汇总雨量：站号={} 站名={} 报文数={} 雨量={}",
                    stcd, stnm, getIgnoreCase(row, "rowsCnt"), rain.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (stations == 0) {
            log.error("日汇总雨量取数为空（320640 雨量站档案未查到任何站），返回 null 由调用方拒发");
            return null;
        }
        // 老系统公式：平均值四舍五入保留两位（对账依据：页面行平均 0.1667→0.17、0.375→0.38）
        String avg = sum.divide(BigDecimal.valueOf(stations), 2, RoundingMode.HALF_UP).toPlainString();
        log.info("日汇总雨量：站数={} 合计={} 平均(四舍五入两位)={}", stations, sum.setScale(2, RoundingMode.HALF_UP).toPlainString(), avg);
        return avg;
    }

    /** 结果集 Map 键大小写不敏感取值（KingbaseES 别名大小写随驱动返回不定） */
    private static Object getIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 单站水位：TM &lt;= endTm 最新一条；缺数据返回 null（调用方拒发，绝不发"暂无"） */
    private String readLevel(String stcd, LocalDateTime endTm) {
        Map<String, Object> row = recordMapper.selectRiverLatestUpTo(stcd, endTm);
        if (row == null || row.get("z") == null) {
            log.error("日汇总水位缺数据：站号={} 截止时间={}", stcd, endTm);
            return null;
        }
        Object tm = row.get("tm");
        BigDecimal z = new BigDecimal(String.valueOf(row.get("z"))).setScale(2, RoundingMode.DOWN);
        log.info("日汇总水位：站号={} 实际取到 TM={} Z={}", stcd, tm, z);
        return z.toPlainString();
    }

    private static BigDecimal toDecimal(Object v) {
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }
}
