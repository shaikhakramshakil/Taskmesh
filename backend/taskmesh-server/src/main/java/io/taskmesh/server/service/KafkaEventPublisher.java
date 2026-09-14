package io.taskmesh.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "taskmesh.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventPublisher implements EventPort {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void jobEvent(String type, String jobId, String detail) {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("jobId", jobId);
        event.put("at", Instant.now().toString());
        event.put("detail", detail == null ? "" : detail);
        send("taskmesh.jobs", jobId, event);
    }

    @Override
    public void opsEvent(String type, String detail) {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("at", Instant.now().toString());
        event.put("detail", detail == null ? "" : detail);
        send("taskmesh.ops", type, event);
    }

    private void send(String topic, String key, Map<String, String> event) {
        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Dropping {} event, cannot serialize: {}", topic, e.getMessage());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                kafka.send(topic, key, payload).get();
            } catch (Exception e) {
                log.warn("Failed to publish to {}: {}", topic, e.getMessage());
            }
        });
    }
}
