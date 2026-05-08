package com.fitness.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String keycloakId;
    private String email;
    private String firstName;
    private String lastName;
    private Integer age;
    private Double height;
    private Double weight;
    private String gender;
    private String goal;
    private String injuryStatus;
    private String fitnessLevel;
    private Boolean assessmentCompleted;
    private String assessmentReport;
    private LocalDateTime assessmentUpdatedAt;
    private LocalDate createdAt;
    private LocalDate updatedAt;
}
