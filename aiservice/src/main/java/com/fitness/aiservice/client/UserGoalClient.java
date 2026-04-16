package com.fitness.aiservice.client;

import com.fitness.aiservice.dto.UserGoalProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserGoalClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.user-service.base-url:http://localhost:8081}")
    private String userServiceBaseUrl;

    public UserGoalProfile getActiveGoal(String userId) {
        RestClient restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();

        try {
            return restClient.get()
                    .uri("/api/users/{userId}/goals/active", userId)
                    .retrieve()
                    .body(UserGoalProfile.class);

        } catch (HttpClientErrorException.NotFound e) {
            log.info("用户当前没有有效目标，userId={}", userId);
            return null;

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
}