package com.fitness.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakUserSyncFilter keycloakUserSyncFilter;


    /**
     * 配置 Spring Security Web 过滤器链，定义 API 的安全策略。
     * <p>
     * 该配置禁用 CSRF 保护，设置路径访问规则（监控端点和注册接口允许匿名访问，
     * 其他所有接口需要认证），启用 OAuth2 JWT 资源服务器支持，
     * 并在认证过滤器之后添加 Keycloak 用户同步过滤器。
     *
     * @param http ServerHttpSecurity 对象，用于配置 WebFlux 安全策略
     * @return SecurityWebFilterChain 配置完成的 Security 过滤器链
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/*").permitAll()
                        .pathMatchers("/api/users/register").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterAfter(keycloakUserSyncFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}