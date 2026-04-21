package com.fitness.aiservice.service;

import com.fitness.aiservice.config.RuleEngineProperties;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ActivityRuleEngineService 单元测试
 *
 * <p>覆盖训练负荷、恢复状态、连续性三个核心判断维度
 */
class ActivityRuleEngineServiceTest {

    private ActivityRuleEngineService service;

    @BeforeEach
    void setUp() {
        RuleEngineProperties props = new RuleEngineProperties();
        // 使用默认配置值
        service = new ActivityRuleEngineService(props);
    }

    // ─────────────────────────── 训练负荷判断 ───────────────────────────

    @Nested
    @DisplayName("训练负荷等级判断")
    class TrainingLoadLevel {

        @Test
        @DisplayName("单次训练 >= 90 分钟 → HIGH")
        void singleSessionHighDuration_returnsHigh() {
            Activity activity = activity(90);
            UserHistorySummary summary = summaryWithWeeklyDuration(0);
            assertThat(service.determineTrainingLoadLevel(activity, summary)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("7天总时长 >= 300 分钟 → HIGH")
        void weeklyHighDuration_returnsHigh() {
            Activity activity = activity(30);
            UserHistorySummary summary = summaryWithWeeklyDuration(300);
            assertThat(service.determineTrainingLoadLevel(activity, summary)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("单次训练 45 分钟、7天100分钟 → MEDIUM")
        void mediumSession_returnsMedium() {
            Activity activity = activity(45);
            UserHistorySummary summary = summaryWithWeeklyDuration(100);
            assertThat(service.determineTrainingLoadLevel(activity, summary)).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("单次训练 30 分钟、7天80分钟 → LOW")
        void lowLoad_returnsLow() {
            Activity activity = activity(30);
            UserHistorySummary summary = summaryWithWeeklyDuration(80);
            assertThat(service.determineTrainingLoadLevel(activity, summary)).isEqualTo("LOW");
        }

        @Test
        @DisplayName("duration 为 null 时安全处理 → LOW")
        void nullDuration_treatedAsZero() {
            Activity activity = activity(null);
            UserHistorySummary summary = summaryWithWeeklyDuration(0);
            assertThat(service.determineTrainingLoadLevel(activity, summary)).isEqualTo("LOW");
        }
    }

    // ─────────────────────────── 恢复状态判断 ───────────────────────────

    @Nested
    @DisplayName("恢复状态判断")
    class RecoveryStatus {

        @Test
        @DisplayName("不活跃 >= 5 天且7天训练 <= 1次 → DETRAINING_RISK")
        void longInactivity_returnsDetrainingRisk() {
            UserHistorySummary summary = summaryBuilder()
                    .inactiveDaysSinceLastActivity(6)
                    .activitiesLast7Days(1)
                    .totalDurationLast7Days(30)
                    .build();
            assertThat(service.determineRecoveryStatus(summary, "LOW")).isEqualTo("DETRAINING_RISK");
        }

        @Test
        @DisplayName("负荷 HIGH → FATIGUE_RISK")
        void highLoad_returnsFatigueRisk() {
            UserHistorySummary summary = summaryBuilder()
                    .inactiveDaysSinceLastActivity(1)
                    .activitiesLast7Days(5)
                    .totalDurationLast7Days(200)
                    .build();
            assertThat(service.determineRecoveryStatus(summary, "HIGH")).isEqualTo("FATIGUE_RISK");
        }

        @Test
        @DisplayName("7天总时长 >= 300 分钟 → FATIGUE_RISK")
        void weeklyDurationOver300_returnsFatigueRisk() {
            UserHistorySummary summary = summaryBuilder()
                    .inactiveDaysSinceLastActivity(1)
                    .activitiesLast7Days(5)
                    .totalDurationLast7Days(300)
                    .build();
            assertThat(service.determineRecoveryStatus(summary, "MEDIUM")).isEqualTo("FATIGUE_RISK");
        }

        @Test
        @DisplayName("正常训练节奏 → BALANCED")
        void normalTraining_returnsBalanced() {
            UserHistorySummary summary = summaryBuilder()
                    .inactiveDaysSinceLastActivity(1)
                    .activitiesLast7Days(3)
                    .totalDurationLast7Days(150)
                    .build();
            assertThat(service.determineRecoveryStatus(summary, "MEDIUM")).isEqualTo("BALANCED");
        }
    }

    // ─────────────────────────── 连续性判断 ───────────────────────────

    @Nested
    @DisplayName("训练连续性判断")
    class ConsistencyLevel {

        @Test
        @DisplayName("7天训练 >= 4次 → HIGH")
        void fourSessionsPerWeek_returnsHigh() {
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(4)
                    .consecutiveActiveDays(1)
                    .build();
            assertThat(service.determineConsistencyLevel(summary)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("连续活跃天数 >= 3 → HIGH")
        void threeConsecutiveDays_returnsHigh() {
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(2)
                    .consecutiveActiveDays(3)
                    .build();
            assertThat(service.determineConsistencyLevel(summary)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("7天训练 2 次 → MEDIUM")
        void twoSessionsPerWeek_returnsMedium() {
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(2)
                    .consecutiveActiveDays(1)
                    .build();
            assertThat(service.determineConsistencyLevel(summary)).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("7天训练 1 次 → LOW")
        void oneSessionPerWeek_returnsLow() {
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(1)
                    .consecutiveActiveDays(1)
                    .build();
            assertThat(service.determineConsistencyLevel(summary)).isEqualTo("LOW");
        }
    }

    // ─────────────────────────── 完整分析结果验证 ───────────────────────────

    @Nested
    @DisplayName("完整 analyze() 输出验证")
    class FullAnalysis {

        @Test
        @DisplayName("疲劳场景：高负荷时，risks 和 suggestions 非空")
        void highLoadAnalysis_containsRisksAndSuggestions() {
            Activity act = activity(100);
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(5)
                    .consecutiveActiveDays(4)
                    .totalDurationLast7Days(320)
                    .inactiveDaysSinceLastActivity(0)
                    .build();

            RuleAnalysisResult result = service.analyze(act, summary);

            assertThat(result.getTrainingLoadLevel()).isEqualTo("HIGH");
            assertThat(result.getRecoveryStatus()).isEqualTo("FATIGUE_RISK");
            assertThat(result.getRisks()).isNotEmpty();
            assertThat(result.getSuggestions()).isNotEmpty();
        }

        @Test
        @DisplayName("去训练化场景：长期不活跃，highlights 包含平衡相关说明")
        void detrainingScenario_containsDetrainingRisk() {
            Activity act = activity(20);
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(1)
                    .consecutiveActiveDays(0)
                    .totalDurationLast7Days(20)
                    .inactiveDaysSinceLastActivity(7)
                    .build();

            RuleAnalysisResult result = service.analyze(act, summary);

            assertThat(result.getRecoveryStatus()).isEqualTo("DETRAINING_RISK");
            assertThat(result.getRisks()).anyMatch(r -> r.contains("训练间隔"));
        }

        @Test
        @DisplayName("平衡场景：中等负荷、正常恢复，highlights 包含平衡说明")
        void balancedScenario_containsPositiveHighlights() {
            Activity act = activity(50);
            UserHistorySummary summary = summaryBuilder()
                    .activitiesLast7Days(3)
                    .consecutiveActiveDays(3)
                    .totalDurationLast7Days(180)
                    .inactiveDaysSinceLastActivity(1)
                    .build();

            RuleAnalysisResult result = service.analyze(act, summary);

            assertThat(result.getRecoveryStatus()).isEqualTo("BALANCED");
            assertThat(result.getHighlights()).isNotEmpty();
        }
    }

    // ─────────────────────────── 测试辅助方法 ───────────────────────────

    private Activity activity(Integer duration) {
        Activity a = new Activity();
        a.setId("test-activity");
        a.setUserId("test-user");
        a.setDuration(duration);
        return a;
    }

    private UserHistorySummary summaryWithWeeklyDuration(int weeklyDuration) {
        return summaryBuilder().totalDurationLast7Days(weeklyDuration).build();
    }

    private UserHistorySummary.UserHistorySummaryBuilder summaryBuilder() {
        return UserHistorySummary.builder()
                .userId("test-user")
                .totalActivities(10)
                .activitiesLast7Days(3)
                .activitiesLast30Days(12)
                .totalDurationLast7Days(150)
                .totalDurationLast30Days(600)
                .consecutiveActiveDays(2)
                .inactiveDaysSinceLastActivity(1)
                .avgDurationLast7Days(50.0)
                .avgDurationLast30Days(50.0);
    }
}
