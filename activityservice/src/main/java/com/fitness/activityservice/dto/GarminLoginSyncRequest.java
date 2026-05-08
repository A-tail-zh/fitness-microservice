package com.fitness.activityservice.dto;

import lombok.Data;

@Data
public class GarminLoginSyncRequest {
    private String email;
    private String password;
    private String sessionToken;
    private Integer days = 30;
}
