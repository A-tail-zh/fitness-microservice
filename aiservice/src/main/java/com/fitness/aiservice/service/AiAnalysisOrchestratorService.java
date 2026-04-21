package com.fitness.aiservice.service;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.client.UserGoalClient;
import com.fitness.aiservice.dto.AiAnalysisContext;
import com.fitness.aiservice.dto.GoalAnalysisResult;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserGoalProfile;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisOrchestratorService {

    private final ActivityHistoryClient activityHistoryClient;
    private final ActivityHistorySummaryService activityHistorySummaryService;
    private final ActivityRuleEngineService activityRuleEngineService;
    private final UserGoalClient userGoalClient;
    private final GoalAnalysisService goalAnalysisService;

    public AiAnalysisContext buildContext(Activity currentActivity) {
        String userId = currentActivity.getUserId();

        List<Activity> historyActivities = activityHistoryClient.getUserActivities(userId).stream()
                .sorted(Comparator.comparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        UserHistorySummary summary = activityHistorySummaryService.buildSummary(userId, historyActivities);
        RuleAnalysisResult ruleAnalysisResult = activityRuleEngineService.analyze(currentActivity, summary);

        UserGoalProfile userGoalProfile = fetchGoalSafely(userId);
        List<Activity> recentActivities = historyActivities.stream().limit(10).toList();
        GoalAnalysisResult goalAnalysisResult = goalAnalysisService.analyze(
                userGoalProfile, summary, ruleAnalysisResult, recentActivities);

        return AiAnalysisContext.builder()
                .currentActivity(currentActivity)
                .recentActivities(recentActivities)
                .historySummary(summary)
                .ruleAnalysisResult(ruleAnalysisResult)
                .userGoalProfile(userGoalProfile)
                .goalAnalysisResult(goalAnalysisResult)
                .build();
    }

    private UserGoalProfile fetchGoalSafely(String userId) {
        try {
            return userGoalClient.getCurrentGoal(userId);
        } catch (Exception e) {
            log.warn("获取用户目标失败，将跳过目标分析, userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
