package com.fitness.userservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitnessAssessmentAiResponse {
    private String fitnessLevel;
    private String summary;
    private String reason;
    private String suggestion;
    private String riskWarning;
    private String rawReport;
}
