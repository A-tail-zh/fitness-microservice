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


    /**
     * 验证用户是否存在
     * <p>
     * 通过调用用户服务的验证接口，检查指定用户ID的用户是否在系统中存在。该方法会：
     * - 记录调用日志，包含待验证的userId；
     * - 向用户服务发起GET请求：/api/users/{userId}/validate；
     * - 将响应转换为Boolean类型的Mono对象；
     * - 如果发生WebClientResponseException异常，则转换为DownstreamServiceException并返回友好的错误信息。
     *
     * @param userId 用户唯一标识符（keycloakId），用于验证用户是否存在
     * @return Mono<Boolean>对象，包含用户存在性验证结果（true表示存在，false表示不存在）
     * @throws DownstreamServiceException 当调用用户服务失败时抛出（如网络错误、服务不可用等）
     */
    public Mono<Boolean> validateUser(String userId) {
        log.info("调用用户验证接口，keycloakId={}", userId);
        return userServiceWebClient.get()
                .uri("/api/users/{userId}/validate", userId)
                .retrieve()//直接获取响应体
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


    /**
     * 映射用户注册异常
     * <p>
     * 根据用户服务返回的HTTP状态码，将WebClientResponseException转换为更具体的业务异常。该方法会：
     * - 409 CONFLICT：转换为用户已存在异常（UserAlreadyExistsException）；
     * - 400 BAD_REQUEST：转换为参数校验失败的下游服务异常；
     * - 500 INTERNAL_SERVER_ERROR：转换为服务器内部错误的下游服务异常；
     * - 其他状态码：转换为通用的下游服务异常。
     *
     * @param request 用户注册请求对象，包含用户的邮箱等信息，用于构建错误消息
     * @param e       WebClientResponseException异常对象，包含下游服务返回的错误响应信息
     * @return RuntimeException对象，根据HTTP状态码返回对应的业务异常
     */
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
