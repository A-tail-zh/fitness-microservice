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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> syncUserIfNecessary(authentication)
                        .then(chain.filter(exchange)))
                .switchIfEmpty(chain.filter(exchange));
    }

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
