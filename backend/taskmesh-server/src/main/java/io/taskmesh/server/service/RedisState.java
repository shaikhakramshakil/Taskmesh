package io.taskmesh.server.service;

import java.util.Optional;

public interface RedisState {
    void syncDepth(long depth);
    Optional<Long> depth();
    void heartbeat(String workerId);
}
