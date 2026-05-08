package com.fitness.userservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiAssessmentClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.ai-service.base-url:http://localhost:8083}")
    private String aiServiceBaseUrl;

    public FitnessAssessmentAiResponse analyze(FitnessAssessmentAiRequest request) {
        RestClient restClient = restClientBuilder.baseUrl(aiServiceBaseUrl).build();
        return restClient.post()
                .uri("/api/ai-analysis/fitness-assessment")
                .body(request)
                .retrieve()
                .body(FitnessAssessmentAiResponse.class);
    }
}
