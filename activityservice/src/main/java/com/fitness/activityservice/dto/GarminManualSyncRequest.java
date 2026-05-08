package com.fitness.activityservice.dto;

import lombok.Data;

@Data
public class GarminManualSyncRequest {
    private Integer days = 30;
}
