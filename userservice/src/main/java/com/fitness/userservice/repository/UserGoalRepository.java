package com.fitness.userservice.repository;

import com.fitness.userservice.model.GoalStatus;
import com.fitness.userservice.model.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserGoalRepository extends JpaRepository<UserGoal, String> {

    List<UserGoal> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<UserGoal> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(String userId, GoalStatus status);

    List<UserGoal> findByUserIdAndStatus(String userId, GoalStatus status);
}