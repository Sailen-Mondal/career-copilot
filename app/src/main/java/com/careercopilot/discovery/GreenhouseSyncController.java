package com.careercopilot.discovery;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class GreenhouseSyncController {

    private final JobDiscoveryService jobDiscoveryService;

    public GreenhouseSyncController(JobDiscoveryService jobDiscoveryService) {
        this.jobDiscoveryService = jobDiscoveryService;
    }

    @PostMapping({"/api/jobs/sync", "/api/sources/greenhouse/sync"})
    public ResponseEntity<List<Job>> syncJobs(@RequestParam(value = "board", defaultValue = "google") String board) {
        List<Job> synced = jobDiscoveryService.syncBoard(board);
        return ResponseEntity.ok(synced);
    }

    @GetMapping("/api/jobs")
    public ResponseEntity<List<Job>> getJobs() {
        List<Job> jobs = jobDiscoveryService.getAllJobs();
        return ResponseEntity.ok(jobs);
    }
}
