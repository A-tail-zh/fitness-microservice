package com.fitness.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitnessAssessmentResult {
    private Boolean assessmentCompleted;
    private String ruleLevel;
    private String aiLevel;
    private String finalLevel;
    private String summary;
    private String reason;
    private String suggestion;
    private String riskWarning;
    private String recentActivitySummary;
    private String aiReport;
    private LocalDateTime assessedAt;
}
