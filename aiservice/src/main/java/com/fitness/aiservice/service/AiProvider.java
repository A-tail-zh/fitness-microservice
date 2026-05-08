package com.fitness.aiservice.service;

import com.fitness.aiservice.model.AiProviderType;

public interface AiProvider {
    AiProviderType type();

    String chat(String systemPrompt, String userPrompt);
}
