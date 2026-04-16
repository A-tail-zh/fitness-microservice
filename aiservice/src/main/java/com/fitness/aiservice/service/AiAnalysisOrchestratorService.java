package com.fitness.aiservice.service;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.dto.AiAnalysisContext;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAnalysisOrchestratorService {

    private final ActivityHistoryClient activityHistoryClient;
    private final ActivityHistorySummaryService activityHistorySummaryService;
    private final ActivityRuleEngineService activityRuleEngineService;

    public AiAnalysisContext buildContext(Activity currentActivity) {
        List<Activity> historyActivities = activityHistoryClient.getUserActivities(currentActivity.getUserId()).stream()
                .sorted(Comparator.comparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        UserHistorySummary summary = activityHistorySummaryService.buildSummary(currentActivity.getUserId(), historyActivities);
        RuleAnalysisResult ruleAnalysisResult = activityRuleEngineService.analyze(currentActivity, summary);

        return AiAnalysisContext.builder()
                .currentActivity(currentActivity)
                .recentActivities(historyActivities.stream().limit(10).toList())
                .historySummary(summary)
                .ruleAnalysisResult(ruleAnalysisResult)
                .build();
    }
}
