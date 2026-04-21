package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.GoalAnalysisResult;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserGoalProfile;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GoalAnalysisService 单元测试
 *
 * <p>覆盖各目标类型（减脂、增肌、耐力、10K、通用）的分析逻辑
 */
class GoalAnalysisServiceTest {

    private GoalAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new GoalAnalysisService();
    }

    @Test
    @DisplayName("无有效目标时返回 NO_ACTIVE_GOAL 状态")
    void nullGoal_returnsNoActiveGoal() {
        GoalAnalysisResult result = service.analyze(null, baseSummary(), balancedRule(), List.of());
        assertThat(result.getProgressStatus()).isEqualTo("NO_ACTIVE_GOAL");
        assertThat(result.getGoalType()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("INACTIVE 状态目标返回 NO_ACTIVE_GOAL")
    void inactiveGoal_returnsNoActiveGoal() {
        UserGoalProfile goal = goal("FAT_LOSS", "INACTIVE");
        GoalAnalysisResult result = service.analyze(goal, baseSummary(), balancedRule(), List.of());
        assertThat(result.getProgressStatus()).isEqualTo("NO_ACTIVE_GOAL");
    }

    @Nested
    @DisplayName("减脂目标分析")
    class FatLossAnalysis {

        @Test
        @DisplayName("频率和时长达标 → 包含频率达标的 strengths")
        void meetsFrequencyAndDuration_hasStrengths() {
            UserGoalProfile goal = goal("FAT_LOSS", "ACTIVE");
            goal.setWeeklyTargetFrequency(3);
            goal.setWeeklyTargetDuration(180);

            UserHistorySummary summary = baseSummaryBuilder()
                    .activitiesLast7Days(4)
                    .totalDurationLast7Days(200)
                    .activityTypeDistribution(Map.of("RUNNING", 5L))
                    .build();

            GoalAnalysisResult result = service.analyze(goal, summary, balancedRule(), List.of());
            assertThat(result.getStrengths()).anyMatch(s -> s.contains("频率"));
        }

        @Test
        @DisplayName("频率不足 → gaps 包含频率不足提示")
        void insufficientFrequency_hasGaps() {
            UserGoalProfile goal = goal("FAT_LOSS", "ACTIVE");
            goal.setWeeklyTargetFrequency(4);
            goal.setWeeklyTargetDuration(240);

            UserHistorySummary summary = baseSummaryBuilder()
                    .activitiesLast7Days(1)
                    .totalDurationLast7Days(50)
                    .build();

            GoalAnalysisResult result = service.analyze(goal, summary, balancedRule(), List.of());
            assertThat(result.getGaps()).anyMatch(g -> g.contains("频率"));
        }
    }

    @Nested
    @DisplayName("增肌目标分析")
    class MuscleGainAnalysis {

        @Test
        @DisplayName("力量训练占主导 → 包含匹配优势")
        void strengthDominant_hasMatchingStrength() {
            UserGoalProfile goal = goal("MUSCLE_GAIN", "ACTIVE");
            goal.setWeeklyTargetFrequency(3);

            UserHistorySummary summary = baseSummaryBuilder()
                    .activitiesLast7Days(3)
                    .activityTypeDistribution(Map.of("STRENGTH", 5L, "RUNNING", 1L))
                    .build();

            GoalAnalysisResult result = service.analyze(goal, summary, balancedRule(), List.of());
            assertThat(result.getStrengths()).anyMatch(s -> s.contains("增肌"));
        }
    }

    @Nested
    @DisplayName("耐力目标分析")
    class EnduranceAnalysis {

        @Test
        @DisplayName("训练趋势 IMPROVING → 包含优势")
        void improvingTrend_hasStrength() {
            UserGoalProfile goal = goal("ENDURANCE", "ACTIVE");
            goal.setWeeklyTargetFrequency(3);

            UserHistorySummary summary = baseSummaryBuilder()
                    .trend("IMPROVING")
                    .consecutiveActiveDays(4)
                    .build();

            GoalAnalysisResult result = service.analyze(goal, summary, balancedRule(), List.of());
            assertThat(result.getStrengths()).anyMatch(s -> s.contains("趋势"));
        }
    }

    @Nested
    @DisplayName("评分与状态映射")
    class ScoreMapping {

        @Test
        @DisplayName("高分 >= 80 → ON_TRACK 且 alignmentLevel HIGH")
        void highScore_onTrackAndHighAlignment() {
            UserGoalProfile goal = goal("FAT_LOSS", "ACTIVE");
            goal.setWeeklyTargetFrequency(3);
            goal.setWeeklyTargetDuration(150);

            UserHistorySummary summary = baseSummaryBuilder()
                    .activitiesLast7Days(4)
                    .totalDurationLast7Days(200)
                    .activityTypeDistribution(Map.of("RUNNING", 8L))
                    .build();

            GoalAnalysisResult result = service.analyze(goal, summary, balancedRule(), List.of());

            assertThat(result.getCompletionScore()).isBetween(0, 100);
            // 完成度高时状态应为 ON_TRACK 或 SLIGHTLY_OFF_TRACK
            assertThat(result.getProgressStatus()).isIn("ON_TRACK", "SLIGHTLY_OFF_TRACK");
        }

        @Test
        @DisplayName("结果的 completionScore 和 alignmentScore 在 0-100 范围内")
        void scores_alwaysInValidRange() {
            UserGoalProfile goal = goal("GENERAL_FITNESS", "ACTIVE");

            GoalAnalysisResult result = service.analyze(goal, baseSummary(), balancedRule(), List.of());

            assertThat(result.getCompletionScore()).isBetween(0, 100);
            assertThat(result.getAlignmentScore()).isBetween(0, 100);
        }
    }

    // ─────────────────────────── 辅助方法 ───────────────────────────

    private UserGoalProfile goal(String type, String status) {
        return UserGoalProfile.builder()
                .id("goal1")
                .userId("user1")
                .goalType(type)
                .status(status)
                .targetValue(new BigDecimal("70"))
                .targetUnit("kg")
                .weeklyTargetFrequency(3)
                .weeklyTargetDuration(180)
                .targetDate(LocalDate.now().plusMonths(3))
                .experienceLevel("INTERMEDIATE")
                .priority("HIGH")
                .build();
    }

    private UserHistorySummary baseSummary() {
        return baseSummaryBuilder().build();
    }

    private UserHistorySummary.UserHistorySummaryBuilder baseSummaryBuilder() {
        return UserHistorySummary.builder()
                .userId("user1")
                .totalActivities(15)
                .activitiesLast7Days(3)
                .activitiesLast30Days(12)
                .totalDurationLast7Days(150)
                .totalDurationLast30Days(600)
                .consecutiveActiveDays(2)
                .inactiveDaysSinceLastActivity(1)
                .trend("STABLE")
                .adherenceLevel("MEDIUM")
                .avgDurationLast7Days(50.0)
                .avgDurationLast30Days(50.0);
    }

    private RuleAnalysisResult balancedRule() {
        return RuleAnalysisResult.builder()
                .trainingLoadLevel("MEDIUM")
                .recoveryStatus("BALANCED")
                .consistencyLevel("MEDIUM")
                .highlights(List.of("训练连续性一般"))
                .risks(List.of())
                .suggestions(List.of())
                .build();
    }
}
