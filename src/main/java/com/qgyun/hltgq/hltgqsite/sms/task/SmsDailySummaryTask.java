package com.qgyun.hltgq.hltgqsite.sms.task;

import com.qgyun.hltgq.hltgqsite.sms.service.SmsDailySummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日汇总短信定时任务：每天 08:01（cron 配置 sms.daily.cron）。
 * <p>为什么 08:01：数据口径上界严格 08:00:00（08:00 后属于下一水文日，绝不取用）；
 * 01 分钟留给 08:00 整点数据入库的窗口。若某站 08:00 数据尚未入库，
 * 取该站 08:00 前最后一条并在日志记录实际 TM（数据层兜底）。
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
}
