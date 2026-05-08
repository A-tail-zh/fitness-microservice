package com.fitness.userservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class GoalNotificationClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.ai-service.base-url:http://localhost:8083}")
    private String aiServiceBaseUrl;

    public void sendGoalCompletedEmail(GoalCompletedNotificationRequest request) {
        RestClient restClient = restClientBuilder.baseUrl(aiServiceBaseUrl).build();
        restClient.post()
                .uri("/api/notifications/goal-completed")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalCompletedNotificationRequest {
        private String userId;
        private String email;
        private String username;
        private String goalId;
        private String goalName;
        private String goalType;
        private String completedAt;
        private String goalPeriod;
        private String completionDescription;
        private String nextStepAdvice;
        private Double distance;
        private Integer durationSeconds;
        private Integer avgHeartRate;
        private Double calories;
        private String aiSuggestion;
    }
}
