package com.fitness.userservice.dto;

import lombok.Data;

@Data
public class ExternalFitnessAssessmentRequest {
    private String activitySource;
    private String ruleLevel;
    private String aiLevel;
    private String finalLevel;
    private String summary;
    private String reason;
    private String suggestion;
    private String riskWarning;
    private String recentActivitySummary;
    private String aiReport;
}
