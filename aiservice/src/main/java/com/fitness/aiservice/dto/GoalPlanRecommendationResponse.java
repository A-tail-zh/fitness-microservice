package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalPlanRecommendationResponse {
    private String goalType;
    private BigDecimal targetValue;
    private String targetUnit;
    private Integer weeklyTargetFrequency;
    private Integer weeklyTargetDuration;
    private LocalDate targetDate;
    private String experienceLevel;
    private String priority;
    private String note;
    private Integer feasibilityScore;
    private String reason;
    private String riskWarning;
    private List<String> weeklyPlan;
    private List<String> milestones;
    private String rawAiReport;
}
