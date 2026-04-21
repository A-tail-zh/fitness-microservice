package com.fitness.aiservice.controller;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.dto.EnhancedAnalysisRequest;
import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.service.EnhancedAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("api/ai-analysis")
@RequiredArgsConstructor
@Slf4j
public class EnhancedAnalysisController {

    private final EnhancedAnalysisService enhancedAnalysisService;
    private final ActivityHistoryClient activityHistoryClient;

    @PostMapping("/enhanced")
    public ResponseEntity<EnhancedAnalysisResponse> enhance(@RequestBody EnhancedAnalysisRequest request) {
        log.info("收到增强分析请求, userId={}, activityId={}, reportType={}",
                request.getUserId(), request.getActivityId(), request.getReportType());

        validateRequest(request);

        List<Activity> activities = activityHistoryClient.getUserActivities(request.getUserId());
        if (activities == null || activities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Activity currentActivity = resolveActivity(request, activities);
        EnhancedAnalysisResponse response = enhancedAnalysisService.analyze(request, currentActivity, activities);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/enhanced/user/{userId}/latest")
    public ResponseEntity<EnhancedAnalysisResponse> enhanceLatest(
            @PathVariable String userId,
            @RequestParam(defaultValue = "DAILY") String reportType,
            @RequestParam(required = false) String userNote) {

        log.info("触发最新活动增强分析, userId={}, reportType={}", userId, reportType);

        List<Activity> activities = activityHistoryClient.getUserActivities(userId);
        if (activities == null || activities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Activity latest = activities.stream()
                .filter(activity -> activity.getStartTime() != null)
                .max(Comparator.comparing(Activity::getStartTime))
                .orElseThrow(() -> new IllegalStateException("未找到有效训练记录"));

        EnhancedAnalysisRequest request = EnhancedAnalysisRequest.builder()
                .userId(userId)
                .activityId(latest.getId())
                .reportType(reportType)
                .userNote(userNote)
                .build();

        EnhancedAnalysisResponse response = enhancedAnalysisService.analyze(request, latest, activities);
        return ResponseEntity.ok(response);
    }

    private void validateRequest(EnhancedAnalysisRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
    }

    private Activity resolveActivity(EnhancedAnalysisRequest request, List<Activity> activities) {
        if (request.getActivityId() == null || request.getActivityId().isBlank()) {
            return activities.stream()
                    .filter(activity -> activity.getStartTime() != null)
                    .max(Comparator.comparing(Activity::getStartTime))
                    .orElseThrow(() -> new IllegalArgumentException("未找到有效训练记录"));
        }

        return activities.stream()
                .filter(activity -> request.getActivityId().equals(activity.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未找到活动 " + request.getActivityId() + "，请确认 activityId 和 userId 正确"));
    }
}
