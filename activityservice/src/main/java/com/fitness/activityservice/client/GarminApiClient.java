package com.fitness.activityservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GarminApiClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${garmin.client-id:}")
    private String clientId;

    @Value("${garmin.client-secret:}")
    private String clientSecret;

    @Value("${garmin.redirect-uri:}")
    private String redirectUri;

    @Value("${garmin.token-url:}")
    private String tokenUrl;

    @Value("${garmin.activity-url:}")
    private String activityUrl;

    public GarminTokenResponse exchangeCodeForToken(String code) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return requestToken(form);
    }

    public GarminTokenResponse refreshAccessToken(String refreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return requestToken(form);
    }

    public List<GarminActivityItem> fetchActivities(String accessToken, LocalDateTime startTime, LocalDateTime endTime) {
        String url = UriComponentsBuilder.fromUriString(activityUrl)
                .queryParam("startTime", formatTime(startTime))
                .queryParam("endTime", formatTime(endTime))
                .build(true)
                .toUriString();

        RestClient restClient = restClientBuilder.build();
        String raw = restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        return parseActivities(raw);
    }

    private GarminTokenResponse requestToken(LinkedMultiValueMap<String, String> form) {
        RestClient restClient = restClientBuilder.build();
        String basicToken = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        String raw = restClient.post()
                .uri(tokenUrl)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicToken)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(raw);
            Integer expiresIn = root.path("expires_in").isMissingNode() ? null : root.path("expires_in").asInt();
            return GarminTokenResponse.builder()
                    .accessToken(text(root, "access_token"))
                    .refreshToken(text(root, "refresh_token"))
                    .externalUserId(firstText(root, "user_id", "sub", "external_user_id"))
                    .expiresIn(expiresIn)
                    .scope(text(root, "scope"))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("解析 Garmin Token 响应失败", ex);
        }
    }

    private List<GarminActivityItem> parseActivities(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode listNode = root;
            if (!root.isArray()) {
                listNode = firstArray(root, "activities", "items", "data");
            }
            if (listNode == null || !listNode.isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(listNode, new TypeReference<List<Map<String, Object>>>() {})
                    .stream()
                    .map(this::toActivityItem)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("解析 Garmin 活动列表失败", ex);
        }
    }

    private GarminActivityItem toActivityItem(Map<String, Object> rawData) {
        JsonNode node = objectMapper.valueToTree(rawData);
        return GarminActivityItem.builder()
                .activityId(firstText(node, "activityId", "activity_id", "id", "summaryId"))
                .activityType(firstText(node, "activityType", "activity_type", "type", "sport"))
                .distance(firstNumber(node, "distanceInMeters", "distance", "totalDistance"))
                .durationSeconds(firstInteger(node, "durationInSeconds", "duration", "movingDuration"))
                .calories(firstNumber(node, "activeKilocalories", "calories", "activeCalories"))
                .avgHeartRate(firstInteger(node,
                        "averageHeartRateInBeatsPerMinute",
                        "averageHeartRate",
                        "avgHeartRate"))
                .maxHeartRate(firstInteger(node,
                        "maxHeartRateInBeatsPerMinute",
                        "maxHeartRate"))
                .avgPace(firstNumber(node,
                        "averagePaceInMetersPerSecond",
                        "averagePace",
                        "avgPace"))
                .startTime(parseDateTime(firstText(node,
                        "startTime",
                        "startTimeLocal",
                        "start_time",
                        "startDate")))
                .rawData(rawData)
                .build();
    }

    private JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.path(name);
            if (node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... names) {
        for (String name : names) {
            String value = text(root, name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode root, String name) {
        JsonNode node = root.path(name);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private Double firstNumber(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.path(name);
            if (!node.isMissingNode() && node.isNumber()) {
                return node.asDouble();
            }
            if (!node.isMissingNode() && node.isTextual()) {
                try {
                    return Double.parseDouble(node.asText());
                } catch (NumberFormatException ignored) {
                    log.debug("Garmin 字段不是数字，field={}, value={}", name, node.asText());
                }
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode root, String... names) {
        Double number = firstNumber(root, names);
        return number == null ? null : (int) Math.round(number);
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT));
        } catch (Exception ignored) {
        }

        return null;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarminTokenResponse {
        private String accessToken;
        private String refreshToken;
        private String externalUserId;
        private Integer expiresIn;
        private String scope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarminActivityItem {
        private String activityId;
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
