package com.fitness.activityservice.dto;

import com.fitness.activityservice.model.ActivitySource;
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
    private ActivitySource source;
    private String externalActivityId;
    private ActivityType type;
    private Integer duration;
    private Integer calorieBurned;
    private LocalDateTime startTime;
    private LocalDateTime syncedAt;
    private Map<String, Object> additionalMetrics;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
