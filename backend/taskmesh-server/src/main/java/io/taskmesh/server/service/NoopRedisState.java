package io.taskmesh.server.service;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "taskmesh.redis", name = "enabled", havingValue = "false")
public class NoopRedisState implements RedisState {
    @Override
    public void syncDepth(long depth) {
    }

    @Override
    public Optional<Long> depth() {
        return Optional.empty();
    }

    @Override
    public void heartbeat(String workerId) {
    }
}
