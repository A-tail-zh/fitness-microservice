package com.fitness.activityservice.service;

import com.fitness.activityservice.client.AiFitnessAssessmentClient;
import com.fitness.activityservice.client.GarminPythonSyncClient;
import com.fitness.activityservice.client.UserProfileClient;
import com.fitness.activityservice.dto.GarminSyncResponse;
import com.fitness.activityservice.dto.StandardActivityDTO;
import com.fitness.activityservice.model.ImportedActivityRecord;
import com.fitness.activityservice.model.ThirdPartyAccount;
import com.fitness.activityservice.model.ThirdPartyBindStatus;
import com.fitness.activityservice.model.ThirdPartyPlatform;
import com.fitness.activityservice.repository.ImportedActivityRecordRepository;
import com.fitness.activityservice.repository.ThirdPartyAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class GarminSyncService {

    private final ThirdPartyAccountRepository thirdPartyAccountRepository;
    private final ImportedActivityRecordRepository importedActivityRecordRepository;
    private final GarminPythonSyncClient garminPythonSyncClient;
    private final ActivityNormalizeService activityNormalizeService;
    private final ActivityService activityService;
    private final UserProfileClient userProfileClient;
    private final AiFitnessAssessmentClient aiFitnessAssessmentClient;

    @Value("${garmin.sync.default-days:3}")
    private int defaultSyncDays;

    @Value("${garmin.ai-assessment.recent-activity-limit:5}")
    private int aiAssessmentRecentActivityLimit;

    @Transactional
    public GarminSyncResponse syncRecentActivities(String userIdentifier, int days) {
        ThirdPartyAccount account = thirdPartyAccountRepository.findByUserIdAndPlatform(userIdentifier, ThirdPartyPlatform.GARMIN)
                .filter(item -> item.getBindStatus() == ThirdPartyBindStatus.BOUND)
                .filter(item -> StringUtils.hasText(item.getAccessToken()))
                .orElseThrow(() -> new IllegalStateException("Garmin 尚未建立可用会话，无法同步"));
        return syncRecentActivities(account, days);
    }

    @Transactional
    public GarminSyncResponse syncRecentActivities(ThirdPartyAccount account, int days) {
        int normalizedDays = normalizeDays(days);
        LocalDateTime now = LocalDateTime.now();

        List<GarminPythonSyncClient.GarminActivityItem> remoteActivities =
                garminPythonSyncClient.fetchActivities(account.getAccessToken(), normalizedDays);
        List<StandardActivityDTO> standardActivities = remoteActivities.stream()
                .map(item -> activityNormalizeService.normalizeGarminActivity(account.getUserId(), item))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StandardActivityDTO::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        int importedCount = 0;
        int skippedCount = 0;
        for (StandardActivityDTO activity : standardActivities) {
            if (!StringUtils.hasText(activity.getExternalActivityId())) {
                skippedCount++;
                continue;
            }
            boolean exists = importedActivityRecordRepository
                    .findByPlatformAndExternalActivityId(ThirdPartyPlatform.GARMIN, activity.getExternalActivityId())
                    .isPresent();
            if (exists) {
                skippedCount++;
                continue;
            }
            importedActivityRecordRepository.save(mapImportedRecord(activity));
            activityService.importActivity(activity);
            importedCount++;
        }

        String ruleLevel = calculateRuleLevel(standardActivities, account);
        String recentSummary = buildRecentActivitySummary(standardActivities, normalizedDays);
        String syncMessage = importedCount > 0
                ? "Garmin 原型同步完成，已导入 %d 条新记录。".formatted(importedCount)
                : "Garmin 原型同步完成，没有新的活动记录。";

        String finalLevel = null;
        String aiSummary = null;
        String lastSyncStatus = "SUCCESS";

        try {
            UserProfileClient.UserProfileResponse userProfile = userProfileClient.getCurrentUserProfile(account.getUserId());
            AiFitnessAssessmentClient.FitnessAssessmentAiResponse aiResponse = aiFitnessAssessmentClient.analyze(
                    buildAiRequest(account.getUserId(), userProfile, standardActivities, recentSummary, ruleLevel));

            finalLevel = normalizeLevel(aiResponse.getFitnessLevel(), ruleLevel);
            aiSummary = firstNonBlank(aiResponse.getSummary(), "系统已根据 Garmin 真实运动数据完成等级评估。");

            userProfileClient.updateExternalAssessment(account.getUserId(),
                    UserProfileClient.ExternalAssessmentUpdateRequest.builder()
                            .activitySource("GARMIN_PYTHON_SYNC")
                            .ruleLevel(ruleLevel)
                            .aiLevel(normalizeLevel(aiResponse.getFitnessLevel(), ruleLevel))
                            .finalLevel(finalLevel)
                            .summary(aiSummary)
                            .reason(firstNonBlank(aiResponse.getReason(), "评估基于 Garmin 最近真实运动数据和 Python 原型同步结果。"))
                            .suggestion(firstNonBlank(aiResponse.getSuggestion(), aiResponse.getTrainingSuggestion()))
                            .riskWarning(firstNonBlank(aiResponse.getRiskWarning(), "当前未识别到额外风险提示。"))
                            .recentActivitySummary(recentSummary)
                            .aiReport(buildAiReport(aiResponse))
                            .build());
        } catch (Exception ex) {
            lastSyncStatus = "PARTIAL";
            syncMessage = "Garmin 数据已同步，但 AI 评估回写失败。";
            log.warn("Garmin 原型同步后的 AI 等级评估失败，userId={}", account.getUserId(), ex);
        }

        account.setBindStatus(ThirdPartyBindStatus.BOUND);
        account.setLastSyncAt(now);
        account.setLastSyncStatus(lastSyncStatus);
        account.setLastSyncMessage(syncMessage);
        thirdPartyAccountRepository.save(account);

        return GarminSyncResponse.builder()
                .totalFetched(remoteActivities.size())
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .fitnessLevel(finalLevel)
                .summary(aiSummary)
                .sessionToken(account.getAccessToken())
                .lastSyncAt(now)
                .lastSyncStatus(lastSyncStatus)
                .message(syncMessage)
                .build();
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return defaultSyncDays;
        }
        return Math.max(1, Math.min(90, days));
    }

    private ImportedActivityRecord mapImportedRecord(StandardActivityDTO activity) {
        return ImportedActivityRecord.builder()
                .userId(activity.getUserId())
                .platform(ThirdPartyPlatform.GARMIN)
                .externalActivityId(activity.getExternalActivityId())
                .activityType(activity.getType() == null ? null : activity.getType().name())
                .distance(activity.getDistance())
                .durationSeconds(activity.getDurationSeconds())
                .calories(activity.getCalories())
                .avgHeartRate(activity.getAvgHeartRate())
                .maxHeartRate(activity.getMaxHeartRate())
                .avgPace(activity.getAvgPace())
                .startTime(activity.getStartTime())
                .rawData(activity.getRawData())
                .build();
    }

    private AiFitnessAssessmentClient.FitnessAssessmentAiRequest buildAiRequest(
            String userIdentifier,
            UserProfileClient.UserProfileResponse userProfile,
            List<StandardActivityDTO> activities,
            String recentSummary,
            String ruleLevel) {
        int weeklyFrequency = Math.max(1, (int) Math.round(activities.size() / 4.0d));
        return AiFitnessAssessmentClient.FitnessAssessmentAiRequest.builder()
                .userId(userIdentifier)
                .age(userProfile.getAge())
                .height(userProfile.getHeight())
                .weight(userProfile.getWeight())
                .gender(userProfile.getGender())
                .goal(userProfile.getGoal())
                .weeklyExerciseFrequency(weeklyFrequency)
                .recentExerciseTime(describeRecentExerciseTime(activities))
                .injuryStatus(userProfile.getInjuryStatus())
                .exerciseExperience(userProfile.getFitnessLevel())
                .recentActivitySummary(recentSummary)
                .ruleLevel(ruleLevel)
                .recentActivities(activities.stream()
                        .limit(Math.max(1, aiAssessmentRecentActivityLimit))
                        .map(activity -> AiFitnessAssessmentClient.ActivityItem.builder()
                                .id(activity.getExternalActivityId())
                                .type(activity.getType() == null ? null : activity.getType().name())
                                .source(activity.getSource() == null ? null : activity.getSource().name())
                                .distance(activity.getDistance())
                                .duration(activity.getDurationSeconds() == null ? null : Math.max(1, (int) Math.round(activity.getDurationSeconds() / 60.0d)))
                                .durationSeconds(activity.getDurationSeconds())
                                .calories(activity.getCalories())
                                .avgHeartRate(activity.getAvgHeartRate())
                                .maxHeartRate(activity.getMaxHeartRate())
                                .avgPace(activity.getAvgPace())
                                .startTime(activity.getStartTime())
                                .createdAt(activity.getStartTime())
                                .build())
                        .toList())
                .build();
    }

    private String calculateRuleLevel(List<StandardActivityDTO> activities, ThirdPartyAccount account) {
        int score = 0;
        long totalMinutes = activities.stream()
                .map(StandardActivityDTO::getDurationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(seconds -> Math.round(seconds / 60.0d))
                .sum();
        long activeDays = activities.stream()
                .map(StandardActivityDTO::getStartTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .count();

        if (activities.size() >= 18) score += 35;
        else if (activities.size() >= 12) score += 28;
        else if (activities.size() >= 6) score += 18;
        else if (activities.size() >= 3) score += 10;
        else score += 4;

        if (activeDays >= 12) score += 20;
        else if (activeDays >= 8) score += 14;
        else if (activeDays >= 4) score += 8;

        if (totalMinutes >= 900) score += 25;
        else if (totalMinutes >= 600) score += 18;
        else if (totalMinutes >= 300) score += 10;

        double avgHeartRate = activities.stream()
                .map(StandardActivityDTO::getAvgHeartRate)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        if (avgHeartRate >= 135 && activities.size() >= 6) {
            score += 6;
        }

        if (StringUtils.hasText(account.getLastSyncMessage()) && account.getLastSyncMessage().contains("风险")) {
            score -= 4;
        }

        if (score < 25) return "BEGINNER";
        if (score < 45) return "NOVICE";
        if (score < 70) return "INTERMEDIATE";
        return "ADVANCED";
    }

    private String describeRecentExerciseTime(List<StandardActivityDTO> activities) {
        LocalDateTime latest = activities.stream()
                .map(StandardActivityDTO::getStartTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (latest == null) {
            return "暂无近期运动";
        }
        long days = ChronoUnit.DAYS.between(latest.toLocalDate(), LocalDateTime.now().toLocalDate());
        if (days <= 7) return "一周内";
        if (days <= 30) return "一个月内";
        if (days <= 90) return "三个月内";
        return "三个月以前";
    }

    private String buildRecentActivitySummary(List<StandardActivityDTO> activities, int days) {
        if (activities.isEmpty()) {
            return "最近 %d 天暂无 Garmin 运动记录。".formatted(days);
        }

        double totalDistance = activities.stream()
                .map(StandardActivityDTO::getDistance)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        long totalMinutes = activities.stream()
                .map(StandardActivityDTO::getDurationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(seconds -> Math.round(seconds / 60.0d))
                .sum();
        double totalCalories = activities.stream()
                .map(StandardActivityDTO::getCalories)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        LocalDateTime latest = activities.stream()
                .map(StandardActivityDTO::getStartTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return "最近 %d 天通过 Garmin Python 原型服务同步 %d 次训练，累计 %.1f 公里、%d 分钟、%.0f 千卡；最近一次训练时间：%s。"
                .formatted(
                        days,
                        activities.size(),
                        totalDistance / 1000.0d,
                        totalMinutes,
                        totalCalories,
                        latest == null ? "暂无" : latest.truncatedTo(ChronoUnit.MINUTES));
    }

    private String normalizeLevel(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BEGINNER", "NOVICE", "INTERMEDIATE", "ADVANCED" -> normalized;
            default -> fallback;
        };
    }

    private String buildAiReport(AiFitnessAssessmentClient.FitnessAssessmentAiResponse response) {
        return String.join("\n",
                "总结：" + firstNonBlank(response.getSummary(), "暂无"),
                "依据：" + firstNonBlank(response.getReason(), "暂无"),
                "建议：" + firstNonBlank(response.getSuggestion(), response.getTrainingSuggestion(), "暂无"),
                "风险提示：" + firstNonBlank(response.getRiskWarning(), "暂无"),
                "原始报告：" + firstNonBlank(response.getRawReport(), "暂无"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
