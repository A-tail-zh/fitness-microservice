package com.fitness.userservice.dto;

import com.fitness.userservice.model.ExperienceLevel;
import com.fitness.userservice.model.GoalPriority;
import com.fitness.userservice.model.GoalType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UserGoalRequest {

    @NotNull(message = "目标类型不能为空")
    private GoalType goalType;

    private BigDecimal targetValue;

    private String targetUnit;

    private Integer weeklyTargetFrequency;

    private Integer weeklyTargetDuration;

    private LocalDate targetDate;

    private ExperienceLevel experienceLevel;

    private GoalPriority priority;

    private String note;
}