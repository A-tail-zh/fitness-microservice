package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleAnalysisResult {
    @Builder.Default
    private List<String> highlights = new ArrayList<>();
    @Builder.Default
    private List<String> risks = new ArrayList<>();
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();
    private String trainingLoadLevel;
    private String recoveryStatus;
    private String consistencyLevel;
}
