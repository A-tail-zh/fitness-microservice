package com.fitness.userservice.service;

import com.fitness.userservice.client.ActivityHistoryClient;
import com.fitness.userservice.client.AiActivityItem;
import com.fitness.userservice.client.AiAssessmentClient;
import com.fitness.userservice.client.FitnessAssessmentAiRequest;
import com.fitness.userservice.client.FitnessAssessmentAiResponse;
import com.fitness.userservice.dto.AssessmentStatusResponse;
import com.fitness.userservice.dto.ExternalFitnessAssessmentRequest;
import com.fitness.userservice.dto.FitnessAssessmentRequest;
import com.fitness.userservice.dto.FitnessAssessmentResult;
import com.fitness.userservice.model.FitnessLevel;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserFitnessAssessment;
import com.fitness.userservice.repository.UserFitnessAssessmentRepository;
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAssessmentService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserFitnessAssessmentRepository assessmentRepository;
    private final ActivityHistoryClient activityHistoryClient;
    private final AiAssessmentClient aiAssessmentClient;

    public AssessmentStatusResponse getAssessmentStatus(String userIdentifier) {
        User user = userService.resolveUserOrThrow(userIdentifier);
        return AssessmentStatusResponse.builder()
                .assessmentCompleted(Boolean.TRUE.equals(user.getAssessmentCompleted()))
                .fitnessLevel(user.getFitnessLevel())
                .assessmentUpdatedAt(user.getAssessmentUpdatedAt())
                .build();
    }

    @Transactional
    public FitnessAssessmentResult submitAssessment(String userIdentifier, FitnessAssessmentRequest request) {
        User user = userService.resolveUserOrThrow(userIdentifier);
        List<String> activityIdentifiers = Stream.of(user.getId(), user.getKeycloakId(), userIdentifier)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        List<ActivityHistoryClient.ActivitySnapshot> recentActivities = activityHistoryClient.getUserActivities(activityIdentifiers).stream()
                .sorted(Comparator.comparing(this::activityTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(12)
                .toList();

        FitnessLevel ruleLevel = calculateRuleLevel(request, recentActivities);
        String recentActivitySummary = buildRecentActivitySummary(recentActivities);
        FitnessAssessmentAiResponse aiResponse = analyzeByAi(
                user, request, recentActivities, recentActivitySummary, ruleLevel);

        FitnessLevel aiLevel = parseLevel(aiResponse.getFitnessLevel(), ruleLevel);
        FitnessLevel finalLevel = mergeLevels(ruleLevel, aiLevel, request.getInjuryStatus());
        String report = buildAssessmentReport(aiResponse);

        saveAssessmentRecord(user, request, recentActivitySummary, ruleLevel, aiLevel, finalLevel, aiResponse, report);
        updateUserAssessment(user, request.getAge(), request.getHeight(), request.getWeight(), request.getGender(),
                request.getGoal(), blankToNull(request.getInjuryStatus()), finalLevel, report);

        return FitnessAssessmentResult.builder()
                .assessmentCompleted(true)
                .ruleLevel(ruleLevel.name())
                .aiLevel(aiLevel.name())
                .finalLevel(finalLevel.name())
                .summary(aiResponse.getSummary())
                .reason(aiResponse.getReason())
                .suggestion(aiResponse.getSuggestion())
                .riskWarning(aiResponse.getRiskWarning())
                .recentActivitySummary(recentActivitySummary)
                .aiReport(report)
                .assessedAt(user.getAssessmentUpdatedAt())
                .build();
    }

    @Transactional
    public FitnessAssessmentResult applyExternalAssessment(String userIdentifier, ExternalFitnessAssessmentRequest request) {
        User user = userService.resolveUserOrThrow(userIdentifier);

        FitnessLevel ruleLevel = parseLevel(request.getRuleLevel(), parseLevel(user.getFitnessLevel(), FitnessLevel.NOVICE));
        FitnessLevel aiLevel = parseLevel(request.getAiLevel(), parseLevel(request.getFinalLevel(), ruleLevel));
        FitnessLevel finalLevel = parseLevel(request.getFinalLevel(), aiLevel);
        String report = StringUtils.hasText(request.getAiReport())
                ? request.getAiReport()
                : buildExternalAssessmentReport(request);

        UserFitnessAssessment assessment = new UserFitnessAssessment();
        assessment.setUserId(user.getId());
        assessment.setAge(user.getAge());
        assessment.setHeight(user.getHeight());
        assessment.setWeight(user.getWeight());
        assessment.setGender(user.getGender());
        assessment.setGoal(user.getGoal());
        assessment.setRecentExerciseTime(StringUtils.hasText(request.getActivitySource())
                ? request.getActivitySource() + " 自动同步"
                : null);
        assessment.setRecentActivitySummary(request.getRecentActivitySummary());
        assessment.setInjuryStatus(user.getInjuryStatus());
        assessment.setExerciseExperience("garmin_sync");
        assessment.setRuleLevel(ruleLevel);
        assessment.setAiLevel(aiLevel);
        assessment.setFinalLevel(finalLevel);
        assessment.setSummary(request.getSummary());
        assessment.setReason(request.getReason());
        assessment.setSuggestion(request.getSuggestion());
        assessment.setRiskWarning(request.getRiskWarning());
        assessment.setAiReport(report);
        assessmentRepository.save(assessment);

        updateUserAssessment(user, user.getAge(), user.getHeight(), user.getWeight(), user.getGender(),
                user.getGoal(), user.getInjuryStatus(), finalLevel, report);

        return FitnessAssessmentResult.builder()
                .assessmentCompleted(true)
                .ruleLevel(ruleLevel.name())
                .aiLevel(aiLevel.name())
                .finalLevel(finalLevel.name())
                .summary(request.getSummary())
                .reason(request.getReason())
                .suggestion(request.getSuggestion())
                .riskWarning(request.getRiskWarning())
                .recentActivitySummary(request.getRecentActivitySummary())
                .aiReport(report)
                .assessedAt(user.getAssessmentUpdatedAt())
                .build();
    }

    FitnessLevel calculateRuleLevel(FitnessAssessmentRequest request, List<ActivityHistoryClient.ActivitySnapshot> recentActivities) {
        int score = 0;

        Integer frequency = request.getWeeklyExerciseFrequency();
        if (frequency != null) {
            if (frequency >= 5) {
                score += 30;
            } else if (frequency >= 3) {
                score += 22;
            } else if (frequency >= 1) {
                score += 12;
            }
        }

        String experience = normalize(request.getExerciseExperience());
        if (experience.contains("year") || experience.contains("advanced") || experience.contains("long_term")) {
            score += 28;
        } else if (experience.contains("half_year") || experience.contains("six_months") || experience.contains("mid")) {
            score += 20;
        } else if (StringUtils.hasText(experience)) {
            score += 10;
        }

        String recentExerciseTime = normalize(request.getRecentExerciseTime());
        if (recentExerciseTime.contains("week")) {
            score += 15;
        } else if (recentExerciseTime.contains("month")) {
            score += 8;
        } else {
            score += 3;
        }

        long activityCount = recentActivities == null ? 0 : recentActivities.stream().filter(Objects::nonNull).count();
        if (activityCount >= 8) {
            score += 20;
        } else if (activityCount >= 4) {
            score += 12;
        } else if (activityCount >= 1) {
            score += 6;
        }

        if (StringUtils.hasText(request.getInjuryStatus())) {
            score -= 12;
        }

        if (score < 25) return FitnessLevel.BEGINNER;
        if (score < 45) return FitnessLevel.NOVICE;
        if (score < 70) return FitnessLevel.INTERMEDIATE;
        return FitnessLevel.ADVANCED;
    }

    private FitnessAssessmentAiResponse analyzeByAi(
            User user,
            FitnessAssessmentRequest request,
            List<ActivityHistoryClient.ActivitySnapshot> recentActivities,
            String recentActivitySummary,
            FitnessLevel ruleLevel) {
        try {
            return aiAssessmentClient.analyze(FitnessAssessmentAiRequest.builder()
                    .userId(user.getId())
                    .age(request.getAge())
                    .height(request.getHeight())
                    .weight(request.getWeight())
                    .gender(request.getGender())
                    .goal(request.getGoal())
                    .weeklyExerciseFrequency(request.getWeeklyExerciseFrequency())
                    .recentExerciseTime(request.getRecentExerciseTime())
                    .injuryStatus(blankToNull(request.getInjuryStatus()))
                    .exerciseExperience(request.getExerciseExperience())
                    .recentActivitySummary(recentActivitySummary)
                    .ruleLevel(ruleLevel.name())
                    .recentActivities(recentActivities.stream()
                            .map(activity -> AiActivityItem.builder()
                                    .id(activity.getId())
                                    .duration(activity.getDuration())
                                    .startTime(activity.getStartTime())
                                    .createdAt(activity.getCreatedAt())
                                    .build())
                            .toList())
                    .build());
        } catch (Exception ex) {
            log.warn("AI 评估失败，降级为规则结果，userId={}", user.getId(), ex);
            return FitnessAssessmentAiResponse.builder()
                    .fitnessLevel(ruleLevel.name())
                    .summary("AI 服务暂时不可用，已返回基于规则的初步评估结果。")
                    .reason("当前结果综合了每周运动频率、运动经验、最近一次运动时间和近期训练记录。")
                    .suggestion(defaultSuggestion(ruleLevel))
                    .riskWarning(StringUtils.hasText(request.getInjuryStatus())
                            ? "已检测到伤病信息，建议先从低强度训练开始并注意风险控制。"
                            : "当前未发现明显风险，建议循序渐进提升训练量。")
                    .rawReport("AI 服务不可用，当前结果为规则评估降级结果。")
                    .build();
        }
    }

    private String buildRecentActivitySummary(List<ActivityHistoryClient.ActivitySnapshot> activities) {
        if (activities == null || activities.isEmpty()) {
            return "系统中暂无近期运动记录。";
        }

        int totalDuration = activities.stream()
                .map(ActivityHistoryClient.ActivitySnapshot::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        LocalDateTime latestActivityTime = activities.stream()
                .map(this::activityTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        long recent7Days = activities.stream()
                .map(this::activityTime)
                .filter(Objects::nonNull)
                .filter(time -> !time.isBefore(LocalDateTime.now().minusDays(7)))
                .count();

        return String.format(
                Locale.ROOT,
                "近期共有 %d 次训练，累计约 %d 分钟；近 7 天训练 %d 次；最近一次训练时间：%s。",
                activities.size(),
                totalDuration,
                recent7Days,
                latestActivityTime == null ? "暂无" : latestActivityTime.truncatedTo(ChronoUnit.MINUTES)
        );
    }

    private void saveAssessmentRecord(
            User user,
            FitnessAssessmentRequest request,
            String recentActivitySummary,
            FitnessLevel ruleLevel,
            FitnessLevel aiLevel,
            FitnessLevel finalLevel,
            FitnessAssessmentAiResponse aiResponse,
            String report) {
        UserFitnessAssessment assessment = new UserFitnessAssessment();
        assessment.setUserId(user.getId());
        assessment.setAge(request.getAge());
        assessment.setHeight(request.getHeight());
        assessment.setWeight(request.getWeight());
        assessment.setGender(request.getGender());
        assessment.setGoal(request.getGoal());
        assessment.setWeeklyExerciseFrequency(request.getWeeklyExerciseFrequency());
        assessment.setRecentExerciseTime(request.getRecentExerciseTime());
        assessment.setRecentActivitySummary(recentActivitySummary);
        assessment.setInjuryStatus(blankToNull(request.getInjuryStatus()));
        assessment.setExerciseExperience(request.getExerciseExperience());
        assessment.setRuleLevel(ruleLevel);
        assessment.setAiLevel(aiLevel);
        assessment.setFinalLevel(finalLevel);
        assessment.setSummary(aiResponse.getSummary());
        assessment.setReason(aiResponse.getReason());
        assessment.setSuggestion(aiResponse.getSuggestion());
        assessment.setRiskWarning(aiResponse.getRiskWarning());
        assessment.setAiReport(report);
        assessmentRepository.save(assessment);
    }

    private void updateUserAssessment(
            User user,
            Integer age,
            Double height,
            Double weight,
            String gender,
            String goal,
            String injuryStatus,
            FitnessLevel finalLevel,
            String report) {
        user.setAge(age);
        user.setHeight(height);
        user.setWeight(weight);
        user.setGender(gender);
        user.setGoal(goal);
        user.setInjuryStatus(injuryStatus);
        user.setFitnessLevel(finalLevel.name());
        user.setAssessmentCompleted(Boolean.TRUE);
        user.setAssessmentReport(report);
        user.setAssessmentUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private String buildAssessmentReport(FitnessAssessmentAiResponse response) {
        return String.join("\n",
                "总结：" + defaultText(response.getSummary()),
                "依据：" + defaultText(response.getReason()),
                "建议：" + defaultText(response.getSuggestion()),
                "风险提示：" + defaultText(response.getRiskWarning()),
                "原始报告：" + defaultText(response.getRawReport()));
    }

    private String buildExternalAssessmentReport(ExternalFitnessAssessmentRequest request) {
        return String.join("\n",
                "数据来源：" + defaultText(request.getActivitySource()),
                "总结：" + defaultText(request.getSummary()),
                "依据：" + defaultText(request.getReason()),
                "建议：" + defaultText(request.getSuggestion()),
                "风险提示：" + defaultText(request.getRiskWarning()),
                "活动摘要：" + defaultText(request.getRecentActivitySummary()));
    }

    private FitnessLevel parseLevel(String rawLevel, FitnessLevel fallback) {
        if (!StringUtils.hasText(rawLevel)) {
            return fallback;
        }

        try {
            return FitnessLevel.valueOf(rawLevel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private FitnessLevel mergeLevels(FitnessLevel ruleLevel, FitnessLevel aiLevel, String injuryStatus) {
        int averageOrdinal = Math.round((ruleLevel.ordinal() + aiLevel.ordinal()) / 2.0f);
        if (StringUtils.hasText(injuryStatus)) {
            averageOrdinal = Math.max(FitnessLevel.BEGINNER.ordinal(), averageOrdinal - 1);
        }
        return FitnessLevel.values()[averageOrdinal];
    }

    private LocalDateTime activityTime(ActivityHistoryClient.ActivitySnapshot activity) {
        if (activity == null) {
            return null;
        }
        return activity.getStartTime() != null ? activity.getStartTime() : activity.getCreatedAt();
    }

    private String defaultSuggestion(FitnessLevel level) {
        return switch (level) {
            case BEGINNER -> "建议从每周 2 到 3 次、每次 20 到 30 分钟的低强度训练开始，优先建立稳定习惯。";
            case NOVICE -> "建议保持每周约 3 次训练，并逐步加入基础有氧与轻力量训练。";
            case INTERMEDIATE -> "建议采用更结构化的计划，逐步增加负荷并控制恢复节奏。";
            case ADVANCED -> "建议按阶段目标进行周期化训练，并重点关注恢复、疲劳和专项提升。";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "暂无";
    }
}
