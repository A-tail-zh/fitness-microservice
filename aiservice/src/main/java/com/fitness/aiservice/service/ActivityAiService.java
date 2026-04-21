package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.*;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAiService {

    private final QwenService qwenService;
    private final RecommendationRepository recommendationRepository;
    private final AiAnalysisOrchestratorService aiAnalysisOrchestratorService;

    public Recommendation generateAndSaveRecommendation(Activity activity) {
        log.info("开始为活动 [{}] 生成 AI 建议，用户: {}", activity.getId(), activity.getUserId());

        AiAnalysisContext context = aiAnalysisOrchestratorService.buildContext(activity);

        String prompt = createPromptForContext(context);
        String rawResponse = qwenService.getAnswer(prompt);
        String content = qwenService.extractContent(rawResponse);

        Recommendation recommendation = buildRecommendation(context, content);

        recommendationRepository.findLatestStandardByActivityId(activity.getId()).ifPresent(existing -> {
            recommendation.setId(existing.getId());
            log.info("活动 [{}] 已有建议，执行更新", activity.getId());
        });

        Recommendation saved = recommendationRepository.save(recommendation);
        log.info("AI 建议已保存，ID: {}", saved.getId());
        return saved;
    }

    private Recommendation buildRecommendation(AiAnalysisContext context, String content) {
        Activity activity = context.getCurrentActivity();
        List<String> improvements = extractSection(content, "问题分析：", "优化建议：");
        List<String> suggestions = extractSection(content, "优化建议：", "下次训练计划：");

        return Recommendation.builder()
                .recommendationType("STANDARD")
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation(content)
                .improvements(improvements)
                .suggestions(suggestions)
                .historySummary(context.getHistorySummary())
                .ruleAnalysisResult(context.getRuleAnalysisResult())
                .userGoalProfile(context.getUserGoalProfile())
                .goalAnalysisResult(context.getGoalAnalysisResult())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSection(String content, String startMarker, String endMarker) {
        try {
            int start = content.indexOf(startMarker);
            int end = endMarker != null ? content.indexOf(endMarker) : -1;

            if (start == -1) {
                return List.of();
            }

            String section = (end == -1 || end <= start)
                    ? content.substring(start + startMarker.length())
                    : content.substring(start + startMarker.length(), end);

            return Arrays.stream(section.split("\\n"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("提取段落失败: startMarker={}", startMarker, e);
            return List.of();
        }
    }

    public String createPromptForContext(AiAnalysisContext context) {
        Activity activity = context.getCurrentActivity();
        UserHistorySummary summary = context.getHistorySummary();
        RuleAnalysisResult rule = context.getRuleAnalysisResult();
        UserGoalProfile goal = context.getUserGoalProfile();
        GoalAnalysisResult goalAnalysis = context.getGoalAnalysisResult();

        String metrics = formatMetrics(activity);
        String recentActivities = formatRecentActivities(context.getRecentActivities());
        String distribution = formatDistribution(summary);
        String highlights = joinLines(rule == null ? null : rule.getHighlights(), "- 暂无明显亮点");
        String risks = joinLines(rule == null ? null : rule.getRisks(), "- 暂无明确风险，但仍需关注恢复");
        String suggestions = joinLines(rule == null ? null : rule.getSuggestions(), "- 维持当前节奏并持续观察");

        String goalStrengths = joinLines(goalAnalysis == null ? null : goalAnalysis.getStrengths(), "- 暂无明显优势");
        String goalGaps = joinLines(goalAnalysis == null ? null : goalAnalysis.getGaps(), "- 暂无明显偏差");
        String goalActions = joinLines(goalAnalysis == null ? null : goalAnalysis.getActionSuggestions(), "- 继续保持当前节奏");

        //【恢复状态说明】
        //- BALANCED：训练与恢复基本平衡，可以继续按计划推进
        //- FATIGUE_RISK：近期训练负荷偏高，存在疲劳积累风险，需要优先恢复
        //- DETRAINING_RISK：近期训练不足或间隔偏长，存在状态退化和节奏中断风险
        return String.format("""
                你是一名专业的运动科学教练、训练计划分析师和恢复管理顾问。
                请基于“当前训练 + 历史记录聚合摘要 + 规则判断结果 + 用户目标分析”输出个性化建议。
                你的重点不是复述数据，而是解释这些数据意味着什么，以及下一步应该怎么做。

                【本次训练数据】
                - 用户ID：%s
                - 活动ID：%s
                - 运动类型：%s
                - 持续时间：%d 分钟
                - 消耗卡路里：%d kcal
                - 开始时间：%s

                【本次训练附加指标】
                %s

                【历史聚合摘要】
                - 总训练次数：%d
                - 最近7天训练次数：%d
                - 最近30天训练次数：%d
                - 最近7天总时长：%d 分钟
                - 最近30天总时长：%d 分钟
                - 最近7天总消耗：%d kcal
                - 最近30天总消耗：%d kcal
                - 最近7天平均时长：%.1f 分钟
                - 最近30天平均时长：%.1f 分钟
                - 最近7天平均消耗：%.1f kcal
                - 最近30天平均消耗：%.1f kcal
                - 最常见训练类型：%s
                - 连续活跃天数：%d
                - 距离上次训练间隔：%d 天
                - 训练趋势：%s
                - 训练执行度：%s
                - 首次训练时间：%s
                - 最近一次训练时间：%s
                - 最近常见训练类型：%s
                - 活动类型分布：%s

                【最近训练记录（最多10条）】
                %s

                【规则判断结果】
                - 训练负荷等级：%s
                - 恢复状态：%s
                - 连续性等级：%s

                【恢复状态说明】
                - BALANCED：训练与恢复基本平衡，可以继续按计划推进
                - FATIGUE_RISK：近期训练负荷偏高，存在疲劳积累风险，需要优先恢复
                - DETRAINING_RISK：近期训练不足或间隔偏长，存在状态退化和节奏中断风险

                【规则识别亮点】
                %s

                【规则识别风险】
                %s

                【规则建议】
                %s

                【用户目标档案】
                - 当前目标类型：%s
                - 目标值：%s %s
                - 每周目标频率：%s 次
                - 每周目标总时长：%s 分钟
                - 目标截止日期：%s
                - 经验等级：%s
                - 优先级：%s
                - 备注：%s

                【目标驱动分析结果】
                - 目标推进状态：%s
                - 目标匹配等级：%s
                - 目标完成度分数：%s
                - 目标对齐度分数：%s

                【目标分析优势】
                %s

                【目标分析偏差】
                %s

                【目标分析建议】
                %s

                【写作要求】
                1. 必须优先判断当前训练模式是否与用户目标一致，而不是只描述本次训练。
                2. 必须结合历史记录判断是短期波动还是长期趋势。
                3. 如果恢复状态是 FATIGUE_RISK，要明确提醒控制疲劳、降低负荷、优先恢复。
                4. 如果恢复状态是 DETRAINING_RISK，要明确提醒先恢复规律训练节奏，而不是直接上强度。
                5. 如果恢复状态是 BALANCED，可以肯定当前节奏，但仍要指出下一步优化空间。
                6. 如果当前训练与目标不匹配，必须明确指出不匹配点。
                7. 给出的建议必须具体、可执行、可落地，避免空泛表达。
                8. 下次训练计划要包含时长、强度方向和频率建议。
                9. 如果用户近期训练不足，优先强调连续性和习惯重建。
                10. 如果用户近期训练偏多，优先强调恢复和疲劳管理。

                【输出格式（必须严格遵守）】
                训练评估：
                - ...

                问题分析：
                - ...

                优化建议：
                - ...

                下次训练计划：
                - ...

                【输出要求】
                - 使用中文
                - 语言简洁、专业、像真人教练
                - 每一点尽量结合历史数据、趋势和目标
                - 不要输出 JSON
                - 不要输出无关解释
                """,
                activity.getUserId(),
                activity.getId(),
                blankToDefault(activity.getType(), "UNKNOWN"),
                safeInt(activity.getDuration()),
                safeInt(activity.getCalorieBurned()),
                activity.getStartTime(),
                metrics,
                summary.getTotalActivities(),
                summary.getActivitiesLast7Days(),
                summary.getActivitiesLast30Days(),
                summary.getTotalDurationLast7Days(),
                summary.getTotalDurationLast30Days(),
                summary.getTotalCaloriesLast7Days(),
                summary.getTotalCaloriesLast30Days(),
                summary.getAvgDurationLast7Days(),
                summary.getAvgDurationLast30Days(),
                summary.getAvgCaloriesLast7Days(),
                summary.getAvgCaloriesLast30Days(),
                blankToDefault(summary.getMostFrequentActivityType(), "UNKNOWN"),
                summary.getConsecutiveActiveDays(),
                summary.getInactiveDaysSinceLastActivity() == Integer.MAX_VALUE ? 999 : summary.getInactiveDaysSinceLastActivity(),
                blankToDefault(summary.getTrend(), "UNKNOWN"),
                blankToDefault(summary.getAdherenceLevel(), "UNKNOWN"),
                summary.getFirstActivityTime(),
                summary.getLatestActivityTime(),
                formatRecentActivityTypes(summary.getRecentActivityTypes()),
                distribution,
                recentActivities,
                rule == null ? "UNKNOWN" : blankToDefault(rule.getTrainingLoadLevel(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : blankToDefault(rule.getRecoveryStatus(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : blankToDefault(rule.getConsistencyLevel(), "UNKNOWN"),
                highlights,
                risks,
                suggestions,
                safeGoalType(goal),
                safeGoalValue(goal),
                safeGoalUnit(goal),
                safeGoalFrequency(goal),
                safeGoalDuration(goal),
                safeGoalDate(goal),
                safeGoalExperience(goal),
                safeGoalPriority(goal),
                safeGoalNote(goal),
                safeGoalProgress(goalAnalysis),
                safeGoalAlignment(goalAnalysis),
                safeGoalCompletionScore(goalAnalysis),
                safeGoalAlignmentScore(goalAnalysis),
                goalStrengths,
                goalGaps,
                goalActions
        );
    }

    private String formatMetrics(Activity activity) {
        if (activity.getAdditionalMetrics() == null || activity.getAdditionalMetrics().isEmpty()) {
            return "- 无额外指标";
        }

        return activity.getAdditionalMetrics().entrySet().stream()
                .map(entry -> "- " + entry.getKey() + "：" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String formatRecentActivities(List<Activity> recentActivities) {
        if (recentActivities == null || recentActivities.isEmpty()) {
            return "- 暂无历史训练记录";
        }

        return recentActivities.stream()
                .map(activity -> String.format(
                        "- %s | %s | %d 分钟 | %d kcal",
                        activity.getStartTime(),
                        blankToDefault(activity.getType(), "UNKNOWN"),
                        safeInt(activity.getDuration()),
                        safeInt(activity.getCalorieBurned())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatDistribution(UserHistorySummary summary) {
        if (summary == null || summary.getActivityTypeDistribution() == null || summary.getActivityTypeDistribution().isEmpty()) {
            return "无";
        }

        return summary.getActivityTypeDistribution().entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private String formatRecentActivityTypes(List<String> recentActivityTypes) {
        if (recentActivityTypes == null || recentActivityTypes.isEmpty()) {
            return "无";
        }
        return String.join(", ", recentActivityTypes);
    }

    private String joinLines(List<String> lines, String defaultValue) {
        if (lines == null || lines.isEmpty()) {
            return defaultValue;
        }
        return lines.stream()
                .map(line -> line.startsWith("-") ? line : "- " + line)
                .collect(Collectors.joining("\n"));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String safeGoalType(UserGoalProfile goal) {
        return goal == null ? "NONE" : blankToDefault(goal.getGoalType(), "NONE");
    }

    private String safeGoalValue(UserGoalProfile goal) {
        return goal == null || goal.getTargetValue() == null ? "未设置" : goal.getTargetValue().toPlainString();
    }

    private String safeGoalUnit(UserGoalProfile goal) {
        return goal == null ? "" : blankToDefault(goal.getTargetUnit(), "");
    }

    private String safeGoalFrequency(UserGoalProfile goal) {
        return goal == null || goal.getWeeklyTargetFrequency() == null ? "未设置" : String.valueOf(goal.getWeeklyTargetFrequency());
    }

    private String safeGoalDuration(UserGoalProfile goal) {
        return goal == null || goal.getWeeklyTargetDuration() == null ? "未设置" : String.valueOf(goal.getWeeklyTargetDuration());
    }

    private String safeGoalDate(UserGoalProfile goal) {
        return goal == null || goal.getTargetDate() == null ? "未设置" : goal.getTargetDate().toString();
    }

    private String safeGoalExperience(UserGoalProfile goal) {
        return goal == null ? "未设置" : blankToDefault(goal.getExperienceLevel(), "未设置");
    }

    private String safeGoalPriority(UserGoalProfile goal) {
        return goal == null ? "未设置" : blankToDefault(goal.getPriority(), "未设置");
    }

    private String safeGoalNote(UserGoalProfile goal) {
        return goal == null ? "无" : blankToDefault(goal.getNote(), "无");
    }

    private String safeGoalProgress(GoalAnalysisResult result) {
        return result == null ? "UNKNOWN" : blankToDefault(result.getProgressStatus(), "UNKNOWN");
    }

    private String safeGoalAlignment(GoalAnalysisResult result) {
        return result == null ? "UNKNOWN" : blankToDefault(result.getAlignmentLevel(), "UNKNOWN");
    }

    private String safeGoalCompletionScore(GoalAnalysisResult result) {
        return result == null || result.getCompletionScore() == null ? "0" : String.valueOf(result.getCompletionScore());
    }

    private String safeGoalAlignmentScore(GoalAnalysisResult result) {
        return result == null || result.getAlignmentScore() == null ? "0" : String.valueOf(result.getAlignmentScore());
    }
}
