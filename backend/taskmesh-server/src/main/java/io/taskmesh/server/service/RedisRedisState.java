package io.taskmesh.server.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "taskmesh.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisRedisState implements RedisState {
    private static final Logger log = LoggerFactory.getLogger(RedisRedisState.class);

    private final StringRedisTemplate redis;

    public RedisRedisState(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void syncDepth(long depth) {
        try {
            redis.opsForValue().set("taskmesh:queue:depth", Long.toString(depth));
        } catch (Exception e) {
            log.warn("Redis depth sync failed: {}", e.getMessage());
        }
    }

    @Override
    public Optional<Long> depth() {
        try {
            String raw = redis.opsForValue().get("taskmesh:queue:depth");
            return raw == null ? Optional.empty() : Optional.of(Long.parseLong(raw));
        } catch (Exception e) {
            log.warn("Redis depth read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void heartbeat(String workerId) {
        try {
            redis.opsForValue().set("taskmesh:worker:" + workerId + ":heartbeat",
                    Instant.now().toString(), Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Redis heartbeat write failed: {}", e.getMessage());
        }
    }
}
