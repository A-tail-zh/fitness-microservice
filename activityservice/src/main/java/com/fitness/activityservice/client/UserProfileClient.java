package com.fitness.activityservice.client;

import com.fitness.activityservice.dto.GarminSyncResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UserProfileClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.user-service.base-url:http://localhost:8081}")
    private String userServiceBaseUrl;

    public UserProfileResponse getCurrentUserProfile(String userIdentifier) {
        RestClient restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();
        return restClient.get()
                .uri("/api/users/me")
                .header("X-User-ID", userIdentifier)
                .retrieve()
                .body(UserProfileResponse.class);
    }

    public ExternalAssessmentResponse updateExternalAssessment(
            String userIdentifier,
            ExternalAssessmentUpdateRequest request) {
        RestClient restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();
        return restClient.post()
                .uri("/api/users/me/assessment/external")
                .header("X-User-ID", userIdentifier)
                .body(request)
                .retrieve()
                .body(ExternalAssessmentResponse.class);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileResponse {
        private String id;
        private String keycloakId;
        private String email;
        private String firstName;
        private String lastName;
        private Integer age;
        private Double height;
        private Double weight;
        private String gender;
        private String goal;
        private String injuryStatus;
        private String fitnessLevel;
        private Boolean assessmentCompleted;
        private String assessmentReport;
        private LocalDateTime assessmentUpdatedAt;
        private LocalDate createdAt;
        private LocalDate updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalAssessmentUpdateRequest {
        private String activitySource;
        private String ruleLevel;
        private String aiLevel;
        private String finalLevel;
        private String summary;
        private String reason;
        private String suggestion;
        private String riskWarning;
        private String recentActivitySummary;
        private String aiReport;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalAssessmentResponse {
        private Boolean assessmentCompleted;
        private String ruleLevel;
        private String aiLevel;
        private String finalLevel;
        private String summary;
        private String reason;
        private String suggestion;
        private String riskWarning;
        private String recentActivitySummary;
        private String aiReport;
        private LocalDateTime assessedAt;
    }
}
