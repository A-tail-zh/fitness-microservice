package com.fitness.aiservice.dto;

import lombok.Data;

@Data
public class GoalCompletedNotificationRequest {
    private String userId;
    private String email;
    private String username;
    private String goalId;
    private String goalName;
    private String goalType;
    private String completedAt;
    private String goalPeriod;
    private String completionDescription;
    private String nextStepAdvice;
    private Double distance;
    private Integer durationSeconds;
    private Integer avgHeartRate;
    private Double calories;
    private String aiSuggestion;
}
