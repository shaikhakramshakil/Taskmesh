package io.taskmesh.server.config;

import io.taskmesh.domain.scheduling.DeadlineScheduler;
import io.taskmesh.domain.scheduling.FIFOScheduler;
import io.taskmesh.domain.scheduling.FairScheduler;
import io.taskmesh.domain.scheduling.PriorityScheduler;
import io.taskmesh.domain.scheduling.ResourceAwareScheduler;
import io.taskmesh.domain.scheduling.SchedulingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StrategiesConfig {

    @Bean
    public SchedulingStrategy fifoStrategy() {
        return new FIFOScheduler();
    }

    @Bean
    public SchedulingStrategy priorityStrategy() {
        return new PriorityScheduler();
    }

    @Bean
    public SchedulingStrategy fairStrategy() {
        return new FairScheduler();
    }

    @Bean
    public SchedulingStrategy resourceStrategy() {
        return new ResourceAwareScheduler(System::currentTimeMillis);
    }

    @Bean
    public SchedulingStrategy deadlineStrategy() {
        return new DeadlineScheduler();
    }
}
