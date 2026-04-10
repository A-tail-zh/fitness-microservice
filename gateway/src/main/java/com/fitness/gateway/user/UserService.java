package com.fitness.gateway.user;

import com.fitness.gateway.exception.DownstreamServiceException;
import com.fitness.gateway.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId) {
        log.info("调用用户验证接口，keycloakId={}", userId);
        return userServiceWebClient.get()
                .uri("/api/users/{userId}/validate", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new DownstreamServiceException(buildValidateErrorMessage(userId, e), e)
                );
    }

    public Mono<UserResponse> registerUser(RegisterRequest request) {
        log.info("调用用户注册接口，email={}", request.getEmail());
        return userServiceWebClient.post()
                .uri("/api/users/register")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .onErrorMap(WebClientResponseException.class, e -> mapRegisterException(request, e));
    }

    private RuntimeException mapRegisterException(RegisterRequest request, WebClientResponseException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        if (status == HttpStatus.CONFLICT) {
            return new UserAlreadyExistsException("用户已存在: " + request.getEmail(), e);
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return new DownstreamServiceException("注册参数校验失败: " + request.getEmail(), e);
        }
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return new DownstreamServiceException("用户服务内部错误: " + request.getEmail(), e);
        }
        return new DownstreamServiceException("用户注册失败: " + request.getEmail(), e);
    }

    private String buildValidateErrorMessage(String userId, WebClientResponseException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return "用户未找到: " + userId;
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "用户校验参数错误: " + userId;
        }
        return "用户校验失败: " + userId;
    }
}
