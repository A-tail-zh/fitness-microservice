package com.fitness.aiservice.service;

import com.fitness.aiservice.client.UserProfileClient;
import com.fitness.aiservice.dto.AiAnalysisContext;
import com.fitness.aiservice.dto.EmailNotificationEvent;
import com.fitness.aiservice.dto.GoalAnalysisResult;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserGoalProfile;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.NotificationType;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAiService {

    private final QwenService qwenService;
    private final RecommendationRepository recommendationRepository;
    private final AiAnalysisOrchestratorService aiAnalysisOrchestratorService;
    private final EmailNotificationProducer emailNotificationProducer;
    private final UserProfileClient userProfileClient;

    public Recommendation generateAndSaveRecommendation(Activity activity) {
        log.info("Generating AI recommendation for activityId={}, userId={}", activity.getId(), activity.getUserId());

        AiAnalysisContext context = aiAnalysisOrchestratorService.buildContext(activity);
        String content;
        try {
            String rawResponse = qwenService.getAnswer(createPromptForContext(context));
            content = blankToDefault(qwenService.extractContent(rawResponse), buildFallbackAnalysis(context, "Empty AI response"));
        } catch (Exception ex) {
            log.warn("AI recommendation generation failed, activityId={}, reason={}", activity.getId(), ex.getMessage());
            content = buildFallbackAnalysis(context, ex.getMessage());
        }

        Recommendation recommendation = buildRecommendation(context, content);
        recommendationRepository.findLatestStandardByActivityId(activity.getId()).ifPresent(existing -> {
            recommendation.setId(existing.getId());
            log.info("Updating existing recommendation for activityId={}", activity.getId());
        });

        Recommendation saved = recommendationRepository.save(recommendation);
        log.info("AI recommendation saved, recommendationId={}", saved.getId());
        publishRecommendationEmail(saved);
        return saved;
    }

    private void publishRecommendationEmail(Recommendation recommendation) {
        try {
            UserProfileClient.UserProfileResponse user = userProfileClient.resolveUser(recommendation.getUserId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.debug("Skip AI recommendation email because user email is empty, userId={}", recommendation.getUserId());
                return;
            }

            String username = (blankToDefault(user.getLastName(), "") + blankToDefault(user.getFirstName(), "")).trim();
            if (username.isBlank()) {
                username = blankToDefault(user.getEmail(), "User");
            }

            String content = blankToDefault(recommendation.getRecommendation(), "Training analysis report generated.");
            emailNotificationProducer.publish(EmailNotificationEvent.builder()
                    .eventId("AI_REPORT_GENERATED:recommendation:" + recommendation.getId() + ":" + UUID.randomUUID())
                    .userId(recommendation.getUserId())
                    .to(user.getEmail())
                    .username(username)
                    .type(NotificationType.AI_REPORT_GENERATED)
                    .subject("LvDong training analysis report generated")
                    .payload(Map.of(
                            "analysisType", "Daily training analysis",
                            "analysisTime", LocalDateTime.now().toString(),
                            "overallScore", "N/A",
                            "overallStatus", "Generated",
                            "overview", "Activity type: " + blankToDefault(recommendation.getActivityType(), "UNKNOWN"),
                            "riskTips", List.of("Review your body feedback before the next workout."),
                            "recoveryAdvice", "Plan recovery based on this training load.",
                            "suggestions", List.of(content),
                            "summary", content
                    ))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("AI recommendation email enqueue failed, activityId={}, reason={}",
                    recommendation.getActivityId(), ex.getMessage());
        }
    }

    public String createPromptForContext(AiAnalysisContext context) {
        Activity activity = context.getCurrentActivity();
        UserHistorySummary summary = safeSummary(context.getHistorySummary(), activity);
        RuleAnalysisResult rule = context.getRuleAnalysisResult();
        UserGoalProfile goal = context.getUserGoalProfile();
        GoalAnalysisResult goalAnalysis = context.getGoalAnalysisResult();

        return String.format("""
                你是“律动”系统中的专业运动训练教练，请基于训练记录、历史趋势、恢复状态和目标进度，生成一份详细、完整、自然的中文分析报告。

                硬性要求：
                1. 全文必须使用标准中文，不要夹杂英文标题、字段名、JSON 键名、代码块或表格字段。
                2. 报告详细程度至少达到常规简报的 3 倍，正文建议 900 到 1500 字，不能只给泛泛而谈的结论。
                3. 每个判断都要尽量引用已知数据，例如近 7 天训练次数、训练时长、热量、恢复状态、目标完成度、训练趋势、主项分布。
                4. 不要输出“Type: / Duration: / Suggestions”这类字段式表达，要写成自然中文句子。
                5. 如果信息不足，要明确说出“目前无法直接判断的部分”，但仍需给出稳妥可执行建议。
                6. 建议必须具体，说明为什么这样安排、怎么做、做多久、要注意什么。
                7. 不要重复同义建议；相近建议合并后写得更完整。

                当前训练：
                - 用户 ID：%s
                - 训练 ID：%s
                - 训练类型：%s
                - 持续时间：%d 分钟
                - 消耗热量：%d 千卡
                - 开始时间：%s
                - 附加指标：
                %s

                近期训练摘要：
                - 历史总训练次数：%d
                - 近 7 天训练次数：%d
                - 近 30 天训练次数：%d
                - 近 7 天训练时长：%d 分钟
                - 近 30 天训练时长：%d 分钟
                - 近 7 天消耗热量：%d 千卡
                - 近 30 天消耗热量：%d 千卡
                - 近 7 天平均单次时长：%.1f 分钟
                - 近 7 天平均单次热量：%.1f 千卡
                - 主要训练类型：%s
                - 连续活跃天数：%d
                - 距离上一次训练后的间隔天数：%d
                - 训练趋势：%s
                - 执行度：%s
                - 类型分布：
                %s
                - 近期训练列表：
                %s

                规则分析：
                - 训练负荷：%s
                - 恢复状态：%s
                - 训练连续性：%s
                - 亮点：
                %s
                - 风险：
                %s
                - 规则建议：
                %s

                目标画像：
                - 目标类型：%s
                - 目标值：%s %s
                - 每周目标频次：%s
                - 每周目标时长：%s
                - 目标日期：%s
                - 训练经验：%s
                - 当前优先级：%s
                - 目标备注：%s

                目标分析：
                - 进度状态：%s
                - 匹配程度：%s
                - 完成度评分：%s
                - 匹配度评分：%s
                - 可行性评分：%s
                - 预计达成周数：%s
                - 下一里程碑：%s
                - 调整原因：%s
                - 建议修订：%s
                - 优势：
                %s
                - 差距：
                %s
                - 行动方向：
                %s

                请严格按以下中文章节输出，不要增加别的标题：
                一、综合结论
                要求：3 到 5 句话，先总结本次训练和近期训练状态，再说明对当前目标推进的实际意义。

                二、问题分析
                要求：输出 4 到 6 条，每条单独一行，以“- ”开头。重点分析训练负荷、恢复、连续性、目标推进、训练结构是否合理，并解释原因。

                三、优化建议
                要求：输出 5 到 8 条，每条单独一行，以“- ”开头。每条建议都要写清执行动作、时间窗口、强度方向或注意事项。

                四、下次训练计划
                要求：输出 4 到 6 条，每条单独一行，以“- ”开头。必须包含下次训练类型、建议时长、强度、训练重点、是否需要恢复或减量。

                五、长期提醒
                要求：输出 3 到 4 条，每条单独一行，以“- ”开头。重点写接下来 1 到 2 周最该坚持的策略，以及需要补充记录的关键数据。
                """,
                activity.getUserId(),
                activity.getId(),
                blankToDefault(activity.getType(), "UNKNOWN"),
                safeInt(activity.getDuration()),
                safeInt(activity.getCalorieBurned()),
                activity.getStartTime(),
                formatMetrics(activity),
                summary.getTotalActivities(),
                summary.getActivitiesLast7Days(),
                summary.getActivitiesLast30Days(),
                summary.getTotalDurationLast7Days(),
                summary.getTotalDurationLast30Days(),
                summary.getTotalCaloriesLast7Days(),
                summary.getTotalCaloriesLast30Days(),
                summary.getAvgDurationLast7Days(),
                summary.getAvgCaloriesLast7Days(),
                blankToDefault(summary.getMostFrequentActivityType(), "UNKNOWN"),
                summary.getConsecutiveActiveDays(),
                summary.getInactiveDaysSinceLastActivity() == Integer.MAX_VALUE ? 999 : summary.getInactiveDaysSinceLastActivity(),
                blankToDefault(summary.getTrend(), "UNKNOWN"),
                blankToDefault(summary.getAdherenceLevel(), "UNKNOWN"),
                formatDistribution(summary),
                formatRecentActivities(context.getRecentActivities()),
                rule == null ? "UNKNOWN" : blankToDefault(rule.getTrainingLoadLevel(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : blankToDefault(rule.getRecoveryStatus(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : blankToDefault(rule.getConsistencyLevel(), "UNKNOWN"),
                joinLines(rule == null ? null : rule.getHighlights(), "- No highlights"),
                joinLines(rule == null ? null : rule.getRisks(), "- No obvious risk"),
                joinLines(rule == null ? null : rule.getSuggestions(), "- Keep a stable training rhythm"),
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
                safeGoalFeasibilityScore(goalAnalysis),
                safeGoalEstimatedWeeks(goalAnalysis),
                safeGoalNextMilestone(goalAnalysis),
                safeGoalAdjustmentReason(goalAnalysis),
                safeGoalRevision(goalAnalysis),
                joinLines(goalAnalysis == null ? null : goalAnalysis.getStrengths(), "- No goal strength data"),
                joinLines(goalAnalysis == null ? null : goalAnalysis.getGaps(), "- No goal gap data"),
                joinLines(goalAnalysis == null ? null : goalAnalysis.getActionSuggestions(), "- No goal action data")
        );
    }

    private String buildFallbackAnalysis(AiAnalysisContext context, String reason) {
        Activity activity = context.getCurrentActivity();
        UserHistorySummary summary = safeSummary(context.getHistorySummary(), activity);
        RuleAnalysisResult rule = context.getRuleAnalysisResult();
        GoalAnalysisResult goalAnalysis = context.getGoalAnalysisResult();

        String recoveryStatus = rule == null ? "UNKNOWN" : blankToDefault(rule.getRecoveryStatus(), "UNKNOWN");
        String loadLevel = rule == null ? "UNKNOWN" : blankToDefault(rule.getTrainingLoadLevel(), "UNKNOWN");
        String consistencyLevel = rule == null ? "UNKNOWN" : blankToDefault(rule.getConsistencyLevel(), "UNKNOWN");
        String priority = determinePriority(recoveryStatus, consistencyLevel, goalAnalysis);

        return String.format("""
                一、综合结论
                本次训练以%s为主，单次持续%d分钟、消耗%d千卡。从近 7 天 %d 次、近 30 天 %d 次训练来看，你当前的训练节奏已经形成基础轨迹，但训练质量是否稳定，还要结合负荷、恢复和目标推进一起判断。系统判断当前训练负荷为%s、连续性为%s、恢复状态为%s，说明你下一步最关键的不是盲目加量，而是先确认训练安排是否与身体恢复和目标节奏一致。目标进度当前为%s，目标匹配度为%s。由于 AI 主模型本次未稳定返回，以下结论由规则分析与历史训练数据综合生成，原因：%s。

                二、问题分析
                %s
                - 当前最需要优先处理的是：%s。
                - 如果接下来一周仍然延续当前节奏但缺少恢复安排，训练效果可能会被疲劳累积、连续性不足或目标偏离进一步放大。
                - 目前的数据更擅长判断训练量与频率，对主观疲劳、睡眠质量和动作完成度的判断仍然有限，因此后续记录越完整，分析会越准确。
                - 本次判断不能只看单次表现，更要看它是否能稳定嵌入你未来 1 到 2 周的训练结构中。

                三、优化建议
                %s
                - 接下来一周只保留一个主目标：%s，并围绕这个主目标安排训练，不要同时追求频率、强度和训练量一起上升。
                - 每次训练后补充疲劳感、疼痛位置、睡眠、心率或配速等真实反馈，后续系统会优先基于这些数据判断，而不是只看时长和热量。
                - 如果身体感觉明显变差，优先减量或恢复，而不是硬性完成计划。
                - 建议把下一次训练后的恢复感受与本次对比，确认当前调整是否真正有效。

                四、下次训练计划
                - 下次训练建议以%s为主。
                - 建议时长控制在%d到%d分钟之间，先保证完成质量，再考虑追加训练量。
                - 强度方向建议为%s，重点不是拼强度，而是让身体在可恢复的前提下继续建立连续性。
                - 本次训练的执行重点应放在%s。
                - 如果训练后 24 小时内仍然疲劳明显、局部酸痛异常或精神状态下降，下一次训练继续下调强度。

                五、长期提醒
                - 未来 1 到 2 周的核心任务是维持节奏，而不是追求单次爆发。
                - 每周都要复盘训练频率、总时长和恢复感受是否同步改善，否则即使训练次数增加，也不一定等于有效进步。
                - 当负荷提高时，要同步增加恢复管理，否则训练收益会被抵消。
                - 如果目标推进连续两周没有改善，就应主动调整计划，而不是继续机械重复原方案。
                """,
                blankToDefault(activity.getType(), "UNKNOWN"),
                safeInt(activity.getDuration()),
                safeInt(activity.getCalorieBurned()),
                summary.getActivitiesLast7Days(),
                summary.getActivitiesLast30Days(),
                loadLevel,
                consistencyLevel,
                recoveryStatus,
                safeGoalProgress(goalAnalysis),
                safeGoalAlignment(goalAnalysis),
                blankToDefault(reason, "AI response unavailable"),
                joinLines(rule == null ? null : rule.getRisks(), "- No major risk detected"),
                joinLines(rule == null ? null : rule.getSuggestions(), "- Keep training stable and adjust by recovery status"),
                chooseNextActivityType(context.getUserGoalProfile(), activity),
                recommendedMinDuration(recoveryStatus, activity),
                recommendedMaxDuration(recoveryStatus, activity),
                recommendedIntensity(recoveryStatus, loadLevel, consistencyLevel),
                priority
        );
    }

    private Recommendation buildRecommendation(AiAnalysisContext context, String content) {
        Activity activity = context.getCurrentActivity();
        return Recommendation.builder()
                .recommendationType("STANDARD")
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation(content)
                .improvements(extractSection(content, "二、问题分析", "三、优化建议").isEmpty()
                        ? extractSection(content, "Issues", "Suggestions")
                        : extractSection(content, "二、问题分析", "三、优化建议"))
                .suggestions(extractSection(content, "三、优化建议", "四、下次训练计划").isEmpty()
                        ? extractSection(content, "Suggestions", "Next workout plan")
                        : extractSection(content, "三、优化建议", "四、下次训练计划"))
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
                    .map(line -> line.replaceFirst("^[#*\\-\\d.、\\s]+", ""))
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to extract recommendation section, startMarker={}", startMarker, e);
            return List.of();
        }
    }

    private String determinePriority(String recoveryStatus, String consistencyLevel, GoalAnalysisResult goalAnalysis) {
        if ("FATIGUE_RISK".equals(recoveryStatus)) {
            return "恢复优先";
        }
        if ("LOW".equals(consistencyLevel) || "DETRAINING_RISK".equals(recoveryStatus)) {
            return "先恢复训练连续性";
        }
        if (goalAnalysis != null && ("OFF_TRACK".equals(goalAnalysis.getProgressStatus())
                || "SERIOUSLY_OFF_TRACK".equals(goalAnalysis.getProgressStatus()))) {
            return "修正目标推进节奏";
        }
        return "维持稳定节奏";
    }

    private String chooseNextActivityType(UserGoalProfile goal, Activity activity) {
        String goalType = goal == null ? "" : blankToDefault(goal.getGoalType(), "");
        return switch (goalType) {
            case "TEN_K_IMPROVEMENT", "ENDURANCE" -> "轻松耐力跑";
            case "MUSCLE_GAIN" -> "力量训练";
            case "FAT_LOSS", "WEIGHT_LOSS" -> "中等强度有氧配合基础力量";
            case "RECOVERY" -> "低冲击恢复训练";
            default -> blankToDefault(activity.getType(), "CARDIO");
        };
    }

    private int recommendedMinDuration(String recoveryStatus, Activity activity) {
        if ("FATIGUE_RISK".equals(recoveryStatus)) {
            return 20;
        }
        if ("DETRAINING_RISK".equals(recoveryStatus)) {
            return 25;
        }
        return Math.max(20, safeInt(activity.getDuration()) - 10);
    }

    private int recommendedMaxDuration(String recoveryStatus, Activity activity) {
        if ("FATIGUE_RISK".equals(recoveryStatus)) {
            return 35;
        }
        if ("DETRAINING_RISK".equals(recoveryStatus)) {
            return 40;
        }
        return Math.max(30, safeInt(activity.getDuration()) + 10);
    }

    private String recommendedIntensity(String recoveryStatus, String loadLevel, String consistencyLevel) {
        if ("FATIGUE_RISK".equals(recoveryStatus) || "HIGH".equals(loadLevel)) {
            return "低强度，以主观用力感 3 到 4 分为宜";
        }
        if ("LOW".equals(consistencyLevel) || "DETRAINING_RISK".equals(recoveryStatus)) {
            return "轻到中等强度，以重新建立节奏为主";
        }
        return "中等强度，循序渐进推进";
    }

    private UserHistorySummary safeSummary(UserHistorySummary summary, Activity activity) {
        if (summary != null) {
            return summary;
        }
        return UserHistorySummary.builder()
                .userId(activity.getUserId())
                .totalActivities(1)
                .activitiesLast7Days(1)
                .activitiesLast30Days(1)
                .totalDurationLast7Days(safeInt(activity.getDuration()))
                .totalDurationLast30Days(safeInt(activity.getDuration()))
                .totalCaloriesLast7Days(safeInt(activity.getCalorieBurned()))
                .totalCaloriesLast30Days(safeInt(activity.getCalorieBurned()))
                .avgDurationLast7Days(safeInt(activity.getDuration()))
                .avgDurationLast30Days(safeInt(activity.getDuration()))
                .avgCaloriesLast7Days(safeInt(activity.getCalorieBurned()))
                .avgCaloriesLast30Days(safeInt(activity.getCalorieBurned()))
                .mostFrequentActivityType(blankToDefault(activity.getType(), "UNKNOWN"))
                .inactiveDaysSinceLastActivity(0)
                .trend("UNKNOWN")
                .adherenceLevel("UNKNOWN")
                .recentActivityTypes(List.of(blankToDefault(activity.getType(), "UNKNOWN")))
                .activityTypeDistribution(Map.of(blankToDefault(activity.getType(), "UNKNOWN"), 1L))
                .build();
    }

    private String formatMetrics(Activity activity) {
        if (activity.getAdditionalMetrics() == null || activity.getAdditionalMetrics().isEmpty()) {
            return "- No extra metrics";
        }
        return activity.getAdditionalMetrics().entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String formatRecentActivities(List<Activity> recentActivities) {
        if (recentActivities == null || recentActivities.isEmpty()) {
            return "- No recent activity data";
        }
        return recentActivities.stream()
                .map(activity -> String.format(
                        "- %s | %s | %d minutes | %d kcal",
                        activity.getStartTime(),
                        blankToDefault(activity.getType(), "UNKNOWN"),
                        safeInt(activity.getDuration()),
                        safeInt(activity.getCalorieBurned())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatDistribution(UserHistorySummary summary) {
        if (summary == null || summary.getActivityTypeDistribution() == null || summary.getActivityTypeDistribution().isEmpty()) {
            return "NONE";
        }
        return summary.getActivityTypeDistribution().entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));
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
        return goal == null || goal.getTargetValue() == null ? "N/A" : goal.getTargetValue().toPlainString();
    }

    private String safeGoalUnit(UserGoalProfile goal) {
        return goal == null ? "" : blankToDefault(goal.getTargetUnit(), "");
    }

    private String safeGoalFrequency(UserGoalProfile goal) {
        return goal == null || goal.getWeeklyTargetFrequency() == null ? "N/A" : String.valueOf(goal.getWeeklyTargetFrequency());
    }

    private String safeGoalDuration(UserGoalProfile goal) {
        return goal == null || goal.getWeeklyTargetDuration() == null ? "N/A" : String.valueOf(goal.getWeeklyTargetDuration());
    }

    private String safeGoalDate(UserGoalProfile goal) {
        return goal == null || goal.getTargetDate() == null ? "N/A" : goal.getTargetDate().toString();
    }

    private String safeGoalExperience(UserGoalProfile goal) {
        return goal == null ? "N/A" : blankToDefault(goal.getExperienceLevel(), "N/A");
    }

    private String safeGoalPriority(UserGoalProfile goal) {
        return goal == null ? "N/A" : blankToDefault(goal.getPriority(), "N/A");
    }

    private String safeGoalNote(UserGoalProfile goal) {
        return goal == null ? "NONE" : blankToDefault(goal.getNote(), "NONE");
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

    private String safeGoalFeasibilityScore(GoalAnalysisResult result) {
        return result == null || result.getFeasibilityScore() == null ? "0" : String.valueOf(result.getFeasibilityScore());
    }

    private String safeGoalEstimatedWeeks(GoalAnalysisResult result) {
        return result == null || result.getEstimatedWeeksToGoal() == null ? "UNKNOWN" : String.valueOf(result.getEstimatedWeeksToGoal());
    }

    private String safeGoalNextMilestone(GoalAnalysisResult result) {
        return result == null ? "N/A" : blankToDefault(result.getNextMilestone(), "N/A");
    }

    private String safeGoalAdjustmentReason(GoalAnalysisResult result) {
        return result == null ? "N/A" : blankToDefault(result.getAdjustmentReason(), "N/A");
    }

    private String safeGoalRevision(GoalAnalysisResult result) {
        return result == null ? "N/A" : blankToDefault(result.getRecommendedGoalRevision(), "N/A");
    }
}
