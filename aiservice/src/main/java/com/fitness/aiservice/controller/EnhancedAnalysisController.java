package com.fitness.aiservice.controller;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.dto.EnhancedAnalysisRequest;
import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.service.EnhancedAnalysisService;
import com.fitness.aiservice.service.ReportNotificationService;
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
    private final ReportNotificationService reportNotificationService;

    @PostMapping("/enhanced")
    public ResponseEntity<EnhancedAnalysisResponse> enhance(@RequestBody EnhancedAnalysisRequest request) {
        log.info("收到增强分析请求，userId={}, activityId={}, reportType={}",
                request.getUserId(), request.getActivityId(), request.getReportType());

        validateRequest(request);

        List<Activity> activities = activityHistoryClient.getUserActivities(request.getUserId());
        if (activities == null || activities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Activity currentActivity = resolveActivity(request, activities);
        EnhancedAnalysisResponse response = enhancedAnalysisService.analyze(request, currentActivity, activities);
        maybeSendEmail(request, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/enhanced/user/{userId}/latest")
    public ResponseEntity<EnhancedAnalysisResponse> enhanceLatest(
            @PathVariable String userId,
            @RequestParam(defaultValue = "DAILY") String reportType,
            @RequestParam(required = false) String userNote,
            @RequestParam(defaultValue = "false") boolean sendEmail) {

        log.info("触发最新活动增强分析，userId={}, reportType={}", userId, reportType);

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
                .sendEmail(sendEmail)
                .build();

        EnhancedAnalysisResponse response = enhancedAnalysisService.analyze(request, latest, activities);
        maybeSendEmail(request, response);
        return ResponseEntity.ok(response);
    }

    private void maybeSendEmail(EnhancedAnalysisRequest request, EnhancedAnalysisResponse response) {
        if (!Boolean.TRUE.equals(request.getSendEmail())) {
            response.setEmailNotificationStatus("SKIPPED");
            response.setEmailNotificationMessage("AI分析报告已生成");
            return;
        }
        boolean queued = reportNotificationService.publishAiReport(request.getUserId(), response);
        response.setEmailNotificationStatus(queued ? "QUEUED" : "SKIPPED");
        response.setEmailNotificationMessage(queued
                ? "报告已发送至你的邮箱"
                : "报告生成成功，但邮件发送失败，请检查邮箱配置");
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
