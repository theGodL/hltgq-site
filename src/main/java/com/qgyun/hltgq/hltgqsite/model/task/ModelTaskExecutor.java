package com.qgyun.hltgq.hltgqsite.model.task;

import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 模型任务单线程串行执行器。
 * <p>模型服务侧全局锁串行执行、禁止高并发，本执行器保证同一时刻仅一个模型计算任务在跑，
 * 其余任务排队；排队任务落库状态仍为 calculating（排队中），前端轮询 status 即可。
 * <p>任务内状态流转由各模块服务负责：calculating → completed / failed(+error_msg)。
 */
@Component
public class ModelTaskExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "model-task-executor");
        thread.setDaemon(true);
        return thread;
    });

    /** 提交模型计算任务（异步执行），返回 Future 供上层按需等待 */
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
