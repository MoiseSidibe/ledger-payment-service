package com.alpian.ledger.payment.config;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.alpian.ledger.payment.service.OutboxEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@Slf4j
public class DbSchedulerManualConfig {

    @Bean
    public RecurringTask<Void> outboxProcessingTask(OutboxEventService outboxEventService) {
        return Tasks
                .recurring("process-outbox-events", FixedDelay.ofSeconds(2))
                .execute((instance, context) -> {
                    outboxEventService.processOutboxEvents();
                });
    }

    @Bean
    @ConditionalOnMissingBean(Scheduler.class)
    public Scheduler scheduler(DataSource dataSource, RecurringTask<Void> outboxProcessingTask) {
        Scheduler scheduler = Scheduler
                .create(dataSource, outboxProcessingTask)
                .pollingInterval(Duration.ofSeconds(2))
                .threads(10)
                .heartbeatInterval(Duration.ofMinutes(1))
                .tableName("scheduled_tasks")
                .registerShutdownHook()
                .build();
        scheduler.start();
        scheduler.schedule(outboxProcessingTask.schedulableInstance("singleton"));
        log.info("db-scheduler started with outbox processing task registered (polling every 2 seconds)");
        return scheduler;
    }
}

