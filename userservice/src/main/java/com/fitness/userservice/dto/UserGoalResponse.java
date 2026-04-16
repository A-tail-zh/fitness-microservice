package com.fitness.userservice.dto;

import com.fitness.userservice.model.ExperienceLevel;
import com.fitness.userservice.model.GoalPriority;
import com.fitness.userservice.model.GoalStatus;
import com.fitness.userservice.model.GoalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class UserGoalResponse {
    private String id;
    private String userId;
    private GoalType goalType;
    private BigDecimal targetValue;
    private String targetUnit;
    private Integer weeklyTargetFrequency;
    private Integer weeklyTargetDuration;
    private LocalDate targetDate;
    private ExperienceLevel experienceLevel;
    private GoalPriority priority;
    private GoalStatus status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}