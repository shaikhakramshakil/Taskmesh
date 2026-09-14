package io.taskmesh.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

/** Minimal JDK-HttpClient talker for the server worker endpoints. */
public class ServerClient {
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    /** Primary wiring (see WorkerConfig); the single-arg test seam needs no Spring. */
    public ServerClient(
            @Value("${taskmesh.server-url:http://localhost:8080}") String baseUrl,
            ObjectMapper mapper) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = base;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = mapper;
    }

    /** Test seam: plain mapper, no Spring needed. */
    public ServerClient(String baseUrl) {
        this(baseUrl, new ObjectMapper());
    }

    /** Register and return the authoritative worker id (echoed or generated). */
    public String register(String id, String address, int totalCpu, long totalMemoryMb) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("address", address);
        body.put("totalCpu", totalCpu);
        body.put("totalMemoryMb", totalMemoryMb);
        String resp = post("/api/v1/workers/register", body);
        try {
            RegisterResponse parsed = mapper.readValue(resp, RegisterResponse.class);
            return parsed.getId() == null || parsed.getId().isBlank() ? id : parsed.getId();
        } catch (IOException e) {
            throw new IllegalStateException("unparseable register response: " + resp, e);
        }
    }

    /**
     * Heartbeat with true availability. Throws {@link WorkerNotFoundException} on 404 so the
     * caller can re-register.
     */
    public HeartbeatResponse heartbeat(
            String workerId, int availableCpu, long availableMemoryMb, int runningJobs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("availableCpu", availableCpu);
        body.put("availableMemoryMb", availableMemoryMb);
        body.put("runningJobs", runningJobs);
        String resp = post("/api/v1/workers/" + workerId + "/heartbeat", body);
        try {
            HeartbeatResponse parsed = mapper.readValue(resp, HeartbeatResponse.class);
            parsed.normalize();
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("unparseable heartbeat response: " + resp, e);
        }
    }

    public void complete(String workerId, String jobId, String outcome, String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", outcome);
        if (error != null) {
            body.put("error", error);
        }
        post("/api/v1/workers/" + workerId + "/jobs/" + jobId + "/complete", body);
    }

    private String post(String path, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                throw new WorkerNotFoundException("POST " + path + " -> 404: " + resp.body());
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException(
                        "POST " + path + " -> " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body() == null ? "" : resp.body();
        } catch (WorkerNotFoundException | IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("POST " + path + " interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("POST " + path + " failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterResponse {
        private String id;

        public RegisterResponse() {}

        public RegisterResponse(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobAssignment {
        private String jobId;
        private String name;
        private int priority;
        private int cpu;
        private long memoryMb;
        private String deadline;

        public JobAssignment() {}

        public JobAssignment(
                String jobId, String name, int priority, int cpu, long memoryMb, String deadline) {
            this.jobId = jobId;
            this.name = name;
            this.priority = priority;
            this.cpu = cpu;
            this.memoryMb = memoryMb;
            this.deadline = deadline;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public int getCpu() {
            return cpu;
        }

        public void setCpu(int cpu) {
            this.cpu = cpu;
        }

        public long getMemoryMb() {
            return memoryMb;
        }

        public void setMemoryMb(long memoryMb) {
            this.memoryMb = memoryMb;
        }

        public String getDeadline() {
            return deadline;
        }

        public void setDeadline(String deadline) {
            this.deadline = deadline;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HeartbeatResponse {
        private String health;
        private List<JobAssignment> assignments;
        private List<String> cancelIds;

        public HeartbeatResponse() {}

        public HeartbeatResponse(String health, List<JobAssignment> assignments, List<String> cancelIds) {
            this.health = health;
            this.assignments = assignments;
            this.cancelIds = cancelIds;
        }

        public String getHealth() {
            return health;
        }

        public void setHealth(String health) {
            this.health = health;
        }

        public List<JobAssignment> getAssignments() {
            return assignments;
        }

        public void setAssignments(List<JobAssignment> assignments) {
            this.assignments = assignments;
        }

        public List<String> getCancelIds() {
            return cancelIds;
        }

        public void setCancelIds(List<String> cancelIds) {
            this.cancelIds = cancelIds;
        }

        void normalize() {
            if (assignments == null) {
                assignments = List.of();
            }
            if (cancelIds == null) {
                cancelIds = List.of();
            }
            if (health == null) {
                health = "HEALTHY";
            }
        }
    }
}
