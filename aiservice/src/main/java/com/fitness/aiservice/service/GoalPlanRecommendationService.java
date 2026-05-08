package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.dto.GoalPlanRecommendationRequest;
import com.fitness.aiservice.dto.GoalPlanRecommendationResponse;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalPlanRecommendationService {

    private final ActivityHistorySummaryService activityHistorySummaryService;
    private final QwenService qwenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoalPlanRecommendationResponse recommend(GoalPlanRecommendationRequest request, List<Activity> activities) {
        if (!StringUtils.hasText(request.getUserId())) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        List<Activity> safeActivities = activities == null ? List.of() : activities;
        UserHistorySummary summary = activityHistorySummaryService.buildSummary(request.getUserId(), safeActivities);
        GoalPlanRecommendationResponse rulePlan = buildRulePlan(request, summary, safeActivities);

        try {
            String rawResponse = qwenService.getAnswer(buildSystemPrompt(), buildUserPrompt(request, summary, safeActivities, rulePlan));
            String content = qwenService.extractContent(rawResponse);
            GoalPlanRecommendationResponse aiPlan = parseAiPlan(content, rulePlan);
            aiPlan.setRawAiReport(content);
            return aiPlan;
        } catch (Exception ex) {
            log.warn("AI 目标制定失败，已返回规则推荐目标，userId={}", request.getUserId(), ex);
            return rulePlan;
        }
    }

    private GoalPlanRecommendationResponse buildRulePlan(
            GoalPlanRecommendationRequest request,
            UserHistorySummary summary,
            List<Activity> activities) {
        String goalType = normalizeGoalType(firstNonBlank(request.getPreferredGoalType(), inferGoalType(summary)));
        int weeklyFrequency = request.getWeeklyTargetFrequency() != null && request.getWeeklyTargetFrequency() > 0
                ? request.getWeeklyTargetFrequency()
                : recommendFrequency(goalType, summary);
        int weeklyDuration = request.getWeeklyTargetDuration() != null && request.getWeeklyTargetDuration() > 0
                ? request.getWeeklyTargetDuration()
                : recommendWeeklyDuration(goalType, summary, weeklyFrequency);
        LocalDate targetDate = request.getTargetDate() != null
                ? request.getTargetDate()
                : LocalDate.now().plusWeeks(recommendWeeks(goalType, summary));

        int feasibility = calculateFeasibility(summary, weeklyFrequency, weeklyDuration, targetDate);
        GoalTarget target = recommendTarget(goalType, request, summary, activities);

        return GoalPlanRecommendationResponse.builder()
                .goalType(goalType)
                .targetValue(target.value())
                .targetUnit(target.unit())
                .weeklyTargetFrequency(weeklyFrequency)
                .weeklyTargetDuration(weeklyDuration)
                .targetDate(targetDate)
                .experienceLevel(firstNonBlank(request.getExperienceLevel(), inferExperience(summary)))
                .priority(firstNonBlank(request.getPriority(), feasibility >= 70 ? "HIGH" : "MEDIUM"))
                .note(buildGoalNote(request, summary, goalType, feasibility))
                .feasibilityScore(feasibility)
                .reason(buildReason(summary, goalType, weeklyFrequency, weeklyDuration))
                .riskWarning(buildRiskWarning(summary, feasibility))
                .weeklyPlan(buildWeeklyPlan(goalType, weeklyFrequency, weeklyDuration))
                .milestones(buildMilestones(goalType, targetDate))
                .rawAiReport("规则推荐目标，AI 服务不可用时使用。")
                .build();
    }

    private String buildSystemPrompt() {
        return """
                你是一名专业运动教练、训练计划师和目标管理顾问。
                你要基于用户最近 7 天和 30 天真实运动数据，制定一个安全、具体、可执行的阶段训练目标。
                不要制定明显超过当前能力太多的目标；如果用户输入的目标过激，要自动下调为更可执行的版本。
                只返回 JSON，不要 Markdown，不要额外解释。
                goalType 只能是 FAT_LOSS、WEIGHT_LOSS、MUSCLE_GAIN、ENDURANCE、TEN_K_IMPROVEMENT、GENERAL_FITNESS、RECOVERY 之一。
                """;
    }

    private String buildUserPrompt(
            GoalPlanRecommendationRequest request,
            UserHistorySummary summary,
            List<Activity> activities,
            GoalPlanRecommendationResponse rulePlan) {
        return """
                请根据用户真实训练数据制定目标。可以参考规则推荐，但如果规则目标不合理，你可以调整。

                用户偏好：
                目标类型：%s
                目标数值：%s %s
                每周目标次数：%s
                每周目标时长：%s
                目标日期：%s
                经验等级：%s
                优先级：%s
                备注：%s

                真实训练摘要：
                最近7天：%d 次，%d 分钟，%d kcal
                最近30天：%d 次，%d 分钟，%d kcal
                平均时长：7天 %.1f 分钟，30天 %.1f 分钟
                趋势：%s
                执行度：%s
                连续训练天数：%d
                距离上次训练：%d 天
                最常见训练类型：%s
                类型分布：%s

                最近训练明细：
                %s

                规则推荐：
                目标类型：%s
                目标数值：%s %s
                每周次数：%d
                每周时长：%d
                截止日期：%s
                可行性评分：%d

                请输出 JSON：
                {
                  "goalType": "目标类型",
                  "targetValue": 目标数值或 null,
                  "targetUnit": "目标单位",
                  "weeklyTargetFrequency": 每周训练次数,
                  "weeklyTargetDuration": 每周训练总分钟数,
                  "targetDate": "yyyy-MM-dd",
                  "experienceLevel": "BEGINNER/NOVICE/INTERMEDIATE/ADVANCED",
                  "priority": "LOW/MEDIUM/HIGH",
                  "note": "写入目标备注的简短说明",
                  "feasibilityScore": 0-100,
                  "reason": "为什么这样制定目标",
                  "riskWarning": "风险提醒",
                  "weeklyPlan": ["第1条", "第2条", "第3条"],
                  "milestones": ["第1阶段", "第2阶段", "第3阶段"]
                }
                """.formatted(
                defaultText(request.getPreferredGoalType()),
                request.getTargetValue() == null ? "未设置" : request.getTargetValue().toPlainString(),
                defaultText(request.getTargetUnit()),
                request.getWeeklyTargetFrequency() == null ? "未设置" : request.getWeeklyTargetFrequency(),
                request.getWeeklyTargetDuration() == null ? "未设置" : request.getWeeklyTargetDuration(),
                request.getTargetDate() == null ? "未设置" : request.getTargetDate(),
                defaultText(request.getExperienceLevel()),
                defaultText(request.getPriority()),
                defaultText(request.getNote()),
                summary.getActivitiesLast7Days(),
                summary.getTotalDurationLast7Days(),
                summary.getTotalCaloriesLast7Days(),
                summary.getActivitiesLast30Days(),
                summary.getTotalDurationLast30Days(),
                summary.getTotalCaloriesLast30Days(),
                summary.getAvgDurationLast7Days(),
                summary.getAvgDurationLast30Days(),
                defaultText(summary.getTrend()),
                defaultText(summary.getAdherenceLevel()),
                summary.getConsecutiveActiveDays(),
                summary.getInactiveDaysSinceLastActivity() == Integer.MAX_VALUE ? 999 : summary.getInactiveDaysSinceLastActivity(),
                defaultText(summary.getMostFrequentActivityType()),
                formatDistribution(summary.getActivityTypeDistribution()),
                formatActivities(activities),
                rulePlan.getGoalType(),
                rulePlan.getTargetValue() == null ? "未设置" : rulePlan.getTargetValue().toPlainString(),
                defaultText(rulePlan.getTargetUnit()),
                rulePlan.getWeeklyTargetFrequency(),
                rulePlan.getWeeklyTargetDuration(),
                rulePlan.getTargetDate(),
                rulePlan.getFeasibilityScore()
        );
    }

    private GoalPlanRecommendationResponse parseAiPlan(String content, GoalPlanRecommendationResponse fallback) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(content));
        return GoalPlanRecommendationResponse.builder()
                .goalType(normalizeGoalType(firstNonBlank(root.path("goalType").asText(null), fallback.getGoalType())))
                .targetValue(root.path("targetValue").isNumber()
                        ? root.path("targetValue").decimalValue()
                        : fallback.getTargetValue())
                .targetUnit(firstNonBlank(root.path("targetUnit").asText(null), fallback.getTargetUnit()))
                .weeklyTargetFrequency(validInt(root.path("weeklyTargetFrequency").asInt(0), fallback.getWeeklyTargetFrequency()))
                .weeklyTargetDuration(validInt(root.path("weeklyTargetDuration").asInt(0), fallback.getWeeklyTargetDuration()))
                .targetDate(parseDate(root.path("targetDate").asText(null), fallback.getTargetDate()))
                .experienceLevel(firstNonBlank(root.path("experienceLevel").asText(null), fallback.getExperienceLevel()))
                .priority(firstNonBlank(root.path("priority").asText(null), fallback.getPriority()))
                .note(firstNonBlank(root.path("note").asText(null), fallback.getNote()))
                .feasibilityScore(Math.max(0, Math.min(100, root.path("feasibilityScore").asInt(fallback.getFeasibilityScore()))))
                .reason(firstNonBlank(root.path("reason").asText(null), fallback.getReason()))
                .riskWarning(firstNonBlank(root.path("riskWarning").asText(null), fallback.getRiskWarning()))
                .weeklyPlan(readStringList(root.path("weeklyPlan"), fallback.getWeeklyPlan()))
                .milestones(readStringList(root.path("milestones"), fallback.getMilestones()))
                .build();
    }

    private String inferGoalType(UserHistorySummary summary) {
        String topType = summary.getMostFrequentActivityType() == null
                ? ""
                : summary.getMostFrequentActivityType().toUpperCase(Locale.ROOT);
        if (summary.getInactiveDaysSinceLastActivity() > 10 || summary.getActivitiesLast30Days() < 4) {
            return "GENERAL_FITNESS";
        }
        if (topType.contains("RUN")) {
            return summary.getActivitiesLast30Days() >= 10 ? "TEN_K_IMPROVEMENT" : "ENDURANCE";
        }
        if (topType.contains("STRENGTH") || topType.contains("WEIGHT")) {
            return "MUSCLE_GAIN";
        }
        return "GENERAL_FITNESS";
    }

    private int recommendFrequency(String goalType, UserHistorySummary summary) {
        int baseline = Math.max(2, (int) Math.round(summary.getActivitiesLast30Days() / 4.0));
        int recommended = switch (goalType) {
            case "TEN_K_IMPROVEMENT" -> Math.max(3, baseline + 1);
            case "MUSCLE_GAIN" -> Math.max(3, baseline);
            case "FAT_LOSS", "WEIGHT_LOSS", "ENDURANCE" -> Math.max(3, baseline);
            case "RECOVERY" -> Math.min(3, Math.max(2, baseline));
            default -> Math.max(2, baseline);
        };
        return Math.min(5, recommended);
    }

    private int recommendWeeklyDuration(String goalType, UserHistorySummary summary, int frequency) {
        int baseline = summary.getTotalDurationLast30Days() <= 0
                ? frequency * 35
                : (int) Math.round(summary.getTotalDurationLast30Days() / 4.0);
        double multiplier = switch (goalType) {
            case "TEN_K_IMPROVEMENT", "ENDURANCE" -> 1.15;
            case "FAT_LOSS", "WEIGHT_LOSS" -> 1.10;
            case "MUSCLE_GAIN" -> 1.05;
            case "RECOVERY" -> 0.75;
            default -> 1.0;
        };
        int target = (int) Math.round(baseline * multiplier);
        return Math.max(frequency * 30, Math.min(frequency * 75, target));
    }

    private int recommendWeeks(String goalType, UserHistorySummary summary) {
        if ("RECOVERY".equals(goalType)) {
            return 4;
        }
        if (summary.getActivitiesLast30Days() < 4) {
            return 10;
        }
        return "TEN_K_IMPROVEMENT".equals(goalType) ? 12 : 8;
    }

    private int calculateFeasibility(UserHistorySummary summary, int weeklyFrequency, int weeklyDuration, LocalDate targetDate) {
        int score = 70;
        int baselineFrequency = Math.max(1, (int) Math.round(summary.getActivitiesLast30Days() / 4.0));
        int baselineDuration = Math.max(30, (int) Math.round(summary.getTotalDurationLast30Days() / 4.0));
        if (weeklyFrequency > baselineFrequency + 2) score -= 18;
        if (weeklyDuration > baselineDuration * 1.35) score -= 18;
        if (summary.getInactiveDaysSinceLastActivity() > 7) score -= 12;
        if ("HIGH".equalsIgnoreCase(summary.getAdherenceLevel())) score += 12;
        if (targetDate != null && targetDate.isBefore(LocalDate.now().plusWeeks(4))) score -= 12;
        return Math.max(20, Math.min(95, score));
    }

    private GoalTarget recommendTarget(
            String goalType,
            GoalPlanRecommendationRequest request,
            UserHistorySummary summary,
            List<Activity> activities) {
        if (request.getTargetValue() != null) {
            return new GoalTarget(request.getTargetValue(), firstNonBlank(request.getTargetUnit(), defaultUnit(goalType)));
        }
        return switch (goalType) {
            case "TEN_K_IMPROVEMENT" -> new GoalTarget(BigDecimal.valueOf(10), "公里");
            case "ENDURANCE" -> new GoalTarget(BigDecimal.valueOf(Math.max(45, Math.round(summary.getAvgDurationLast30Days() + 10))), "分钟");
            case "MUSCLE_GAIN" -> new GoalTarget(BigDecimal.valueOf(8), "周力量训练周期");
            case "FAT_LOSS" -> new GoalTarget(BigDecimal.valueOf(3), "公斤");
            case "WEIGHT_LOSS" -> new GoalTarget(BigDecimal.valueOf(2), "公斤");
            case "RECOVERY" -> new GoalTarget(BigDecimal.valueOf(4), "周恢复周期");
            default -> new GoalTarget(BigDecimal.valueOf(Math.max(8, summary.getActivitiesLast30Days() + 4)), "次训练");
        };
    }

    private String buildGoalNote(GoalPlanRecommendationRequest request, UserHistorySummary summary, String goalType, int feasibility) {
        String base = "AI 根据最近 30 天真实训练数据制定：目标类型 %s，可行性评分 %d/100。".formatted(goalType, feasibility);
        if (StringUtils.hasText(request.getNote())) {
            return base + " 用户备注：" + request.getNote();
        }
        if (summary.getActivitiesLast30Days() == 0) {
            return base + " 当前缺少历史训练记录，目标以建立训练习惯为主。";
        }
        return base;
    }

    private String buildReason(UserHistorySummary summary, String goalType, int weeklyFrequency, int weeklyDuration) {
        return "根据最近 30 天 %d 次训练、累计 %d 分钟、执行度 %s，推荐 %s 目标；每周 %d 次、累计 %d 分钟属于相对安全的递进。"
                .formatted(
                        summary.getActivitiesLast30Days(),
                        summary.getTotalDurationLast30Days(),
                        defaultText(summary.getAdherenceLevel()),
                        goalType,
                        weeklyFrequency,
                        weeklyDuration);
    }

    private String buildRiskWarning(UserHistorySummary summary, int feasibility) {
        if (summary.getInactiveDaysSinceLastActivity() > 7) {
            return "最近训练间隔偏长，前 1-2 周不要直接上高强度，先恢复规律性。";
        }
        if (feasibility < 60) {
            return "目标有一定挑战性，需要控制每周训练量增长，避免疲劳和受伤。";
        }
        return "当前目标整体可行，但仍需关注睡眠、疲劳和连续高强度训练风险。";
    }

    private List<String> buildWeeklyPlan(String goalType, int frequency, int duration) {
        int perSession = Math.max(25, duration / Math.max(1, frequency));
        return switch (goalType) {
            case "TEN_K_IMPROVEMENT" -> List.of(
                    "每周 %d 次跑步训练，每次约 %d 分钟".formatted(frequency, perSession),
                    "安排 1 次节奏跑或间歇跑，其余以轻松跑为主",
                    "每周至少 1 天完全休息或主动恢复");
            case "MUSCLE_GAIN" -> List.of(
                    "每周 %d 次力量训练，每次约 %d 分钟".formatted(frequency, perSession),
                    "记录深蹲、推、拉等主要动作重量，逐周小幅递进",
                    "每周保留 1-2 次低强度有氧或拉伸恢复");
            case "RECOVERY" -> List.of(
                    "每周 %d 次低强度活动，每次约 %d 分钟".formatted(frequency, perSession),
                    "避免连续高强度训练，优先恢复睡眠和关节状态",
                    "若疼痛或疲劳加重，应暂停训练并降低目标");
            default -> List.of(
                    "每周 %d 次训练，每次约 %d 分钟".formatted(frequency, perSession),
                    "有氧、力量和灵活性训练组合安排",
                    "训练后记录主观疲劳，作为下周调整依据");
        };
    }

    private List<String> buildMilestones(String goalType, LocalDate targetDate) {
        LocalDate now = LocalDate.now();
        return List.of(
                "第 1 阶段（%s 前）：建立稳定训练节奏".formatted(now.plusWeeks(2)),
                "第 2 阶段（%s 前）：达到目标周频率和周时长".formatted(now.plusWeeks(4)),
                "截止阶段（%s 前）：完成目标评估并生成下一阶段计划".formatted(targetDate));
    }

    private String formatActivities(List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return "暂无训练明细";
        }
        return activities.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(12)
                .map(activity -> "%s %s %d分钟 %dkcal".formatted(
                        activity.getStartTime() == null ? "未知时间" : activity.getStartTime().toLocalDate(),
                        defaultText(activity.getType()),
                        activity.getDuration() == null ? 0 : activity.getDuration(),
                        activity.getCalorieBurned() == null ? 0 : activity.getCalorieBurned()))
                .collect(Collectors.joining("；"));
    }

    private String formatDistribution(Map<String, Long> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return "无";
        }
        return distribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private String normalizeGoalType(String value) {
        String goalType = firstNonBlank(value, "GENERAL_FITNESS").trim().toUpperCase(Locale.ROOT);
        return switch (goalType) {
            case "FAT_LOSS", "WEIGHT_LOSS", "MUSCLE_GAIN", "ENDURANCE", "TEN_K_IMPROVEMENT", "GENERAL_FITNESS", "RECOVERY" -> goalType;
            default -> "GENERAL_FITNESS";
        };
    }

    private String inferExperience(UserHistorySummary summary) {
        if (summary.getActivitiesLast30Days() >= 16) return "ADVANCED";
        if (summary.getActivitiesLast30Days() >= 8) return "INTERMEDIATE";
        if (summary.getActivitiesLast30Days() >= 3) return "NOVICE";
        return "BEGINNER";
    }

    private String defaultUnit(String goalType) {
        return switch (goalType) {
            case "TEN_K_IMPROVEMENT" -> "公里";
            case "ENDURANCE" -> "分钟";
            case "FAT_LOSS", "WEIGHT_LOSS" -> "公斤";
            case "RECOVERY" -> "周恢复周期";
            default -> "次训练";
        };
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return "{}";
        }
        String trimmed = content.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private List<String> readStringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            if (StringUtils.hasText(item.asText())) {
                values.add(item.asText());
            }
        });
        return values.isEmpty() ? fallback : values;
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return StringUtils.hasText(value) ? LocalDate.parse(value) : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Integer validInt(int value, Integer fallback) {
        return value > 0 ? value : fallback;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "未设置";
    }

    private record GoalTarget(BigDecimal value, String unit) {
    }
}
