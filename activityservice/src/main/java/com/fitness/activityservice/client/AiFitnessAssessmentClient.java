package com.fitness.activityservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AiFitnessAssessmentClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.ai-service.base-url:http://localhost:8083}")
    private String aiServiceBaseUrl;

    public FitnessAssessmentAiResponse analyze(FitnessAssessmentAiRequest request) {
        RestClient restClient = restClientBuilder.baseUrl(aiServiceBaseUrl).build();
        return restClient.post()
                .uri("/api/ai-analysis/fitness-assessment")
                .body(request)
                .retrieve()
                .body(FitnessAssessmentAiResponse.class);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FitnessAssessmentAiRequest {
        private String userId;
        private Integer age;
        private Double height;
        private Double weight;
        private String gender;
        private String goal;
        private Integer weeklyExerciseFrequency;
        private String recentExerciseTime;
        private String injuryStatus;
        private String exerciseExperience;
        private String recentActivitySummary;
        private String ruleLevel;
        private List<ActivityItem> recentActivities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String id;
        private String type;
        private String source;
        private Double distance;
        private Integer duration;
        private Integer durationSeconds;
        private Double calories;
        private Integer avgHeartRate;
        private Integer maxHeartRate;
        private Double avgPace;
        private LocalDateTime startTime;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FitnessAssessmentAiResponse {
        private String fitnessLevel;
        private String summary;
        private String reason;
        private String suggestion;
        private String trainingSuggestion;
        private String riskWarning;
        private String rawReport;
    }
}
