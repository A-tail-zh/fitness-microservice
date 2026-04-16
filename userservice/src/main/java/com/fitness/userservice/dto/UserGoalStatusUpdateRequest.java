package com.fitness.userservice.dto;

import com.fitness.userservice.model.GoalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserGoalStatusUpdateRequest {

    @NotNull(message = "目标状态不能为空")
    private GoalStatus status;
}