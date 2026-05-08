package com.fitness.activityservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GarminBindStatusResponse {
    private boolean bound;
    private boolean sessionStored;
    private String bindStatus;
    private String externalUserId;
    private LocalDateTime tokenExpireTime;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastSyncMessage;
    private String syncMode;
    private boolean officialApiReserved;
    private boolean fitImportReserved;
}
