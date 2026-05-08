package com.fitness.aiservice.controller;

import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import com.fitness.aiservice.dto.GoalCompletedNotificationRequest;
import com.fitness.aiservice.dto.ReportEmailRequest;
import com.fitness.aiservice.service.ReportNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class ReportNotificationController {

    private final ReportNotificationService reportNotificationService;

    @PostMapping("/reports/email")
    public ResponseEntity<EnhancedAnalysisResponse> sendReportEmail(@RequestBody ReportEmailRequest request) {
        return ResponseEntity.ok(reportNotificationService.generateAndPublishReport(request));
    }

    @PostMapping("/goal-completed")
    public ResponseEntity<Void> sendGoalCompletedEmail(@RequestBody GoalCompletedNotificationRequest request) {
        reportNotificationService.publishGoalCompleted(request);
        return ResponseEntity.accepted().build();
    }
}
