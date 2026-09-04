package com.qgyun.hltgq.hltgqsite.model.task;

import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.MoistureRecordMapper;
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
    private final MoistureRecordMapper moistureRecordMapper;
    private final int staleCalculatingMinutes;

    public ModelTaskStartupRecovery(ShortForecastRecordMapper shortForecastRecordMapper,
                                    LongPredictRecordMapper longPredictRecordMapper,
                                    DemandRecordMapper demandRecordMapper,
                                    LossRecordMapper lossRecordMapper,
                                    AllocateRecordMapper allocateRecordMapper,
                                    DecisionRecordMapper decisionRecordMapper,
                                    MoistureRecordMapper moistureRecordMapper,
                                    @Value("${model.task.stale-calculating-minutes:15}") int staleCalculatingMinutes) {
        this.shortForecastRecordMapper = shortForecastRecordMapper;
        this.longPredictRecordMapper = longPredictRecordMapper;
        this.demandRecordMapper = demandRecordMapper;
        this.lossRecordMapper = lossRecordMapper;
        this.allocateRecordMapper = allocateRecordMapper;
        this.decisionRecordMapper = decisionRecordMapper;
        this.moistureRecordMapper = moistureRecordMapper;
        this.staleCalculatingMinutes = staleCalculatingMinutes;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(staleCalculatingMinutes);
        LocalDateTime now = LocalDateTime.now();
        String errorMsg = "服务重启，任务中断";
        int total = 0;
        total += cleanQuietly("短期预报", () -> shortForecastRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        total += cleanQuietly("中长期预报", () -> longPredictRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        total += cleanQuietly("需水预测", () -> demandRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        total += cleanQuietly("水量损失", () -> lossRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        total += cleanQuietly("水资源配置", () -> allocateRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        total += cleanQuietly("配水调度", () -> decisionRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        total += cleanQuietly("墒情预测", () -> moistureRecordMapper.markStaleCalculatingFailed(deadline, errorMsg, now));
        if (total > 0) {
            log.info("启动清理：{} 条遗留 calculating 方案已置为 failed", total);
        }
    }

    /**
     * 单表清理容错：任一模块主表 DDL 未跟上（表不存在）或库异常时仅告警跳过，
     * 不得阻断应用启动（历史教训：moisture 表 DDL 缺失曾导致 ApplicationRunner 抛异常、全站 502）。
     */
    private int cleanQuietly(String module, java.util.function.IntSupplier cleaner) {
        try {
            return cleaner.getAsInt();
        } catch (Exception e) {
            log.warn("启动清理跳过[{}]：{}（表 DDL 未执行或数据库异常，不影响其他模块）",
                    module, rootMessage(e));
            return 0;
        }
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
