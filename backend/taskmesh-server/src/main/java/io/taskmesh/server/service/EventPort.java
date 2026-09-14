package io.taskmesh.server.service;

public interface EventPort {
    void jobEvent(String type, String jobId, String detail);
    void opsEvent(String type, String detail);
}
