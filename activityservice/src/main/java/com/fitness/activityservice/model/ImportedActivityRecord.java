package com.fitness.activityservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "imported_activity_records")
@CompoundIndex(name = "uk_platform_activity", def = "{'platform': 1, 'externalActivityId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportedActivityRecord {
    @Id
    private String id;
    private String userId;
    private ThirdPartyPlatform platform;
    private String externalActivityId;
    private String activityType;
    private Double distance;
    private Integer durationSeconds;
    private Double calories;
    private Integer avgHeartRate;
    private Integer maxHeartRate;
    private Double avgPace;
    private LocalDateTime startTime;
    private Map<String, Object> rawData;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
