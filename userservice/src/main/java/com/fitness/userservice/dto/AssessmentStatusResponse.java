package com.fitness.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentStatusResponse {
    private Boolean assessmentCompleted;
    private String fitnessLevel;
    private LocalDateTime assessmentUpdatedAt;
}
