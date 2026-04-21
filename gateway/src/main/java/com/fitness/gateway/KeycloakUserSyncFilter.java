package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;


    /**
     * 网关过滤器核心方法，用于在请求处理链中同步 Keycloak 用户信息到本地系统。
     * <p>
     * 该过滤器从 ServerWebExchange 中提取认证主体，验证其已认证状态后，
     * 触发用户同步逻辑（如需要），然后继续执行后续过滤器链。
     * 如果未检测到认证信息或用户未认证，则直接放行请求。
     *
     * @param exchange 当前请求的 ServerWebExchange 对象，提供对请求、响应和上下文的访问
     * @param chain    WebFilterChain 过滤器链，用于将请求传递给下一个过滤器或目标处理器
     * @return Mono<Void> 异步信号，表示过滤器处理完成，无返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> syncUserIfNecessary(authentication)
                        .then(chain.filter(exchange)))
                .switchIfEmpty(chain.filter(exchange));
    }


    /**
     * 根据需要同步 Keycloak 用户到本地系统。
     * <p>
     * 该方法检查认证信息是否为 JWT 令牌，验证必要字段后，判断用户是否已存在。
     * 如果是新用户，则从 JWT 中提取用户信息并注册到本地系统。
     * 同步过程中的错误不会中断请求流程，仅记录日志。
     *
     * @param authentication Spring Security 认证对象，预期为 JwtAuthenticationToken 类型
     * @return Mono<Void> 异步信号，表示用户同步完成；如果跳过同步或发生错误，返回空的 Mono
     */
    private Mono<Void> syncUserIfNecessary(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Mono.empty();
        }

        String userId = jwtAuth.getToken().getSubject();
        String token = jwtAuth.getToken().getTokenValue();

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(token)) {
            log.debug("JWT 中缺少 sub 或 token，跳过用户同步");
            return Mono.empty();
        }

        return userService.validateUser(userId)
                .flatMap(exist -> {
                    if (Boolean.TRUE.equals(exist)) {
                        log.debug("用户已存在，无需同步，keycloakId={}", userId);
                        return Mono.empty();
                    }

                    RegisterRequest registerRequest = getUserDetails(jwtAuth);
                    if (registerRequest == null) {
                        log.warn("无法从 token 中提取完整用户信息，跳过同步，keycloakId={}", userId);
                        return Mono.empty();
                    }

                    return userService.registerUser(registerRequest)
                            .doOnSuccess(user -> log.info(
                                    "首次登录自动同步用户成功，keycloakId={}, email={}",
                                    userId,
                                    registerRequest.getEmail()
                            ))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("用户同步失败，继续放行请求，keycloakId={}, error={}", userId, e.getMessage());
                    return Mono.empty();
                });
    }


    private RegisterRequest getUserDetails(JwtAuthenticationToken jwtAuth) {
        String email = jwtAuth.getToken().getClaimAsString("email");
        String keycloakId = jwtAuth.getToken().getSubject();
        String firstName = jwtAuth.getToken().getClaimAsString("given_name");
        String lastName = jwtAuth.getToken().getClaimAsString("family_name");
        String preferredUsername = jwtAuth.getToken().getClaimAsString("preferred_username");
        String name = jwtAuth.getToken().getClaimAsString("name");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(keycloakId)) {
            return null;
        }

        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setKeycloakId(keycloakId);
        request.setPassword(generatePlaceholderPassword());

        if (StringUtils.hasText(firstName)) {
            request.setFirstName(firstName);
        } else if (StringUtils.hasText(preferredUsername)) {
            request.setFirstName(preferredUsername);
        } else if (StringUtils.hasText(name)) {
            request.setFirstName(name);
        }

        if (StringUtils.hasText(lastName)) {
            request.setLastName(lastName);
        }

        return request;
    }


    private String generatePlaceholderPassword() {
        return "KC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
