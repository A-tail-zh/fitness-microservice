package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class QwenService {
    @Value("${dashscope.model}")
    private String model;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.url}")
    private String url;

    private final WebClient  webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QwenService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * 发送问题并获取回答（无系统提示词）
     */
    public String getAnswer(String question) {
        return getAnswer(null, question);
    }

    /**
     * 发送问题并获取回答（支持系统提示词）
     */
    public String getAnswer(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages
        );

        log.debug("调用千问 API，模型: {}", model);

        try {
            String response = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "千问 API 客户端错误: " + clientResponse.statusCode() + " - " + body)))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "千问 API 服务器错误: " + clientResponse.statusCode() + " - " + body)))
                    )
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                            .filter(this::isRetriable)
                            .doBeforeRetry(signal -> log.warn(
                                    "千问 API 调用失败，准备重试，第 {} 次，原因: {}",
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage())))
                    .block();

            log.debug("千问 API 调用成功");
            return response;

        } catch (WebClientResponseException e) {
            log.error("千问 API 响应异常，状态码: {}, 响应体: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("千问 AI 服务调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("千问 API 调用失败", e);
            throw new RuntimeException("千问 AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    private boolean isRetriable(Throwable throwable) {
        if (throwable instanceof WebClientRequestException || throwable instanceof TimeoutException) {
            return true;
        }

        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof java.net.SocketException || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * 从 API 响应 JSON 中提取 content 文本
     */
    public String extractContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();
            log.debug("提取到 AI 内容，长度: {}", content.length());
            return content;
        } catch (Exception e) {
            log.error("解析千问 API 响应失败，原始响应: {}", rawResponse, e);
            throw new RuntimeException("解析 AI 响应失败", e);
        }
    }
}
