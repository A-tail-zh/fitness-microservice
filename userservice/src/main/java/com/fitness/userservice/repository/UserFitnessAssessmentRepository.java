package com.fitness.userservice.repository;

import com.fitness.userservice.model.UserFitnessAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFitnessAssessmentRepository extends JpaRepository<UserFitnessAssessment, String> {
    Optional<UserFitnessAssessment> findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
