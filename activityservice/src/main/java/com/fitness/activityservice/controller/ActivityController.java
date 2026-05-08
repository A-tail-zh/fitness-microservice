package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.dto.ActivityImportResult;
import com.fitness.activityservice.dto.StandardActivityDTO;
import com.fitness.activityservice.model.ActivitySource;
import com.fitness.activityservice.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public ResponseEntity<ActivityResponse> trackActivity(@Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.trackActivity(request));
    }

    @PostMapping("/import/garmin")
    public ResponseEntity<ActivityImportResult> importGarminActivity(@RequestBody StandardActivityDTO request) {
        request.setSource(ActivitySource.GARMIN_CN);
        ActivityImportResult result = activityService.importActivityWithResult(request);
        HttpStatus status = "created".equals(result.getStatus()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getUserActivities(@RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(activityService.getUserActivities(userId));
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> getActivityById(@PathVariable String activityId) {
        return ResponseEntity.ok(activityService.getActivityById(activityId));
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> deleteActivity(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable String activityId) {
        activityService.deleteActivity(activityId, userId);
        return ResponseEntity.noContent().build();
    }
}
