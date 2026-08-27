package com.qgyun.hltgq.hltgqsite.model.task;

import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动清理：将遗留的 calculating 记录（超过 model.task.stale-calculating-minutes 分钟）置为 failed。
 * <p>防止服务重启后历史方案永久停留在"计算中"，前端轮询永不结束。
 */
@Component
public class ModelTaskStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelTaskStartupRecovery.class);

    private final ShortForecastRecordMapper shortForecastRecordMapper;
    private final LongPredictRecordMapper longPredictRecordMapper;
    private final DemandRecordMapper demandRecordMapper;
    private final LossRecordMapper lossRecordMapper;
    private final AllocateRecordMapper allocateRecordMapper;
    private final DecisionRecordMapper decisionRecordMapper;
    private final int staleCalculatingMinutes;

    public ModelTaskStartupRecovery(ShortForecastRecordMapper shortForecastRecordMapper,
                                    LongPredictRecordMapper longPredictRecordMapper,
                                    DemandRecordMapper demandRecordMapper,
                                    LossRecordMapper lossRecordMapper,
                                    AllocateRecordMapper allocateRecordMapper,
                                    DecisionRecordMapper decisionRecordMapper,
                                    @Value("${model.task.stale-calculating-minutes:15}") int staleCalculatingMinutes) {
        this.shortForecastRecordMapper = shortForecastRecordMapper;
        this.longPredictRecordMapper = longPredictRecordMapper;
        this.demandRecordMapper = demandRecordMapper;
        this.lossRecordMapper = lossRecordMapper;
        this.allocateRecordMapper = allocateRecordMapper;
        this.decisionRecordMapper = decisionRecordMapper;
        this.staleCalculatingMinutes = staleCalculatingMinutes;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(staleCalculatingMinutes);
        LocalDateTime now = LocalDateTime.now();
        String errorMsg = "服务重启，任务中断";
        int total = 0;
        total += shortForecastRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now);
        total += longPredictRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now);
        total += demandRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now);
        total += lossRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now);
        total += allocateRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now);
        total += decisionRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now);
        if (total > 0) {
            log.info("启动清理：{} 条遗留 calculating 方案已置为 failed", total);
        }
    }
}
