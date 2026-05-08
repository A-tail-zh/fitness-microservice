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
public class StandardActivityDTO {
    private String userId;
    private ActivitySource source;
    private String externalActivityId;
    private ActivityType type;
    private Double distance;
    private Integer durationSeconds;
    private Double calories;
    private Integer avgHeartRate;
    private Integer maxHeartRate;
    private Double avgPace;
    private LocalDateTime startTime;
    private Map<String, Object> rawData;
}
