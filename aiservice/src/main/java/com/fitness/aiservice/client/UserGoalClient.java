package com.fitness.aiservice.client;

import com.fitness.aiservice.dto.UserGoalProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserGoalClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.user-service.base-url:http://localhost:8081}")
    private String userServiceBaseUrl;

    public UserGoalProfile getCurrentGoal(String userId) {
        RestClient restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();

        try {
            return restClient.get()
                    .uri("/api/users/{userId}/goals/active", userId)
                    .retrieve()
                    .body(UserGoalProfile.class);

        } catch (HttpClientErrorException.NotFound e) {
            log.info("用户当前没有进行中的目标，尝试回退到最近目标，userId={}", userId);
            return getLatestGoal(restClient, userId);

        } catch (HttpClientErrorException e) {
            log.error("获取用户目标失败，userId={}, status={}, body={}",
                    userId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new IllegalStateException("获取用户目标失败，HTTP状态异常: " + e.getStatusCode(), e);

        } catch (RestClientException e) {
            log.error("调用 userservice 失败，userId={}, baseUrl={}", userId, userServiceBaseUrl, e);
            throw new IllegalStateException("调用 userservice 失败，请检查配置或服务状态", e);

        } catch (Exception e) {
            log.error("解析用户目标或处理响应失败，userId={}", userId, e);
            throw new IllegalStateException("处理用户目标响应失败", e);
        }
    }

    private UserGoalProfile getLatestGoal(RestClient restClient, String userId) {
        try {
            UserGoalProfile[] goals = restClient.get()
                    .uri("/api/users/{userId}/goals", userId)
                    .retrieve()
                    .body(UserGoalProfile[].class);

            if (goals == null || goals.length == 0) {
                return null;
            }

            List<UserGoalProfile> goalProfiles = Arrays.stream(goals)
                    .filter(goal -> goal.getStatus() == null || !"ABANDONED".equalsIgnoreCase(goal.getStatus()))
                    .sorted(Comparator.comparingInt(this::goalPriority))
                    .toList();

            return goalProfiles.isEmpty() ? null : goalProfiles.get(0);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            log.error("回退获取最近目标失败，userId={}, baseUrl={}", userId, userServiceBaseUrl, e);
            throw new IllegalStateException("获取最近目标失败，请检查 userservice 状态", e);
        }
    }

    private int goalPriority(UserGoalProfile goal) {
        String status = goal.getStatus();
        if (status == null) {
            return 0;
        }
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> 0;
            case "COMPLETED" -> 1;
            case "PAUSED" -> 2;
            default -> 3;
        };
    }
}
