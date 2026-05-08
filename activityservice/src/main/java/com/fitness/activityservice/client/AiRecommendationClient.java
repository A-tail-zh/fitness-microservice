package com.fitness.activityservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiRecommendationClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.ai-service.base-url:http://localhost:8083}")
    private String aiServiceBaseUrl;

    public void deleteByActivityId(String activityId) {
        try {
            restClientBuilder.baseUrl(aiServiceBaseUrl)
                    .build()
                    .delete()
                    .uri("/api/recommendations/activity/{activityId}", activityId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("删除活动对应 AI 历史建议失败，activityId={}, reason={}", activityId, ex.getMessage());
        }
    }
}
