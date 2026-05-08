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
public class TrainingAnalysisAgentResult {
    private String summary;
    private Integer score;
    private String level;
    private String markdownReport;
    private String structuredAnalysisJson;
    private List<EnhancedAnalysisResponse.RiskAlert> risks;
    private List<EnhancedAnalysisResponse.StructuredSuggestion> suggestions;
    private EnhancedAnalysisResponse.WeeklyPlan nextTrainingPlan;
    private String providerTrace;
    private boolean fallbackUsed;
}
