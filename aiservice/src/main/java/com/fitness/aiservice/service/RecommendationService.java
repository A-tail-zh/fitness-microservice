package com.fitness.aiservice.service;

import com.fitness.aiservice.exception.RecommendationNotFoundException;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository recommendationRepository;

    public List<Recommendation> getUserCommendation(String userId) {
        return recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Recommendation getActivityRecommendation(String activityId) {
        return recommendationRepository.findLatestStandardByActivityId(activityId)
                .orElseThrow(() -> new RecommendationNotFoundException("未找到活动建议: " + activityId));
    }

    public long deleteByActivityId(String activityId) {
        return recommendationRepository.deleteByActivityId(activityId);
    }
}
