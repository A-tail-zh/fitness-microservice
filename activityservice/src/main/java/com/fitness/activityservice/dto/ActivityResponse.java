package com.fitness.activityservice.dto;

import com.fitness.activityservice.model.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {
    private String id;
    private String userId;
    private ActivityType type;
    private Integer duration;//持续时间
    private Integer calorieBurned;
    private LocalDateTime startTime;
    private Map<String,Object> additionalMetrics;//额外的指标
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


}
