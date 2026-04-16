package com.fitness.userservice.controller;

import com.fitness.userservice.dto.UserGoalRequest;
import com.fitness.userservice.dto.UserGoalResponse;
import com.fitness.userservice.dto.UserGoalStatusUpdateRequest;
import com.fitness.userservice.service.UserGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/goals")
@RequiredArgsConstructor
public class UserGoalController {

    private final UserGoalService userGoalService;

    @PostMapping
    public ResponseEntity<UserGoalResponse> createGoal(@PathVariable String userId,
                                                       @Valid @RequestBody UserGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userGoalService.createGoal(userId, request));
    }

    @GetMapping("/active")
    public ResponseEntity<UserGoalResponse> getActiveGoal(@PathVariable String userId) {
        return ResponseEntity.ok(userGoalService.getActiveGoal(userId));
    }

    @GetMapping
    public ResponseEntity<List<UserGoalResponse>> getAllGoals(@PathVariable String userId) {
        return ResponseEntity.ok(userGoalService.getAllGoals(userId));
    }

    @PutMapping("/{goalId}")
    public ResponseEntity<UserGoalResponse> updateGoal(@PathVariable String userId,
                                                       @PathVariable String goalId,
                                                       @Valid @RequestBody UserGoalRequest request) {
        return ResponseEntity.ok(userGoalService.updateGoal(userId, goalId, request));
    }

    @PatchMapping("/{goalId}/status")
    public ResponseEntity<UserGoalResponse> updateGoalStatus(@PathVariable String userId,
                                                             @PathVariable String goalId,
                                                             @Valid @RequestBody UserGoalStatusUpdateRequest request) {
        return ResponseEntity.ok(userGoalService.updateGoalStatus(userId, goalId, request.getStatus()));
    }
}