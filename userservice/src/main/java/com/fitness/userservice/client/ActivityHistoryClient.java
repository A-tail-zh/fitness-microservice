package com.fitness.userservice.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ActivityHistoryClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.activity-service.base-url:http://localhost:8082}")
    private String activityServiceBaseUrl;

    public List<ActivitySnapshot> getUserActivities(String userId) {
        RestClient restClient = restClientBuilder.baseUrl(activityServiceBaseUrl).build();

        return restClient.get()
                .uri("/api/activities")
                .header("X-User-ID", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ActivitySnapshot>>() {
                });
    }

    public List<ActivitySnapshot> getUserActivities(List<String> userIdentifiers) {
        if (userIdentifiers == null || userIdentifiers.isEmpty()) {
            return List.of();
        }

        Map<String, ActivitySnapshot> merged = new LinkedHashMap<>();
        userIdentifiers.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(identifier -> getUserActivities(identifier)
                        .forEach(activity -> merged.putIfAbsent(activity.getId(), activity)));
        return List.copyOf(merged.values());
    }

    @Data
    public static class ActivitySnapshot {
        private String id;
        private String source;
        private String externalActivityId;
        private String type;
        private Integer duration;
        private Integer calorieBurned;
        private LocalDateTime startTime;
        private LocalDateTime syncedAt;
        private Map<String, Object> additionalMetrics;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
