package com.fitness.aiservice.service;

import com.fitness.aiservice.config.RuleEngineProperties;
import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityRuleEngineService {

    private final RuleEngineProperties props;

    // ─────────────────────────── 原有分析入口（保持兼容） ───────────────────────────

    public RuleAnalysisResult analyze(Activity currentActivity, UserHistorySummary summary) {
        List<String> highlights = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        String trainingLoadLevel = determineTrainingLoadLevel(currentActivity, summary);
        String recoveryStatus = determineRecoveryStatus(summary, trainingLoadLevel);
        String consistencyLevel = determineConsistencyLevel(summary);

        switch (trainingLoadLevel) {
            case "HIGH" -> {
                risks.add("近期训练负荷偏高，若恢复不足，可能影响后续训练质量");
                suggestions.add("下一次训练建议适当降强度或减少训练时长，优先保证恢复");
            }
            case "MEDIUM" -> highlights.add("当前训练负荷处于中等水平，整体可控");
            default -> highlights.add("当前训练负荷较低，适合作为恢复或重新建立节奏的阶段");
        }

        switch (recoveryStatus) {
            case "FATIGUE_RISK" -> {
                risks.add("系统识别到疲劳风险，说明近期训练推进和恢复节奏不够平衡");
                suggestions.add("建议安排 1-2 天低强度训练或主动恢复，避免继续叠加高负荷");
            }
            case "DETRAINING_RISK" -> {
                risks.add("近期训练间隔偏长，存在能力退化和节奏中断风险");
                suggestions.add("建议先恢复规律训练频率，从低到中等强度重新建立连续性");
            }
            default -> highlights.add("当前恢复状态整体平衡，可继续按计划推进训练");
        }

        switch (consistencyLevel) {
            case "HIGH" -> highlights.add("近期训练连续性较好，有利于能力稳定提升");
            case "MEDIUM" -> highlights.add("训练连续性一般，仍有进一步稳定节奏的空间");
            default -> {
                risks.add("训练连续性偏弱，容易影响长期进步");
                suggestions.add("优先保证每周固定训练次数，先建立连续性，再追求更高强度");
            }
        }

        return RuleAnalysisResult.builder()
                .trainingLoadLevel(trainingLoadLevel)
                .recoveryStatus(recoveryStatus)
                .consistencyLevel(consistencyLevel)
                .highlights(highlights)
                .risks(risks)
                .suggestions(suggestions)
                .build();
    }

    // ─────────────────────────── 新增：多维度增强分析 ───────────────────────────

    /**
     * 计算训练容量指标（Volume）
     */
    public EnhancedAnalysisResponse.TrainingVolume analyzeVolume(UserHistorySummary summary) {
        int d7 = summary.getTotalDurationLast7Days();
        int d30 = summary.getTotalDurationLast30Days();
        var loadCfg = props.getLoad();

        String volumeLevel;
        if (d7 >= loadCfg.getWeeklyHighMinutes()) {
            volumeLevel = "HIGH";
        } else if (d7 >= loadCfg.getWeeklyMediumMinutes()) {
            volumeLevel = "MEDIUM";
        } else if (d7 > 0) {
            volumeLevel = "LOW";
        } else {
            volumeLevel = "NONE";
        }

        double weeklyAvg30 = d30 / 4.0;
        String volumeTrend;
        if (d7 > weeklyAvg30 * 1.2) {
            volumeTrend = "INCREASING";
        } else if (d7 < weeklyAvg30 * 0.8) {
            volumeTrend = "DECREASING";
        } else {
            volumeTrend = "STABLE";
        }

        return EnhancedAnalysisResponse.TrainingVolume.builder()
                .totalDurationLast7Days(d7)
                .totalDurationLast30Days(d30)
                .totalCaloriesLast7Days(summary.getTotalCaloriesLast7Days())
                .totalCaloriesLast30Days(summary.getTotalCaloriesLast30Days())
                .avgDurationPerSession(summary.getAvgDurationLast7Days())
                .volumeLevel(volumeLevel)
                .volumeTrend(volumeTrend)
                .typeDistribution(summary.getActivityTypeDistribution())
                .build();
    }

    /**
     * 计算训练负荷指标（Load）— 含急慢性负荷比（ACWR）和过度训练风险
     */
    public EnhancedAnalysisResponse.TrainingLoad analyzeLoad(Activity currentActivity, UserHistorySummary summary) {
        String loadLevel = determineTrainingLoadLevel(currentActivity, summary);
        var acCfg = props.getAcuteChronic();

        double acuteLoad = summary.getAvgDurationLast7Days();
        double chronicLoad = summary.getAvgDurationLast30Days();
        double acwr = chronicLoad > 0 ? acuteLoad / chronicLoad : 1.0;

        String overtrainingRisk;
        if (acwr >= acCfg.getOverttrainingHighRatio()) {
            overtrainingRisk = "HIGH";
        } else if (acwr >= acCfg.getOvertrainingMediumRatio()) {
            overtrainingRisk = "MEDIUM";
        } else {
            overtrainingRisk = "LOW";
        }

        int recoveryHours = switch (loadLevel) {
            case "HIGH" -> 48;
            case "MEDIUM" -> 24;
            default -> 12;
        };

        double weeklyLoadScore = summary.getTotalDurationLast7Days() * (1 + (acwr - 1) * 0.5);

        return EnhancedAnalysisResponse.TrainingLoad.builder()
                .weeklyLoadScore(Math.round(weeklyLoadScore * 10.0) / 10.0)
                .loadLevel(loadLevel)
                .acuteChronicRatio(Math.round(acwr * 100.0) / 100.0)
                .overtrainingRisk(overtrainingRisk)
                .estimatedRecoveryHoursNeeded(recoveryHours)
                .build();
    }

    /**
     * 计算恢复状态评分（Recovery Score 0-100）
     */
    public EnhancedAnalysisResponse.RecoveryStatus analyzeRecovery(Activity currentActivity, UserHistorySummary summary) {
        String trainingLoadLevel = determineTrainingLoadLevel(currentActivity, summary);
        String recoveryStatusStr = determineRecoveryStatus(summary, trainingLoadLevel);

        int score = 100;
        if ("HIGH".equalsIgnoreCase(trainingLoadLevel)) score -= 25;
        else if ("MEDIUM".equalsIgnoreCase(trainingLoadLevel)) score -= 10;
        if ("FATIGUE_RISK".equalsIgnoreCase(recoveryStatusStr)) score -= 30;
        else if ("DETRAINING_RISK".equalsIgnoreCase(recoveryStatusStr)) score -= 15;
        if ("LOW".equalsIgnoreCase(determineConsistencyLevel(summary))) score -= 10;
        score = Math.max(0, Math.min(100, score));

        boolean readyForHighIntensity = score >= 70 && "BALANCED".equalsIgnoreCase(recoveryStatusStr);

        String recommendation = switch (recoveryStatusStr) {
            case "FATIGUE_RISK" -> "优先安排主动恢复或休息，控制下次训练强度";
            case "DETRAINING_RISK" -> "从低强度训练重新建立节奏，避免突然高强度复出";
            default -> score >= 80 ? "恢复状态良好，可按计划推进或适度提升强度"
                    : "保持当前训练节奏，注意睡眠和营养补充";
        };

        return EnhancedAnalysisResponse.RecoveryStatus.builder()
                .status(recoveryStatusStr)
                .recoveryScore(score)
                .inactiveDays(summary.getInactiveDaysSinceLastActivity() == Integer.MAX_VALUE
                        ? 999 : summary.getInactiveDaysSinceLastActivity())
                .readyForHighIntensity(readyForHighIntensity)
                .recommendation(recommendation)
                .build();
    }

    /**
     * 连续性指标
     */
    public EnhancedAnalysisResponse.ConsistencyMetrics analyzeConsistency(UserHistorySummary summary) {
        return EnhancedAnalysisResponse.ConsistencyMetrics.builder()
                .level(determineConsistencyLevel(summary))
                .consecutiveActiveDays(summary.getConsecutiveActiveDays())
                .activitiesLast7Days(summary.getActivitiesLast7Days())
                .activitiesLast30Days(summary.getActivitiesLast30Days())
                .adherenceLevel(summary.getAdherenceLevel())
                .trend(summary.getTrend())
                .build();
    }

    // ─────────────────────────── 核心判断逻辑（基于配置） ───────────────────────────

    public String determineTrainingLoadLevel(Activity currentActivity, UserHistorySummary summary) {
        int currentDuration = currentActivity.getDuration() == null ? 0 : currentActivity.getDuration();
        int weeklyTotal = summary.getTotalDurationLast7Days();
        var loadCfg = props.getLoad();

        if (currentDuration >= loadCfg.getSingleSessionHighMinutes() || weeklyTotal >= loadCfg.getWeeklyHighMinutes()) {
            return "HIGH";
        }
        if (currentDuration >= loadCfg.getSingleSessionMediumMinutes() || weeklyTotal >= loadCfg.getWeeklyMediumMinutes()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public String determineRecoveryStatus(UserHistorySummary summary, String trainingLoadLevel) {
        var recCfg = props.getRecovery();
        if (summary.getInactiveDaysSinceLastActivity() >= recCfg.getDetrainingInactiveDays()
                && summary.getActivitiesLast7Days() <= recCfg.getDetrainingMaxWeeklyActivities()) {
            return "DETRAINING_RISK";
        }
        if ("HIGH".equalsIgnoreCase(trainingLoadLevel)
                || summary.getTotalDurationLast7Days() >= recCfg.getFatigueWeeklyMinutes()) {
            return "FATIGUE_RISK";
        }
        return "BALANCED";
    }

    public String determineConsistencyLevel(UserHistorySummary summary) {
        var conCfg = props.getConsistency();
        if (summary.getActivitiesLast7Days() >= conCfg.getHighWeeklyActivities()
                || summary.getConsecutiveActiveDays() >= conCfg.getHighConsecutiveDays()) {
            return "HIGH";
        }
        if (summary.getActivitiesLast7Days() >= conCfg.getMediumWeeklyActivities()) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
