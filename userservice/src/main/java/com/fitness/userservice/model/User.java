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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String keycloakId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;
    private Integer age;
    private Double height;
    private Double weight;
    private String gender;
    private String goal;
    private String injuryStatus;
    private String fitnessLevel;
    private Boolean assessmentCompleted = Boolean.FALSE;

    @Column(columnDefinition = "TEXT")
    private String assessmentReport;

    private LocalDateTime assessmentUpdatedAt;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    @CreationTimestamp
    private LocalDate createdAt;

    @UpdateTimestamp
    private LocalDate updatedAt;
}
