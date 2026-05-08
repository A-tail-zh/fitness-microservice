package com.fitness.userservice.controller;

import com.fitness.userservice.dto.AssessmentStatusResponse;
import com.fitness.userservice.dto.ExternalFitnessAssessmentRequest;
import com.fitness.userservice.dto.FitnessAssessmentRequest;
import com.fitness.userservice.dto.FitnessAssessmentResult;
import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.UserAssessmentService;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserAssessmentService userAssessmentService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfileById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserProfileById(userId));
    }

    @GetMapping("/keycloak/{keycloakId}")
    public ResponseEntity<UserResponse> getUserProfileByKeycloakId(@PathVariable String keycloakId) {
        return ResponseEntity.ok(userService.getUserProfileByKeycloakId(keycloakId));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUserProfile(@RequestHeader("X-User-ID") String userIdentifier) {
        return ResponseEntity.ok(userService.getCurrentUserProfile(userIdentifier));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @GetMapping("/me/assessment/status")
    public ResponseEntity<AssessmentStatusResponse> getAssessmentStatus(
            @RequestHeader("X-User-ID") String userIdentifier) {
        return ResponseEntity.ok(userAssessmentService.getAssessmentStatus(userIdentifier));
    }

    @PostMapping("/me/assessment")
    public ResponseEntity<FitnessAssessmentResult> submitAssessment(
            @RequestHeader("X-User-ID") String userIdentifier,
            @Valid @RequestBody FitnessAssessmentRequest request) {
        return ResponseEntity.ok(userAssessmentService.submitAssessment(userIdentifier, request));
    }

    @PostMapping("/me/assessment/external")
    public ResponseEntity<FitnessAssessmentResult> applyExternalAssessment(
            @RequestHeader("X-User-ID") String userIdentifier,
            @RequestBody ExternalFitnessAssessmentRequest request) {
        return ResponseEntity.ok(userAssessmentService.applyExternalAssessment(userIdentifier, request));
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.existByUserId(userId));
    }
}
