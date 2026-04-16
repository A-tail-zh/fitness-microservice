package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityRuleEngineService {

    public RuleAnalysisResult analyze(Activity currentActivity, UserHistorySummary summary) {
        List<String> highlights = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        String trainingLoadLevel = determineTrainingLoadLevel(currentActivity, summary);
        String recoveryStatus = determineRecoveryStatus(summary, trainingLoadLevel);
        String consistencyLevel = determineConsistencyLevel(summary);

        if ("HIGH".equalsIgnoreCase(trainingLoadLevel)) {
            risks.add("近期训练负荷偏高，若恢复不足，可能影响后续训练质量");
            suggestions.add("下一次训练建议适当降强度或减少训练时长，优先保证恢复");
        } else if ("MEDIUM".equalsIgnoreCase(trainingLoadLevel)) {
            highlights.add("当前训练负荷处于中等水平，整体可控");
        } else {
            highlights.add("当前训练负荷较低，适合作为恢复或重新建立节奏的阶段");
        }

        if ("FATIGUE_RISK".equalsIgnoreCase(recoveryStatus)) {
            risks.add("系统识别到疲劳风险，说明近期训练推进和恢复节奏不够平衡");
            suggestions.add("建议安排 1-2 天低强度训练或主动恢复，避免继续叠加高负荷");
        } else if ("DETRAINING_RISK".equalsIgnoreCase(recoveryStatus)) {
            risks.add("近期训练间隔偏长，存在能力退化和节奏中断风险");
            suggestions.add("建议先恢复规律训练频率，从低到中等强度重新建立连续性");
        } else {
            highlights.add("当前恢复状态整体平衡，可继续按计划推进训练");
        }

        if ("HIGH".equalsIgnoreCase(consistencyLevel)) {
            highlights.add("近期训练连续性较好，有利于能力稳定提升");
        } else if ("MEDIUM".equalsIgnoreCase(consistencyLevel)) {
            highlights.add("训练连续性一般，仍有进一步稳定节奏的空间");
        } else {
            risks.add("训练连续性偏弱，容易影响长期进步");
            suggestions.add("优先保证每周固定训练次数，先建立连续性，再追求更高强度");
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

    private String determineTrainingLoadLevel(Activity currentActivity, UserHistorySummary summary) {
        int currentDuration = currentActivity.getDuration() == null ? 0 : currentActivity.getDuration();
        int totalDurationLast7Days = summary.getTotalDurationLast7Days();

        if (currentDuration >= 90 || totalDurationLast7Days >= 300) {
            return "HIGH";
        }
        if (currentDuration >= 45 || totalDurationLast7Days >= 150) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String determineRecoveryStatus(UserHistorySummary summary, String trainingLoadLevel) {
        if (summary.getInactiveDaysSinceLastActivity() >= 5 && summary.getActivitiesLast7Days() <= 1) {
            return "DETRAINING_RISK";
        }
        if ("HIGH".equalsIgnoreCase(trainingLoadLevel) || summary.getTotalDurationLast7Days() >= 300) {
            return "FATIGUE_RISK";
        }
        return "BALANCED";
    }

    private String determineConsistencyLevel(UserHistorySummary summary) {
        if (summary.getActivitiesLast7Days() >= 4 || summary.getConsecutiveActiveDays() >= 3) {
            return "HIGH";
        }
        if (summary.getActivitiesLast7Days() >= 2) {
            return "MEDIUM";
        }
        return "LOW";
    }
}