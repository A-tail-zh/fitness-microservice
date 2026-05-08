package com.fitness.aiservice.service;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.client.UserProfileClient;
import com.fitness.aiservice.dto.EmailNotificationEvent;
import com.fitness.aiservice.dto.EnhancedAnalysisRequest;
import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import com.fitness.aiservice.dto.GoalCompletedNotificationRequest;
import com.fitness.aiservice.dto.ReportEmailRequest;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ActivityHistoryClient activityHistoryClient;
    private final EnhancedAnalysisService enhancedAnalysisService;
    private final UserProfileClient userProfileClient;
    private final EmailNotificationProducer emailNotificationProducer;

    public boolean publishAiReport(String userIdentifier, EnhancedAnalysisResponse response) {
        try {
            UserProfileClient.UserProfileResponse user = userProfileClient.resolveUser(userIdentifier);
            if (user == null || !StringUtils.hasText(user.getEmail())) {
                log.debug("Skip AI report email because user email is empty, userId={}", userIdentifier);
                return false;
            }

            EmailNotificationEvent event = EmailNotificationEvent.builder()
                    .eventId("AI_REPORT_GENERATED:" + response.getRequestId() + ":" + UUID.randomUUID())
                    .type(NotificationType.AI_REPORT_GENERATED)
                    .userId(user.getId() != null ? user.getId() : userIdentifier)
                    .to(user.getEmail())
                    .username(resolveUsername(user))
                    .subject("【律动】你的训练分析报告已生成")
                    .payload(buildAiReportPayload(response))
                    .createdAt(LocalDateTime.now())
                    .build();
            return emailNotificationProducer.publish(event);
        } catch (Exception ex) {
            log.debug("Failed to create AI report email event, userId={}, reason={}",
                    userIdentifier, ex.getMessage(), ex);
            return false;
        }
    }

    public EnhancedAnalysisResponse generateAndPublishReport(ReportEmailRequest request) {
        validateReportEmailRequest(request);

        List<Activity> activities = activityHistoryClient.getUserActivities(request.getUserId());
        if (activities == null || activities.isEmpty()) {
            throw new IllegalStateException("当前用户暂无训练记录，无法生成训练报告");
        }

        Activity currentActivity = resolveActivity(request, activities);
        EnhancedAnalysisRequest enhancedRequest = EnhancedAnalysisRequest.builder()
                .userId(request.getUserId())
                .activityId(request.getActivityId())
                .reportType(request.getReportType())
                .userNote(request.getUserNote())
                .sendEmail(true)
                .build();

        EnhancedAnalysisResponse response = enhancedAnalysisService.analyze(enhancedRequest, currentActivity, activities);
        publishAiReport(request.getUserId(), response);
        return response;
    }

    public boolean publishGoalCompleted(GoalCompletedNotificationRequest request) {
        if (!StringUtils.hasText(request.getEmail())) {
            log.debug("Skip goal completed email because recipient is empty, userId={}, goalId={}",
                    request.getUserId(), request.getGoalId());
            return false;
        }

        EmailNotificationEvent event = EmailNotificationEvent.builder()
                .eventId("GOAL_COMPLETED:" + request.getGoalId())
                .type(NotificationType.GOAL_COMPLETED)
                .userId(request.getUserId())
                .to(request.getEmail())
                .username(defaultText(request.getUsername(), "用户"))
                .subject("【律动】目标已完成：" + defaultText(request.getGoalName(), "训练目标"))
                .payload(buildGoalPayload(request))
                .createdAt(LocalDateTime.now())
                .build();
        return emailNotificationProducer.publish(event);
    }

    private void validateReportEmailRequest(ReportEmailRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserId())) {
            throw new IllegalArgumentException("userId 不能为空");
        }
    }

    private Activity resolveActivity(ReportEmailRequest request, List<Activity> activities) {
        if (!StringUtils.hasText(request.getActivityId())) {
            return activities.stream()
                    .filter(activity -> activity.getStartTime() != null)
                    .max(Comparator.comparing(Activity::getStartTime))
                    .orElseThrow(() -> new IllegalStateException("未找到有效训练记录"));
        }
        return activities.stream()
                .filter(activity -> request.getActivityId().equals(activity.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到指定训练记录 " + request.getActivityId()));
    }

    private String resolveUsername(UserProfileClient.UserProfileResponse user) {
        if (StringUtils.hasText(user.getFirstName()) || StringUtils.hasText(user.getLastName())) {
            return (defaultString(user.getFirstName()) + defaultString(user.getLastName())).trim();
        }
        if (StringUtils.hasText(user.getEmail()) && user.getEmail().contains("@")) {
            return user.getEmail().substring(0, user.getEmail().indexOf('@'));
        }
        return "用户";
    }

    private Map<String, Object> buildAiReportPayload(EnhancedAnalysisResponse response) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("analysisType", reportTypeText(response.getReportType()));
        payload.put("analysisTime", response.getGeneratedAt() == null
                ? LocalDateTime.now().format(DATE_TIME_FORMATTER)
                : response.getGeneratedAt().format(DATE_TIME_FORMATTER));
        payload.put("overallScore", response.getOverallScore());
        payload.put("overallStatus", statusText(response.getOverallStatus()));
        payload.put("overview", overview(response));
        payload.put("riskTips", riskTips(response));
        payload.put("recoveryAdvice", recoveryAdvice(response));
        payload.put("suggestions", suggestions(response));
        payload.put("summary", summary(response));
        payload.put("markdownReport", defaultText(response.getMarkdownReport(), response.getNarrativeAnalysis()));
        payload.put("providerTrace", defaultText(response.getProviderTrace(), ""));
        return payload;
    }

    private Map<String, Object> buildGoalPayload(GoalCompletedNotificationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("goalId", request.getGoalId());
        payload.put("goalName", defaultText(request.getGoalName(), "训练目标"));
        payload.put("goalType", defaultText(request.getGoalType(), "训练目标"));
        payload.put("completedAt", defaultText(request.getCompletedAt(), LocalDateTime.now().format(DATE_TIME_FORMATTER)));
        payload.put("goalPeriod", defaultText(request.getGoalPeriod(), "当前周期"));
        payload.put("completionDescription", defaultText(
                request.getCompletionDescription(),
                "目标进度已达到 100%，系统已判定本阶段目标完成。"));
        payload.put("nextStepAdvice", defaultText(
                request.getNextStepAdvice(),
                defaultText(request.getAiSuggestion(), "设置新的阶段目标，并保持稳定训练节奏。")));
        return payload;
    }

    private String reportTypeText(String reportType) {
        return switch (defaultText(reportType, "DAILY").toUpperCase()) {
            case "WEEKLY" -> "每周训练分析";
            case "MONTHLY" -> "每月训练分析";
            case "FILTER" -> "当前筛选训练分析";
            default -> "每日训练分析";
        };
    }

    private String statusText(String status) {
        return switch (defaultText(status, "").toUpperCase()) {
            case "EXCELLENT" -> "优秀";
            case "GOOD" -> "良好";
            case "FAIR" -> "一般";
            case "NEEDS_IMPROVEMENT" -> "待改善";
            default -> defaultText(status, "暂无");
        };
    }

    private String overview(EnhancedAnalysisResponse response) {
        if (response.getDimensions() == null || response.getDimensions().getVolume() == null) {
            return "已完成训练数据汇总";
        }
        EnhancedAnalysisResponse.TrainingVolume volume = response.getDimensions().getVolume();
        return "近7天 " + volume.getTotalDurationLast7Days() + " 分钟 / "
                + volume.getTotalCaloriesLast7Days() + " 千卡";
    }

    private List<String> riskTips(EnhancedAnalysisResponse response) {
        if (response.getRiskAlerts() == null || response.getRiskAlerts().isEmpty()) {
            return List.of("当前未发现明显高风险，请继续关注训练恢复节奏。");
        }
        return response.getRiskAlerts().stream()
                .map(alert -> defaultText(alert.getDescription(), defaultText(alert.getType(), "风险提示")))
                .limit(4)
                .toList();
    }

    private String recoveryAdvice(EnhancedAnalysisResponse response) {
        if (response.getDimensions() != null
                && response.getDimensions().getRecovery() != null
                && StringUtils.hasText(response.getDimensions().getRecovery().getRecommendation())) {
            return response.getDimensions().getRecovery().getRecommendation();
        }
        return "根据恢复状态安排下一次训练强度。";
    }

    private List<String> suggestions(EnhancedAnalysisResponse response) {
        if (response.getSuggestions() == null || response.getSuggestions().isEmpty()) {
            return List.of("保持稳定训练频率，并根据身体反馈调整负荷。");
        }
        List<String> items = new ArrayList<>();
        for (EnhancedAnalysisResponse.StructuredSuggestion suggestion : response.getSuggestions()) {
            String text = defaultText(suggestion.getAction(), defaultText(suggestion.getContent(), suggestion.getTitle()));
            if (StringUtils.hasText(text)) {
                items.add(text);
            }
            if (items.size() >= 4) {
                break;
            }
        }
        return items.isEmpty() ? List.of("保持稳定训练频率，并根据身体反馈调整负荷。") : items;
    }

    private String summary(EnhancedAnalysisResponse response) {
        String source = response.getNarrativeAnalysis();
        if (!StringUtils.hasText(source)) {
            return "系统已结合训练、恢复与目标数据生成本次分析。";
        }
        String normalized = source.replaceAll("\\s+", " ").replace("```json", "").replace("```", "").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
