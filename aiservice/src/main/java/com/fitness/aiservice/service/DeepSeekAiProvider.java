package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.AiProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DeepSeekAiProvider implements AiProvider {

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    @Value("${deepseek.url:https://api.deepseek.com/chat/completions}")
    private String url;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeepSeekAiProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.DEEPSEEK;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is empty");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );

        log.debug("Calling DeepSeek API, model={}", model);
        String rawResponse = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(90))
                .block();

        return extractContent(rawResponse);
    }

    private String extractContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception ex) {
            log.error("Failed to parse DeepSeek response: {}", rawResponse, ex);
            throw new RuntimeException("Failed to parse DeepSeek response", ex);
        }
    }
}
