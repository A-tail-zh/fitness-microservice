package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedAnalysisRequest {
    private String activityId;
    private String userId;
    private String reportType;
    private String userNote;
    private Boolean sendEmail;
}
