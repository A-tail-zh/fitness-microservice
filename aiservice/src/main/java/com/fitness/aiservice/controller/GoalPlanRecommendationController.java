package com.fitness.aiservice.controller;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.dto.GoalPlanRecommendationRequest;
import com.fitness.aiservice.dto.GoalPlanRecommendationResponse;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.service.GoalPlanRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/ai-analysis/goals")
@RequiredArgsConstructor
public class GoalPlanRecommendationController {

    private final ActivityHistoryClient activityHistoryClient;
    private final GoalPlanRecommendationService goalPlanRecommendationService;

    @PostMapping("/recommendation")
    public ResponseEntity<GoalPlanRecommendationResponse> recommendGoal(
            @RequestBody GoalPlanRecommendationRequest request) {
        List<Activity> activities = activityHistoryClient.getUserActivities(request.getUserId());
        return ResponseEntity.ok(goalPlanRecommendationService.recommend(request, activities));
    }
}
