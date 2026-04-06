package com.jobpilot.jobpilot_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "scraperExecutor")
    public Executor scraperExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("scraper-");
        executor.setWaitForTasksToCompleteOnShutdown(false); // don't block shutdown for scrapes
        executor.initialize();
        return executor;
    }
}