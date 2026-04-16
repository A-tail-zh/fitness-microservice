package com.fitness.aiservice.dto;

import com.fitness.aiservice.model.Activity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisContext {
    private Activity currentActivity;
    private List<Activity> recentActivities;
    private UserHistorySummary historySummary;
    private RuleAnalysisResult ruleAnalysisResult;
    private UserGoalProfile userGoalProfile;
    private GoalAnalysisResult goalAnalysisResult;
}