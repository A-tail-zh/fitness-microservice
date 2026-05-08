package com.fitness.aiservice.service;

import com.fitness.aiservice.model.AiProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QwenAiProvider implements AiProvider {

    private final QwenService qwenService;

    @Override
    public AiProviderType type() {
        return AiProviderType.QWEN;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return qwenService.extractContent(qwenService.getAnswer(systemPrompt, userPrompt));
    }
}
