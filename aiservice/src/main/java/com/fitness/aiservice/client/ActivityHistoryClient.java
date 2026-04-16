package com.fitness.aiservice.client;

import com.fitness.aiservice.exception.AiAnalysisException;
import com.fitness.aiservice.model.Activity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class ActivityHistoryClient {

    private final WebClient webClient;

    public ActivityHistoryClient(WebClient.Builder webClientBuilder,
                                 @Value("${services.activity-service.base-url:http://localhost:8082}") String activityServiceBaseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(activityServiceBaseUrl)
                .build();
    }

    public List<Activity> getUserActivities(String userId) {
        try {
            return webClient.get()
                    .uri("/api/activities")
                    .header("X-User-ID", userId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Activity>>() {})
                    .timeout(Duration.ofSeconds(15))
                    .blockOptional()
                    .orElse(List.of());
        } catch (Exception e) {
            log.error("查询用户历史活动失败, userId={}", userId, e);
            throw new AiAnalysisException("查询历史活动失败，无法生成基于历史记录的智能分析", e);
        }
    }
}
