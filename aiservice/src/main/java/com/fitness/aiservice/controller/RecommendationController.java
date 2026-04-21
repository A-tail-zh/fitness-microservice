package com.fitness.aiservice.controller;

import com.fitness.aiservice.client.ActivityHistoryClient;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.ActivityAiService;
import com.fitness.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ActivityAiService activityAiService;
    private final ActivityHistoryClient activityHistoryClient;

    @GetMapping("user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserCommendation(@PathVariable String userId) {
        return ResponseEntity.ok(recommendationService.getUserCommendation(userId));
    }

    @GetMapping("activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityCommendation(@PathVariable String activityId) {
        return ResponseEntity.ok(recommendationService.getActivityRecommendation(activityId));
    }

    /**
     * 手动触发为指定活动生成 AI 建议（同步）
     *
     * <p>POST /api/recommendations/generate?userId=xxx&activityId=yyy
     */
    @PostMapping("generate")
    public ResponseEntity<Recommendation> generate(
            @RequestParam String userId,
            @RequestParam String activityId) {
        log.info("手动触发建议生成, userId={}, activityId={}", userId, activityId);

        Activity activity = activityHistoryClient.getUserActivities(userId).stream()
                .filter(a -> activityId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到活动: " + activityId));

        Recommendation recommendation = activityAiService.generateAndSaveRecommendation(activity);
        return ResponseEntity.ok(recommendation);
    }
}
