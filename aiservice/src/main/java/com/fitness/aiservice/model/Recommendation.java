package com.fitness.aiservice.model;

import com.fitness.aiservice.dto.GoalAnalysisResult;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserGoalProfile;
import com.fitness.aiservice.dto.UserHistorySummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "recommendations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {
    @Id
    private String id;
    private String activityId;
    private String userId;
    private String activityType;
    private String recommendation;
    private List<String> improvements;
    private List<String> suggestions;
    private UserHistorySummary historySummary;
    private RuleAnalysisResult ruleAnalysisResult;
    private UserGoalProfile userGoalProfile;
    private GoalAnalysisResult goalAnalysisResult;

    @CreatedDate
    private LocalDateTime createdAt;
}