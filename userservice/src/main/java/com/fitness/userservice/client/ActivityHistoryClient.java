package com.fitness.userservice.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityHistoryClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.activity-service.base-url:http://localhost:8082}")
    private String activityServiceBaseUrl;

    /**
     * 获取指定用户的活动历史记录列表
     * <p>
     * 通过REST API调用活动服务，检索指定用户的所有活动快照信息。
     *
     * @param userId 用户唯一标识符，用于查询该用户的活动记录
     * @return 用户活动快照列表，每个快照包含活动ID、持续时间、开始时间和创建时间等信息；
     * 如果用户没有任何活动记录，则返回空列表
     */
    public List<ActivitySnapshot> getUserActivities(String userId) {
        RestClient restClient = restClientBuilder.baseUrl(activityServiceBaseUrl).build();

        return restClient.get()
                .uri("/api/activities")
                .header("X-User-ID", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ActivitySnapshot>>() {
                });
    }

    /**
     * 活动快照数据结构
     */
    @Data
    public static class ActivitySnapshot {
        private String id;
        private Integer duration;
        private LocalDateTime startTime;
        private LocalDateTime createdAt;
    }
}
