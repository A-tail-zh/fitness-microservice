package com.fitness.activityservice.service;

import com.fitness.activityservice.exception.InvalidUserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {

    private final WebClient userServiceWebClient;

    public boolean validateUser(String userId) {
        log.info("调用用户校验接口，keycloakId={}", userId);
        try {
            return Boolean.TRUE.equals(userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block());

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new InvalidUserException("用户未找到: " + userId);
            }
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new InvalidUserException("用户校验参数错误: " + userId);
            }
            throw new InvalidUserException("用户校验失败: " + userId);
        }
    }
}
