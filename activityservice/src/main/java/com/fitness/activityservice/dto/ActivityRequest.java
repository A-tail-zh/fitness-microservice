package com.fitness.activityservice.dto;

import com.fitness.activityservice.model.ActivityType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ActivityRequest {
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotNull(message = "运动类型不能为空")
    private ActivityType type;

    @NotNull(message = "训练时长不能为空")
    @Min(value = 1, message = "训练时长必须大于0")
    private Integer duration;

    @NotNull(message = "消耗卡路里不能为空")
    @Min(value = 0, message = "消耗卡路里不能为负数")
    private Integer calorieBurned;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    private Map<String, Object> additionalMetrics;
}
