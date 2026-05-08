package com.fitness.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_fitness_assessment")
@Data
public class UserFitnessAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    private Integer age;
    private Double height;
    private Double weight;
    private String gender;
    private String goal;
    private Integer weeklyExerciseFrequency;

    @Column(length = 500)
    private String recentExerciseTime;

    @Column(length = 1000)
    private String recentActivitySummary;

    @Column(length = 500)
    private String injuryStatus;

    @Column(length = 500)
    private String exerciseExperience;

    @Enumerated(EnumType.STRING)
    private FitnessLevel ruleLevel;

    @Enumerated(EnumType.STRING)
    private FitnessLevel aiLevel;

    @Enumerated(EnumType.STRING)
    private FitnessLevel finalLevel;

    @Column(columnDefinition = "TEXT")
    private String aiReport;

    @Column(length = 1000)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(length = 1000)
    private String riskWarning;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
