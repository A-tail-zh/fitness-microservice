package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ActivityHistorySummaryService 单元测试
 *
 * <p>验证历史聚合摘要计算的正确性
 */
class ActivityHistorySummaryServiceTest {

    private ActivityHistorySummaryService service;
    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        service = new ActivityHistorySummaryService();
    }

    @Test
    @DisplayName("空活动列表 → 返回零值摘要")
    void emptyActivities_returnsZeroSummary() {
        UserHistorySummary summary = service.buildSummary("user1", List.of());

        assertThat(summary.getTotalActivities()).isZero();
        assertThat(summary.getActivitiesLast7Days()).isZero();
        assertThat(summary.getTotalDurationLast7Days()).isZero();
        assertThat(summary.getConsecutiveActiveDays()).isZero();
        assertThat(summary.getInactiveDaysSinceLastActivity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("最近7天内的活动被正确统计")
    void recentActivities_countedInLast7Days() {
        List<Activity> activities = List.of(
                activity("act1", NOW.minusDays(1), 30, 200),
                activity("act2", NOW.minusDays(3), 45, 300),
                activity("act3", NOW.minusDays(10), 60, 400)  // 超出7天范围
        );

        UserHistorySummary summary = service.buildSummary("user1", activities);

        assertThat(summary.getTotalActivities()).isEqualTo(3);
        assertThat(summary.getActivitiesLast7Days()).isEqualTo(2);
        assertThat(summary.getTotalDurationLast7Days()).isEqualTo(75);  // 30+45
        assertThat(summary.getTotalCaloriesLast7Days()).isEqualTo(500); // 200+300
    }

    @Test
    @DisplayName("连续活跃天数计算正确")
    void consecutiveActiveDays_calculatedCorrectly() {
        List<Activity> activities = List.of(
                activity("act1", NOW.minusDays(0), 30, 200),
                activity("act2", NOW.minusDays(1), 45, 300),
                activity("act3", NOW.minusDays(2), 60, 400),
                activity("act4", NOW.minusDays(5), 60, 400)  // 中断了
        );

        UserHistorySummary summary = service.buildSummary("user1", activities);
        // 连续 today, yesterday, day-before = 3 天
        assertThat(summary.getConsecutiveActiveDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("训练趋势：7天明显多于月均 → IMPROVING")
    void improvingTrend_detectedCorrectly() {
        List<Activity> activities = List.of(
                activity("a1", NOW.minusDays(1), 60, 400),
                activity("a2", NOW.minusDays(2), 60, 400),
                activity("a3", NOW.minusDays(3), 60, 400),
                activity("a4", NOW.minusDays(4), 60, 400),
                activity("a5", NOW.minusDays(5), 60, 400),
                // 月内其他（低频）
                activity("a6", NOW.minusDays(15), 60, 400),
                activity("a7", NOW.minusDays(20), 60, 400)
        );

        UserHistorySummary summary = service.buildSummary("user1", activities);
        assertThat(summary.getTrend()).isEqualTo("IMPROVING");
    }

    @Test
    @DisplayName("最常见训练类型识别正确")
    void mostFrequentActivityType_identifiedCorrectly() {
        List<Activity> activities = List.of(
                activityWithType("a1", "RUNNING", NOW.minusDays(1)),
                activityWithType("a2", "RUNNING", NOW.minusDays(2)),
                activityWithType("a3", "CYCLING", NOW.minusDays(3))
        );

        UserHistorySummary summary = service.buildSummary("user1", activities);
        assertThat(summary.getMostFrequentActivityType()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("无7天活动时执行度为 LOW")
    void noRecentActivities_adherenceLevelLow() {
        List<Activity> activities = List.of(
                activity("a1", NOW.minusDays(15), 60, 400)
        );

        UserHistorySummary summary = service.buildSummary("user1", activities);
        assertThat(summary.getAdherenceLevel()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("平均时长和卡路里计算正确")
    void averages_calculatedCorrectly() {
        List<Activity> activities = List.of(
                activity("a1", NOW.minusDays(1), 30, 200),
                activity("a2", NOW.minusDays(2), 60, 400)
        );

        UserHistorySummary summary = service.buildSummary("user1", activities);
        assertThat(summary.getAvgDurationLast7Days()).isEqualTo(45.0);
        assertThat(summary.getAvgCaloriesLast7Days()).isEqualTo(300.0);
    }

    // ─────────────────────────── 辅助方法 ───────────────────────────

    private Activity activity(String id, LocalDateTime startTime, int duration, int calories) {
        Activity a = new Activity();
        a.setId(id);
        a.setUserId("user1");
        a.setStartTime(startTime);
        a.setDuration(duration);
        a.setCalorieBurned(calories);
        a.setType("RUNNING");
        return a;
    }

    private Activity activityWithType(String id, String type, LocalDateTime startTime) {
        Activity a = new Activity();
        a.setId(id);
        a.setUserId("user1");
        a.setStartTime(startTime);
        a.setDuration(30);
        a.setCalorieBurned(200);
        a.setType(type);
        return a;
    }
}
