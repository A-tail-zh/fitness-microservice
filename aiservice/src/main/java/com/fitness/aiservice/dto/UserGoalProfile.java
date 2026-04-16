package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGoalProfile {
    private String id;
    private String userId;
    private String goalType;
    private BigDecimal targetValue;
    private String targetUnit;
    private Integer weeklyTargetFrequency;
    private Integer weeklyTargetDuration;
    private LocalDate targetDate;
    private String experienceLevel;
    private String priority;
    private String status;
    private String note;
}