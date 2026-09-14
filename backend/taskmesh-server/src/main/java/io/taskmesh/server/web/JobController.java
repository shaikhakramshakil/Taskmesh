package io.taskmesh.server.web;

import io.taskmesh.server.service.JobService;
import io.taskmesh.server.web.Dto.JobView;
import io.taskmesh.server.web.Dto.SubmitJobRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private static final java.util.Set<String> STATUSES =
            java.util.Set.of("QUEUED", "ASSIGNED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED", "REJECTED");

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    public ResponseEntity<JobView> submit(@Valid @RequestBody SubmitJobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobs.submit(req));
    }

    @GetMapping
    public List<JobView> list(@RequestParam(required = false) String status,
                              @RequestParam(defaultValue = "50") int limit) {
        String normalized = status == null ? null : status.toUpperCase();
        if (normalized != null && !STATUSES.contains(normalized)) {
            throw new ApiExceptions.BadRequestException("Unknown status: '" + status + "'");
        }
        return jobs.list(normalized, limit);
    }

    @GetMapping("/{id}")
    public JobView get(@PathVariable String id) {
        return jobs.get(id);
    }

    @PostMapping("/{id}/cancel")
    public JobView cancel(@PathVariable String id) {
        return jobs.cancel(id);
    }
}
