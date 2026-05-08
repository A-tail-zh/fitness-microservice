package com.fitness.aiservice.client;

import lombok.AllArgsConstructor;
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

    public UserProfileResponse resolveUser(String identifier) {
        RestClient restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();
        try {
            return restClient.get()
                    .uri("/api/users/{userId}", identifier)
                    .retrieve()
                    .body(UserProfileResponse.class);
        } catch (Exception ignored) {
            return restClient.get()
                    .uri("/api/users/keycloak/{keycloakId}", identifier)
                    .retrieve()
                    .body(UserProfileResponse.class);
        }
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
}
