package com.fitness.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GarminAuthUrlResponse {
    private String authUrl;
}
