package com.fitness.activityservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GarminPythonSyncClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${garmin-python-service.base-url:http://localhost:8090}")
    private String garminPythonServiceBaseUrl;

    public GarminSessionResponse login(GarminSessionRequest request) {
        RestClient restClient = restClientBuilder.baseUrl(garminPythonServiceBaseUrl).build();
        try {
            return restClient.post()
                    .uri("/garmin/login")
                    .body(request)
                    .retrieve()
                    .body(GarminSessionResponse.class);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("无法连接 Garmin Python 同步服务，请先启动 http://localhost:8090", ex);
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new IllegalStateException(body == null || body.isBlank()
                    ? "Garmin Python 同步服务调用失败，状态码=" + ex.getStatusCode().value()
                    : body, ex);
        }
    }

    public List<GarminActivityItem> fetchActivities(String sessionToken, int days) {
        RestClient restClient = restClientBuilder.baseUrl(garminPythonServiceBaseUrl).build();
        GarminActivitiesResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/garmin/activities")
                            .queryParam("days", days)
                            .build())
                    .header("X-Garmin-Session", sessionToken)
                    .retrieve()
                    .body(GarminActivitiesResponse.class);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("无法连接 Garmin Python 同步服务，请先启动 http://localhost:8090", ex);
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new IllegalStateException(body == null || body.isBlank()
                    ? "Garmin 活动拉取失败，状态码=" + ex.getStatusCode().value()
                    : body, ex);
        }
        return response == null || response.getActivities() == null
                ? Collections.emptyList()
                : response.getActivities();
    }

    public void deleteSession(String sessionToken) {
        RestClient restClient = restClientBuilder.baseUrl(garminPythonServiceBaseUrl).build();
        try {
            restClient.delete()
                    .uri("/garmin/session")
                    .header("X-Garmin-Session", sessionToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("无法连接 Garmin Python 同步服务，请先启动 http://localhost:8090", ex);
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new IllegalStateException(body == null || body.isBlank()
                    ? "Garmin 本地会话删除失败，状态码=" + ex.getStatusCode().value()
                    : body, ex);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarminSessionRequest {
        private String email;
        private String password;
        private String sessionToken;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarminSessionResponse {
        private String sessionToken;
        private String displayName;
        private String externalUserId;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarminActivitiesResponse {
        private int days;
        private int total;
        private List<GarminActivityItem> activities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarminActivityItem {
        private String activityId;
        private String activityName;
        private String activityType;
        private Double distance;
        private Integer durationSeconds;
        private Double calories;
        private Integer avgHeartRate;
        private Integer maxHeartRate;
        private Double avgPace;
        private LocalDateTime startTime;
        private Map<String, Object> rawData;
    }
}
