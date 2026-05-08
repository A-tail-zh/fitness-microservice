package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalAnalysisResult {
    private String goalType;
    private String progressStatus;
    private String alignmentLevel;
    private Integer completionScore;
    private Integer alignmentScore;
    private Integer feasibilityScore;
    private Integer estimatedWeeksToGoal;
    private String nextMilestone;
    private String adjustmentReason;
    private String recommendedGoalRevision;
    private List<String> strengths;
    private List<String> gaps;
    private List<String> actionSuggestions;
}
