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

    @DeleteMapping("activity/{activityId}")
    public ResponseEntity<Void> deleteActivityRecommendation(@PathVariable String activityId) {
        long deleted = recommendationService.deleteByActivityId(activityId);
        log.info("删除活动历史建议, activityId={}, deleted={}", activityId, deleted);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("activity/{activityId}/download")
    public ResponseEntity<String> downloadActivityRecommendation(@PathVariable String activityId) {
        Recommendation recommendation = recommendationService.getActivityRecommendation(activityId);
        String content = buildDownloadContent(recommendation);
        String fileName = "activity-ai-analysis-" + activityId + ".txt";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(content);
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

    private String buildDownloadContent(Recommendation recommendation) {
        return """
                活动 AI 分析报告
                =================

                活动ID：%s
                用户ID：%s
                运动类型：%s
                生成时间：%s

                %s
                """.formatted(
                recommendation.getActivityId(),
                recommendation.getUserId(),
                safeText(recommendation.getActivityType()),
                recommendation.getCreatedAt() == null ? "未知" : recommendation.getCreatedAt().toString(),
                safeText(recommendation.getRecommendation())
        );
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "暂无" : value;
    }
}
