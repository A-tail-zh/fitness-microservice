package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitnessAssessmentAnalysisRequest {
    private String userId;
    private Integer age;
    private Double height;
    private Double weight;
    private String gender;
    private String goal;
    private Integer weeklyExerciseFrequency;
    private String recentExerciseTime;
    private String injuryStatus;
    private String exerciseExperience;
    private String recentActivitySummary;
    private String ruleLevel;
    private List<ActivityItem> recentActivities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String id;
        private String type;
        private String source;
        private Double distance;
        private Integer duration;
        private Integer durationSeconds;
        private Double calories;
        private Integer avgHeartRate;
        private Integer maxHeartRate;
        private Double avgPace;
        private LocalDateTime startTime;
        private LocalDateTime createdAt;
    }
}
