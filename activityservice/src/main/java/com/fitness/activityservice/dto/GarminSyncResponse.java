package com.fitness.activityservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GarminSyncResponse {
    private int totalFetched;
    private int importedCount;
    private int skippedCount;
    private String fitnessLevel;
    private String summary;
    private String sessionToken;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String message;
}
