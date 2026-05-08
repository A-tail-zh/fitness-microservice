package com.fitness.aiservice.dto;

import lombok.Data;

@Data
public class ReportEmailRequest {
    private String userId;
    private String activityId;
    private String reportType = "DAILY";
    private String userNote;
}
