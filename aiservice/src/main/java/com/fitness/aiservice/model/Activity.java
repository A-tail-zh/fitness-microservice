package com.fitness.aiservice.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class Activity {
    private String id;
    private String userId;
    private String type;
    private Integer duration;//持续时间
    private Integer calorieBurned;
    private LocalDateTime startTime;
    private Map<String,Object> additionalMetrics;//额外的指标
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
