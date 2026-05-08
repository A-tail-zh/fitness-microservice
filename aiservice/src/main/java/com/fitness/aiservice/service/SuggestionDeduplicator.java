package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SuggestionDeduplicator {

    public List<EnhancedAnalysisResponse.StructuredSuggestion> deduplicate(
            List<EnhancedAnalysisResponse.StructuredSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        List<EnhancedAnalysisResponse.StructuredSuggestion> sorted = suggestions.stream()
                .sorted(Comparator.comparingInt(this::priorityRank).reversed())
                .toList();

        List<EnhancedAnalysisResponse.StructuredSuggestion> result = new ArrayList<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        for (EnhancedAnalysisResponse.StructuredSuggestion candidate : sorted) {
            String category = normalize(candidate.getCategory());
            if (categoryCount.getOrDefault(category, 0) >= 2) {
                continue;
            }
            if (isDuplicate(result, candidate)) {
                continue;
            }
            result.add(candidate);
            categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
            if (result.size() >= 6) {
                break;
            }
        }
        return result;
    }

    private boolean isDuplicate(List<EnhancedAnalysisResponse.StructuredSuggestion> kept,
                                EnhancedAnalysisResponse.StructuredSuggestion candidate) {
        for (EnhancedAnalysisResponse.StructuredSuggestion item : kept) {
            if (similarity(normalize(item.getTitle()), normalize(candidate.getTitle())) >= 0.7) {
                return true;
            }
            if (isRecoveryLike(item.getAction()) && isRecoveryLike(candidate.getAction())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecoveryLike(String action) {
        String text = normalize(action);
        return text.contains("恢复") || text.contains("休息") || text.contains("降低强度")
                || text.contains("recovery") || text.contains("rest");
    }

    private int priorityRank(EnhancedAnalysisResponse.StructuredSuggestion suggestion) {
        return switch (normalize(suggestion.getPriority())) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private double similarity(String a, String b) {
        if (a.isBlank() || b.isBlank()) {
            return 0;
        }
        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1 : 1.0 - (double) distance / max;
    }

    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[b.length()];
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
