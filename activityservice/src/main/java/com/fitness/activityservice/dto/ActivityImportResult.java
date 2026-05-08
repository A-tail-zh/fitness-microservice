package com.fitness.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityImportResult {
    private String status;
    private ActivityResponse activity;
}
