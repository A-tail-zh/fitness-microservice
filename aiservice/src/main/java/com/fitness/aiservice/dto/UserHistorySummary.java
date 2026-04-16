package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserHistorySummary {
    private String userId;
    private int totalActivities;
    private int activitiesLast7Days;
    private int activitiesLast30Days;
    private int totalDurationLast7Days;
    private int totalDurationLast30Days;
    private int totalCaloriesLast7Days;
    private int totalCaloriesLast30Days;

    private double avgCaloriesLast7Days;
    private double avgCaloriesLast30Days;

    private double avgDurationLast7Days;
    private double avgDurationLast30Days;

    private String mostFrequentActivityType;
    private int consecutiveActiveDays;
    private int inactiveDaysSinceLastActivity;
    private String trend;
    private String adherenceLevel;

    private LocalDateTime firstActivityTime;
    private LocalDateTime latestActivityTime;
    private List<String> recentActivityTypes;

    private Map<String, Long> activityTypeDistribution;
}