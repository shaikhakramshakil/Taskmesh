package io.taskmesh.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkerAgentTest {
    private final List<WorkerAgent> agents = new ArrayList<>();

    @AfterEach
    void shutdownAgents() {
        for (WorkerAgent agent : agents) {
            try {
                agent.close();
            } catch (Exception ignored) {
                // best effort cleanup
            }
        }
        agents.clear();
    }

    @Test
    void durationFormulaBounds() {
        assertEquals(500 + 2 * 250 + 4096 / 512, WorkerAgent.computeDurationMs(2, 4096));
        assertEquals(1008, WorkerAgent.computeDurationMs(2, 4096));
        assertTrue(WorkerAgent.computeDurationMs(1, 512) >= 500);
        assertTrue(
                WorkerAgent.computeDurationMs(8, 16384) > WorkerAgent.computeDurationMs(1, 512));
    }

    @Test
    void availabilityAccounting() throws Exception {
        WorkerProperties props = props("w-avail", 2, 4096, 0.0);
        ServerClient client = mock(ServerClient.class);
        when(client.register(eq("w-avail"), any(), eq(2), eq(4096L))).thenReturn("w-avail");
        ServerClient.JobAssignment assignment =
                new ServerClient.JobAssignment("job-1", "n", 5, 1, 1024, null);
        ServerClient.HeartbeatResponse withJob =
                new ServerClient.HeartbeatResponse("HEALTHY", List.of(assignment), List.of());
        ServerClient.HeartbeatResponse empty =
                new ServerClient.HeartbeatResponse("HEALTHY", List.of(), List.of());
        when(client.heartbeat(eq("w-avail"), anyInt(), anyLong(), anyInt()))
                .thenReturn(withJob, empty);

        WorkerAgent agent = new WorkerAgent(props, client);
        agents.add(agent);
        agent.run(null);
        agent.heartbeatOnce();
        agent.heartbeatOnce();

        ArgumentCaptor<Integer> cpu = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> mem = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> running = ArgumentCaptor.forClass(Integer.class);
        verify(client, atLeast(2)).heartbeat(eq("w-avail"), cpu.capture(), mem.capture(), running.capture());

        List<Integer> cpus = cpu.getAllValues();
        List<Long> mems = mem.getAllValues();
        List<Integer> runnings = running.getAllValues();
        assertEquals(2, cpus.get(0));
        assertEquals(1, cpus.get(cpus.size() - 1));
        assertEquals(4096L, mems.get(0));
        assertEquals(3072L, mems.get(mems.size() - 1));
        assertEquals(0, runnings.get(0));
        assertEquals(1, runnings.get(runnings.size() - 1));
    }

    @Test
    void cancelAbortsRunningJob() throws Exception {
        WorkerProperties props = props("w-cancel", 4, 32768, 0.0);
        ServerClient client = mock(ServerClient.class);
        when(client.register(eq("w-cancel"), any(), eq(4), eq(32768L))).thenReturn("w-cancel");
        ServerClient.JobAssignment assignment =
                new ServerClient.JobAssignment("job-9", "n", 5, 4, 16384, null);
        long fullDurationMs = WorkerAgent.computeDurationMs(4, 16384);
        ServerClient.HeartbeatResponse withJob =
                new ServerClient.HeartbeatResponse("HEALTHY", List.of(assignment), List.of());
        ServerClient.HeartbeatResponse cancel =
                new ServerClient.HeartbeatResponse("HEALTHY", List.of(), List.of("job-9"));
        when(client.heartbeat(eq("w-cancel"), anyInt(), anyLong(), anyInt()))
                .thenReturn(withJob, cancel);

        WorkerAgent agent = new WorkerAgent(props, client);
        agents.add(agent);
        agent.run(null);
        long start = System.nanoTime();
        agent.heartbeatOnce();
        agent.heartbeatOnce();

        verify(client, timeout(5000))
                .complete(eq("w-cancel"), eq("job-9"), eq("CANCELLED"), any());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(
                elapsedMs < fullDurationMs,
                "cancel should abort before the full duration: " + elapsedMs + " < " + fullDurationMs);
    }

    @Test
    void heartbeat404TriggersReregister() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger registers = new AtomicInteger();
        AtomicInteger beats = new AtomicInteger();
        server.createContext(
                "/api/v1/workers/register",
                exchange -> {
                    registers.incrementAndGet();
                    exchange.getRequestBody().readAllBytes();
                    byte[] body = "{\"id\":\"w1\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.createContext(
                "/api/v1/workers/w1/heartbeat",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    int n = beats.incrementAndGet();
                    byte[] body;
                    int code;
                    if (n == 1) {
                        code = 404;
                        body =
                                "{\"error\":\"NOT_FOUND\",\"message\":\"unknown worker\"}"
                                        .getBytes(StandardCharsets.UTF_8);
                    } else {
                        code = 200;
                        body =
                                "{\"health\":\"HEALTHY\",\"assignments\":[],\"cancelIds\":[]}"
                                        .getBytes(StandardCharsets.UTF_8);
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(code, body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WorkerProperties props = props("w1", 2, 4096, 0.0);
            WorkerAgent agent = new WorkerAgent(props, new ServerClient("http://127.0.0.1:" + port));
            agents.add(agent);
            agent.run(null);
            assertEquals(1, registers.get());
            agent.heartbeatOnce();
            assertEquals(2, registers.get());
            assertEquals(2, beats.get());
        } finally {
            server.stop(0);
        }
    }

    private static WorkerProperties props(String id, int cpu, long memMb, double failureRate) {
        WorkerProperties props = new WorkerProperties();
        props.setId(id);
        props.setCpu(cpu);
        props.setMemoryMb(memMb);
        props.setFailureRate(failureRate);
        return props;
    }
}
