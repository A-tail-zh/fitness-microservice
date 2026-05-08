package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitnessAssessmentAnalysisResponse {
    private String fitnessLevel;
    private String summary;
    private String reason;
    private String suggestion;
    private String trainingSuggestion;
    private String riskWarning;
    private String rawReport;
}
