package com.forge.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Value("${app.threadpool.core-pool-size:10}")
    private int corePoolSize;

    @Value("${app.threadpool.max-pool-size:25}")
    private int maxPoolSize;

    @Value("${app.threadpool.queue-capacity:500}")
    private int queueCapacity;

    @Value("${app.threadpool.thread-name-prefix:OrderWorker-}")
    private String threadNamePrefix;

    @Bean(name = "orderProcessingExecutor")
    public Executor orderProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // Caller runs policy if queue is full to apply natural backpressure
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
