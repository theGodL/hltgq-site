package com.qgyun.hltgq.hltgqsite.sms.task;

import com.qgyun.hltgq.hltgqsite.sms.service.SmsDailySummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日汇总短信定时任务：每天 08:01（cron 配置 sms.daily.cron）发早 8 点报，
 * 每天 20:00（cron 配置 sms.evening.cron）发晚 8 点报。
 * <p>为什么 08:01 触发：数据口径上界严格 08:00:00（08:00 后属于下一水文日，绝不取用）。
 * <p><b>触发后不在 08:01 结算，而是等 08:00 整点报文到齐再发（Service 内轮询，最迟 08:15）</b>：
 * 实测 12 站 08:00 整点报文从 08:00:03 陆续入库到 08:06:29，老系统 08:02~08:05 结算时缺一条
 * 0.5（4.63=55.5/12）；等齐后发送 = 与老系统公式一致且取完整数据值。
 * <p><b>晚 8 点报（20:00）内容与早 8 点报完全一致</b>：同一个模板与参数、数据截止仍是今日 08:00，
 * 不发两次不同的数据，只是接收人取 evening 清单；20:00 时整点报文早已入库，不再等报文。
 * <p>异常只记日志不外抛，避免调度线程中断后续触发。
 */
@Component
public class SmsDailySummaryTask {

    private static final Logger log = LoggerFactory.getLogger(SmsDailySummaryTask.class);

    @Autowired
    private SmsDailySummaryService smsDailySummaryService;

    @Scheduled(cron = "${sms.daily.cron:0 1 8 * * ?}")
    public void runDailySummary() {
        log.info("日汇总短信任务开始");
        try {
            smsDailySummaryService.runDailySummary();
            log.info("日汇总短信任务结束");
        } catch (Exception e) {
            log.error("日汇总短信任务异常", e);
        }
    }

    /** 晚 8 点报（20:00）：复用日汇总模板与取数，接收人取 evening 清单 */
    @Scheduled(cron = "${sms.evening.cron:0 0 20 * * ?}")
    public void runEveningSummary() {
        log.info("晚8点报短信任务开始");
        try {
            smsDailySummaryService.runEveningSummary();
            log.info("晚8点报短信任务结束");
        } catch (Exception e) {
            log.error("晚8点报短信任务异常", e);
        }
    }
}
