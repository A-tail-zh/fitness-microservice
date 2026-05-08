package com.fitness.userservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitnessAssessmentAiRequest {
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
    private List<AiActivityItem> recentActivities;
}
