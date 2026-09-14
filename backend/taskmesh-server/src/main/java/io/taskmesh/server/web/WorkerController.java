package io.taskmesh.server.web;

import io.taskmesh.server.service.JobService;
import io.taskmesh.server.service.WorkerRegistry;
import io.taskmesh.server.web.Dto.CompleteRequest;
import io.taskmesh.server.web.Dto.HeartbeatRequest;
import io.taskmesh.server.web.Dto.HeartbeatResponse;
import io.taskmesh.server.web.Dto.JobView;
import io.taskmesh.server.web.Dto.RegisterWorkerRequest;
import io.taskmesh.server.web.Dto.WorkerView;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {
    private final WorkerRegistry workers;
    private final JobService jobs;

    public WorkerController(WorkerRegistry workers, JobService jobs) {
        this.workers = workers;
        this.jobs = jobs;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterWorkerRequest req) {
        if (req.address() == null || req.address().isBlank()) {
            throw new ApiExceptions.BadRequestException("address is required");
        }
        if (req.totalCpu() < 1 || req.totalMemoryMb() < 1) {
            throw new ApiExceptions.BadRequestException("totalCpu and totalMemoryMb must be >= 1");
        }
        String id = workers.register(req.id(), req.address(), req.totalCpu(), req.totalMemoryMb());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PostMapping("/{id}/heartbeat")
    public HeartbeatResponse heartbeat(@PathVariable String id,
                                       @RequestBody HeartbeatRequest req) {
        return workers.heartbeat(id, req.availableCpu(), req.availableMemoryMb(), req.runningJobs());
    }

    @PostMapping("/{id}/jobs/{jobId}/complete")
    public JobView complete(@PathVariable String id, @PathVariable String jobId,
                            @RequestBody CompleteRequest req) {
        return jobs.complete(id, jobId, req.outcome(), req.error());
    }

    @GetMapping
    public List<WorkerView> list() {
        return workers.list();
    }
}
