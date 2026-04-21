package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 增强分析响应 — 结构化 JSON 输出，包含多维度分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedAnalysisResponse {

    /** 响应元数据 */
    private String requestId;
    private String userId;
    private String activityId;
    private String reportType;        // DAILY / WEEKLY / MONTHLY
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime generatedAt;

    /** 综合评分 (0-100) */
    private Integer overallScore;
    private String overallStatus;     // EXCELLENT / GOOD / FAIR / NEEDS_IMPROVEMENT

    /** 多维度指标分析 */
    private DimensionMetrics dimensions;

    /** AI 生成的自然语言分析（原始文本） */
    private String narrativeAnalysis;

    /** 结构化建议列表 */
    private List<StructuredSuggestion> suggestions;

    /** 风险预警列表 */
    private List<RiskAlert> riskAlerts;

    /** 下周训练计划 */
    private WeeklyPlan nextWeekPlan;

    /** 目标进度预测 */
    private GoalProgressPrediction goalPrediction;

    /** 用户文本备注的 AI 解读（多模态输入融合） */
    private String userNoteInsight;

    // ─────────────────────────── 嵌套 DTO ───────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionMetrics {
        private TrainingVolume volume;
        private TrainingLoad load;
        private RecoveryStatus recovery;
        private ConsistencyMetrics consistency;
        private GoalAlignment goalAlignment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingVolume {
        private int totalDurationLast7Days;     // 分钟
        private int totalDurationLast30Days;
        private int totalCaloriesLast7Days;
        private int totalCaloriesLast30Days;
        private double avgDurationPerSession;
        private String volumeLevel;             // LOW / MEDIUM / HIGH / VERY_HIGH
        private String volumeTrend;             // INCREASING / STABLE / DECREASING
        private Map<String, Long> typeDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingLoad {
        private double weeklyLoadScore;         // 综合负荷分 (计算值)
        private String loadLevel;               // LOW / MEDIUM / HIGH
        private double acuteChronicRatio;       // 急慢性负荷比 (7天/30天均值)
        private String overtrainingRisk;        // LOW / MEDIUM / HIGH
        private int estimatedRecoveryHoursNeeded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecoveryStatus {
        private String status;                  // BALANCED / FATIGUE_RISK / DETRAINING_RISK
        private int recoveryScore;              // 0-100
        private int inactiveDays;
        private boolean readyForHighIntensity;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsistencyMetrics {
        private String level;                   // HIGH / MEDIUM / LOW
        private int consecutiveActiveDays;
        private int activitiesLast7Days;
        private int activitiesLast30Days;
        private String adherenceLevel;
        private String trend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalAlignment {
        private String goalType;
        private String alignmentLevel;          // HIGH / MEDIUM / LOW
        private int completionScore;            // 0-100
        private String progressStatus;
        private List<String> strengths;
        private List<String> gaps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructuredSuggestion {
        private String category;                // TRAINING / RECOVERY / NUTRITION / LIFESTYLE
        private String priority;                // HIGH / MEDIUM / LOW
        private String content;
        private boolean actionable;
        private String timeframe;               // IMMEDIATE / THIS_WEEK / NEXT_MONTH
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAlert {
        private String type;                    // OVERTRAINING / DETRAINING / INJURY / GOAL_DRIFT
        private String severity;                // HIGH / MEDIUM / LOW
        private String description;
        private String mitigation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyPlan {
        private int recommendedFrequency;       // 次/周
        private int recommendedDurationPerSession; // 分钟/次
        private String intensityDirection;      // INCREASE / MAINTAIN / DECREASE
        private List<String> focusAreas;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalProgressPrediction {
        private String goalType;
        private Integer estimatedWeeksToGoal;   // null 表示无法预测
        private String confidence;              // HIGH / MEDIUM / LOW
        private String currentPace;             // AHEAD / ON_TRACK / BEHIND / STALLED
        private String adjustmentNeeded;
    }
}
