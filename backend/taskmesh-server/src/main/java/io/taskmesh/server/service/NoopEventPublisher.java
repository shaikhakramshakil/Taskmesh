package io.taskmesh.server.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "taskmesh.kafka", name = "enabled", havingValue = "false")
public class NoopEventPublisher implements EventPort {
    @Override
    public void jobEvent(String type, String jobId, String detail) {
    }

    @Override
    public void opsEvent(String type, String detail) {
    }
}
