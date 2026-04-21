package com.fitness.aiservice.service;

import com.fitness.aiservice.client.UserGoalClient;
import com.fitness.aiservice.dto.*;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.Collectors;

/**
 * 增强分析服务 — 提供结构化多维度健身分析，支持异步执行
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedAnalysisService {

    private final ActivityHistorySummaryService activityHistorySummaryService;
    private final ActivityRuleEngineService activityRuleEngineService;
    private final UserGoalClient userGoalClient;
    private final GoalAnalysisService goalAnalysisService;
    private final RecommendationRepository recommendationRepository;
    private final QwenService qwenService;

    /**
     * 同步执行增强分析（用于直接 API 调用）
     */
    public EnhancedAnalysisResponse analyze(
            EnhancedAnalysisRequest request,
            Activity currentActivity,
            List<Activity> allActivities) {
        log.info("开始增强分析, userId={}, activityId={}, reportType={}",
                request.getUserId(), currentActivity.getId(), request.getReportType());

        String userId = request.getUserId();
        String reportType = normalizeReportType(request.getReportType());

        // 1. 构建历史数据
        List<Activity> historyActivities = allActivities.stream()
                .sorted(Comparator.comparing(Activity::getStartTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        UserHistorySummary summary = activityHistorySummaryService.buildSummary(userId, historyActivities);
        LocalDateTime reportReferenceTime = determineReportEnd(historyActivities, currentActivity, reportType);
        ReportWindow reportWindow = determineReportWindow(reportReferenceTime, reportType);
        List<Activity> recentActivities = selectRecentActivities(
                historyActivities, reportReferenceTime, reportWindow, reportType);
        LocalDateTime reportStart = reportWindow.start();
        LocalDateTime reportEnd = reportWindow.end();
        Activity analysisActivity = "DAILY".equals(reportType)
                ? currentActivity
                : buildPeriodSummaryActivity(userId, reportType, reportReferenceTime, reportStart, reportEnd, recentActivities);

        // 2. 规则引擎多维度分析
        RuleAnalysisResult ruleResult = activityRuleEngineService.analyze(analysisActivity, summary);
        EnhancedAnalysisResponse.TrainingVolume volume = activityRuleEngineService.analyzeVolume(summary);
        EnhancedAnalysisResponse.TrainingLoad load = activityRuleEngineService.analyzeLoad(analysisActivity, summary);
        EnhancedAnalysisResponse.RecoveryStatus recovery = activityRuleEngineService.analyzeRecovery(analysisActivity, summary);
        EnhancedAnalysisResponse.ConsistencyMetrics consistency = activityRuleEngineService.analyzeConsistency(summary);

        // 3. 目标分析
        UserGoalProfile userGoalProfile = fetchGoalSafely(userId);
        GoalAnalysisResult goalAnalysis = goalAnalysisService.analyze(
                userGoalProfile, summary, ruleResult, recentActivities);

        EnhancedAnalysisResponse.GoalAlignment goalAlignment = buildGoalAlignment(goalAnalysis);

        // 4. 构建 AI 上下文并调用 AI
        AiAnalysisContext context = AiAnalysisContext.builder()
                .currentActivity(analysisActivity)
                .recentActivities(recentActivities)
                .historySummary(summary)
                .ruleAnalysisResult(ruleResult)
                .userGoalProfile(userGoalProfile)
                .goalAnalysisResult(goalAnalysis)
                .build();

        String narrativeAnalysis = callAiWithEnhancedPrompt(
                context, request.getUserNote(), reportType, reportStart, reportEnd);

        // 5. 解析 AI 输出中的文本备注洞察
        String userNoteInsight = extractUserNoteInsight(narrativeAnalysis, request.getUserNote());

        // 6. 构建结构化建议和风险列表
        List<EnhancedAnalysisResponse.StructuredSuggestion> structuredSuggestions =
                buildStructuredSuggestions(ruleResult, goalAnalysis, recovery);
        List<EnhancedAnalysisResponse.RiskAlert> riskAlerts = buildRiskAlerts(ruleResult, load, goalAnalysis);

        // 7. 构建下周训练计划和目标预测
        EnhancedAnalysisResponse.WeeklyPlan weeklyPlan = buildWeeklyPlan(ruleResult, recovery, userGoalProfile);
        EnhancedAnalysisResponse.GoalProgressPrediction prediction = buildGoalPrediction(goalAnalysis, summary, userGoalProfile);

        // 8. 计算综合评分
        int overallScore = calculateOverallScore(goalAnalysis, recovery, consistency, load);
        String overallStatus = mapOverallStatus(overallScore);

        log.info("增强分析完成, userId={}, overallScore={}", userId, overallScore);

        EnhancedAnalysisResponse response = EnhancedAnalysisResponse.builder()
                .requestId(UUID.randomUUID().toString())
                .userId(userId)
                .activityId("DAILY".equals(reportType) ? currentActivity.getId() : null)
                .reportType(reportType)
                .periodStart(reportStart)
                .periodEnd(reportEnd)
                .generatedAt(LocalDateTime.now())
                .overallScore(overallScore)
                .overallStatus(overallStatus)
                .dimensions(EnhancedAnalysisResponse.DimensionMetrics.builder()
                        .volume(volume)
                        .load(load)
                        .recovery(recovery)
                        .consistency(consistency)
                        .goalAlignment(goalAlignment)
                        .build())
                .narrativeAnalysis(narrativeAnalysis)
                .suggestions(structuredSuggestions)
                .riskAlerts(riskAlerts)
                .nextWeekPlan(weeklyPlan)
                .goalPrediction(prediction)
                .userNoteInsight(userNoteInsight)
                .build();

        archiveEnhancedAnalysis(response, context);
        return response;
    }

    /**
     * 异步版本 — 用于后台任务或长报告生成
     */
    @Async("analysisTaskExecutor")
    public CompletableFuture<EnhancedAnalysisResponse> analyzeAsync(
            EnhancedAnalysisRequest request,
            Activity currentActivity,
            List<Activity> allActivities) {
        return CompletableFuture.completedFuture(analyze(request, currentActivity, allActivities));
    }

    // ─────────────────────────── AI 提示词构建 ───────────────────────────

    private String normalizeReportType(String reportType) {
        return reportType == null || reportType.isBlank()
                ? "DAILY"
                : reportType.toUpperCase(Locale.ROOT);
    }

    private LocalDateTime determineReportEnd(
            List<Activity> historyActivities,
            Activity currentActivity,
            String reportType) {
        if ("DAILY".equals(reportType) && currentActivity.getStartTime() != null) {
            return currentActivity.getStartTime();
        }

        return historyActivities.stream()
                .map(Activity::getStartTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElseGet(LocalDateTime::now);
    }

    private ReportWindow determineReportWindow(LocalDateTime referenceTime, String reportType) {
        if (referenceTime == null) {
            referenceTime = LocalDateTime.now();
        }

        return switch (reportType) {
            case "MONTHLY" -> {
                LocalDate date = referenceTime.toLocalDate();
                LocalDate startDate = date.withDayOfMonth(1);
                LocalDate endDate = date.with(TemporalAdjusters.lastDayOfMonth());
                yield new ReportWindow(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
            }
            case "WEEKLY" -> {
                LocalDate date = referenceTime.toLocalDate();
                LocalDate startDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate endDate = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                yield new ReportWindow(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
            }
            default -> new ReportWindow(referenceTime, referenceTime);
        };
    }

    private List<Activity> selectRecentActivities(
            List<Activity> historyActivities,
            LocalDateTime anchorTime,
            ReportWindow reportWindow,
            String reportType) {
        LocalDateTime windowStart = "DAILY".equals(reportType)
                ? anchorTime.minusDays(13)
                : reportWindow.start();
        LocalDateTime windowEnd = "DAILY".equals(reportType)
                ? anchorTime
                : reportWindow.end();

        return historyActivities.stream()
                .filter(activity -> activity.getStartTime() != null)
                .filter(activity -> !activity.getStartTime().isBefore(windowStart))
                .filter(activity -> !activity.getStartTime().isAfter(windowEnd))
                .toList();
    }

    private Activity buildPeriodSummaryActivity(
            String userId,
            String reportType,
            LocalDateTime reportReferenceTime,
            LocalDateTime reportStart,
            LocalDateTime reportEnd,
            List<Activity> reportActivities) {
        int totalDuration = reportActivities.stream()
                .map(Activity::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalCalories = reportActivities.stream()
                .map(Activity::getCalorieBurned)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        String dominantType = reportActivities.stream()
                .map(Activity::getType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(reportType + "_SUMMARY");

        Activity summaryActivity = new Activity();
        summaryActivity.setId(reportType + "_PERIOD_SUMMARY");
        summaryActivity.setUserId(userId);
        summaryActivity.setType(dominantType);
        summaryActivity.setDuration(totalDuration);
        summaryActivity.setCalorieBurned(totalCalories);
        summaryActivity.setStartTime(reportReferenceTime);
        summaryActivity.setAdditionalMetrics(Map.of(
                "reportType", reportType,
                "activityCount", reportActivities.size(),
                "periodStart", reportStart.toLocalDate().toString(),
                "periodEnd", reportEnd.toLocalDate().toString()
        ));
        return summaryActivity;
    }

    private String callAiWithEnhancedPrompt(
            AiAnalysisContext context,
            String userNote,
            String reportType,
            LocalDateTime reportStart,
            LocalDateTime reportEnd) {
        try {
            String prompt = buildEnhancedPrompt(context, userNote, reportType, reportStart, reportEnd);
            String rawResponse = qwenService.getAnswer(prompt);
            return qwenService.extractContent(rawResponse);
        } catch (Exception e) {
            log.warn("增强分析 AI 调用失败，已降级返回结构化结果, userId={}: {}", context.getCurrentActivity().getUserId(), e.getMessage());
            return "AI 分析服务暂时不可用，已保存结构化指标数据，请稍后重试。";
        }
    }

    private String buildEnhancedPrompt(
            AiAnalysisContext context,
            String userNote,
            String reportType,
            LocalDateTime reportStart,
            LocalDateTime reportEnd) {
        Activity activity = context.getCurrentActivity();
        UserHistorySummary summary = context.getHistorySummary();
        RuleAnalysisResult rule = context.getRuleAnalysisResult();
        UserGoalProfile goal = context.getUserGoalProfile();
        GoalAnalysisResult goalAnalysis = context.getGoalAnalysisResult();

        // 用户文字备注部分
        String userNoteSection = (userNote != null && !userNote.isBlank())
                ? String.format("""
                【用户训练备注（请融入分析上下文）】
                用户描述："%s"
                请在分析中结合此主观感受，判断其与客观数据是否一致，并给出针对性建议。
                """, userNote)
                : "";

        // 报告类型专属指令
        String reportInstruction = switch (reportType) {
            case "WEEKLY" -> """
                    【报告类型：自然周报告】
                    本报告基于当前自然周（周一至周日）的训练数据。
                    重点分析本周整体训练质量、负荷分布、与上一自然周相比的变化趋势，以及下周优化方向。
                    """;
            case "MONTHLY" -> """
                    【报告类型：自然月报告】
                    本报告基于当前自然月（每月 1 日至月末）的训练数据。
                    重点分析本月训练量趋势、目标推进进度、体能发展轨迹，以及下月训练方向调整建议。
                    """;
            default -> """
                    【报告类型：每日训练小结】
                    聚焦本次训练质量评估、恢复状态和明日建议，简明扼要。
                    """;
        };

        String reportPeriodSection = "DAILY".equals(reportType)
                ? ""
                : String.format("""
                【统计周期】
                - 周期开始：%s
                - 周期结束：%s
                - 请严格围绕该自然周期内的训练数据进行分析
                """, formatPromptDate(reportStart), formatPromptDate(reportEnd));

        String periodTrainingSection = "DAILY".equals(reportType)
                ? ""
                : String.format("""
                【周期训练概览】
                - 本周期训练次数：%d 次
                - 本周期累计时长：%d 分钟
                - 本周期累计消耗：%d 千卡
                - 本周期主要训练类型：%s
                - 本周期训练明细：%s
                - 注意：这是一个周期报告，不要误写成“本周仅进行一次训练”或“本月只进行一次训练”，除非训练次数确实为 1
                """,
                context.getRecentActivities().size(),
                sumActivityDuration(context.getRecentActivities()),
                sumActivityCalories(context.getRecentActivities()),
                buildDominantTypesText(context.getRecentActivities()),
                buildActivityTimeline(context.getRecentActivities()));

        // 目标预测部分
        String predictionSection = "";
        if (goal != null && goalAnalysis != null && goal.getTargetDate() != null) {
            predictionSection = String.format("""
                    【目标预测要求】
                    目标类型：%s，目标状态：%s，目标截止日期：%s，当前完成度评分：%s/100。
                    请基于当前训练节奏，给出是否能按时完成目标的预判，以及关键风险点。
                    """,
                    defaultStr(goal.getGoalType(), "GENERAL_FITNESS"),
                    defaultStr(goal.getStatus(), "ACTIVE"),
                    goal.getTargetDate(), goalAnalysis.getCompletionScore());
        }

        return String.format("""
                你是一名专业的运动科学教练、训练计划分析师和恢复管理顾问。
                请基于以下综合数据输出个性化分析报告。
                你的重点不是复述数据，而是解释数据意味着什么，以及下一步应该怎么做。

                %s
                %s
                %s
                %s

                【本次训练数据】
                - 用户ID：%s | 活动ID：%s | 运动类型：%s
                - 持续时间：%d 分钟 | 消耗卡路里：%d kcal | 开始时间：%s

                【历史摘要（7天/30天）】
                - 训练次数：%d / %d | 总时长：%d / %d 分钟 | 总消耗：%d / %d kcal
                - 平均时长：%.1f / %.1f 分钟 | 连续天数：%d | 距上次：%d 天
                - 趋势：%s | 执行度：%s | 最常见类型：%s

                【规则判断】负荷：%s | 恢复：%s | 连续性：%s
                【风险】%s
                【亮点】%s

                【用户目标】%s（状态：%s），完成度：%s/100，推进状态：%s

                %s

                【写作要求】
                1. 必须优先判断训练模式是否与用户目标一致
                2. 结合历史判断是短期波动还是长期趋势
                3. FATIGUE_RISK 时明确提醒控制疲劳；DETRAINING_RISK 时先恢复节奏
                4. 建议必须具体、可执行，包含时长/强度/频率
                5. 如训练与目标不匹配，明确指出不匹配点
                6. 如有用户备注，优先结合主观感受给出针对性建议

                【输出格式（严格遵守）】
                训练评估：
                - ...

                问题分析：
                - ...

                优化建议：
                - ...

                下次训练计划：
                - ...

                【输出要求】使用中文，语言简洁专业，不输出 JSON，不输出无关解释
                """,
                reportInstruction,
                userNoteSection,
                reportPeriodSection,
                periodTrainingSection,
                activity.getUserId(),
                activity.getId(),
                defaultStr(activity.getType(), "UNKNOWN"),
                safeInt(activity.getDuration()),
                safeInt(activity.getCalorieBurned()),
                activity.getStartTime(),
                summary.getActivitiesLast7Days(), summary.getActivitiesLast30Days(),
                summary.getTotalDurationLast7Days(), summary.getTotalDurationLast30Days(),
                summary.getTotalCaloriesLast7Days(), summary.getTotalCaloriesLast30Days(),
                summary.getAvgDurationLast7Days(), summary.getAvgDurationLast30Days(),
                summary.getConsecutiveActiveDays(),
                summary.getInactiveDaysSinceLastActivity() == Integer.MAX_VALUE ? 999 : summary.getInactiveDaysSinceLastActivity(),
                defaultStr(summary.getTrend(), "UNKNOWN"),
                defaultStr(summary.getAdherenceLevel(), "UNKNOWN"),
                defaultStr(summary.getMostFrequentActivityType(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : defaultStr(rule.getTrainingLoadLevel(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : defaultStr(rule.getRecoveryStatus(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : defaultStr(rule.getConsistencyLevel(), "UNKNOWN"),
                rule == null ? "暂无" : String.join("；", rule.getRisks()),
                rule == null ? "暂无" : String.join("；", rule.getHighlights()),
                goal == null ? "未设置目标" : defaultStr(goal.getGoalType(), "GENERAL_FITNESS"),
                goal == null ? "无" : defaultStr(goal.getStatus(), "ACTIVE"),
                goalAnalysis == null ? "0" : String.valueOf(goalAnalysis.getCompletionScore()),
                goalAnalysis == null ? "UNKNOWN" : defaultStr(goalAnalysis.getProgressStatus(), "UNKNOWN"),
                predictionSection
        );
    }

    // ─────────────────────────── 辅助构建方法 ───────────────────────────

    private EnhancedAnalysisResponse.GoalAlignment buildGoalAlignment(GoalAnalysisResult goalAnalysis) {
        if (goalAnalysis == null) {
            return EnhancedAnalysisResponse.GoalAlignment.builder()
                    .goalType("NONE")
                    .alignmentLevel("UNKNOWN")
                    .completionScore(0)
                    .progressStatus("NO_GOAL")
                    .strengths(List.of())
                    .gaps(List.of())
                    .build();
        }
        return EnhancedAnalysisResponse.GoalAlignment.builder()
                .goalType(goalAnalysis.getGoalType())
                .alignmentLevel(goalAnalysis.getAlignmentLevel())
                .completionScore(goalAnalysis.getCompletionScore())
                .progressStatus(goalAnalysis.getProgressStatus())
                .strengths(goalAnalysis.getStrengths())
                .gaps(goalAnalysis.getGaps())
                .build();
    }

    private List<EnhancedAnalysisResponse.StructuredSuggestion> buildStructuredSuggestions(
            RuleAnalysisResult rule, GoalAnalysisResult goalAnalysis,
            EnhancedAnalysisResponse.RecoveryStatus recovery) {

        List<EnhancedAnalysisResponse.StructuredSuggestion> suggestions = new ArrayList<>();

        // 恢复建议（高优先级）
        if ("FATIGUE_RISK".equals(recovery.getStatus())) {
            suggestions.add(EnhancedAnalysisResponse.StructuredSuggestion.builder()
                    .category("RECOVERY").priority("HIGH")
                    .content("安排 1-2 天主动恢复：轻度拉伸、散步或瑜伽，避免高强度训练")
                    .actionable(true).timeframe("IMMEDIATE").build());
        } else if ("DETRAINING_RISK".equals(recovery.getStatus())) {
            suggestions.add(EnhancedAnalysisResponse.StructuredSuggestion.builder()
                    .category("TRAINING").priority("HIGH")
                    .content("先从 30-40 分钟低强度训练重新建立节奏，每周 3 次，连续执行 2 周")
                    .actionable(true).timeframe("THIS_WEEK").build());
        }

        // 规则引擎建议
        if (rule != null) {
            rule.getSuggestions().forEach(s -> suggestions.add(
                    EnhancedAnalysisResponse.StructuredSuggestion.builder()
                            .category("TRAINING").priority("MEDIUM")
                            .content(s).actionable(true).timeframe("THIS_WEEK").build()));
        }

        // 目标建议
        if (goalAnalysis != null) {
            goalAnalysis.getActionSuggestions().forEach(s -> suggestions.add(
                    EnhancedAnalysisResponse.StructuredSuggestion.builder()
                            .category("TRAINING").priority("MEDIUM")
                            .content(s).actionable(true).timeframe("THIS_WEEK").build()));
        }

        return suggestions;
    }

    private List<EnhancedAnalysisResponse.RiskAlert> buildRiskAlerts(
            RuleAnalysisResult rule, EnhancedAnalysisResponse.TrainingLoad load,
            GoalAnalysisResult goalAnalysis) {

        List<EnhancedAnalysisResponse.RiskAlert> alerts = new ArrayList<>();

        if (rule != null) {
            if ("FATIGUE_RISK".equals(rule.getRecoveryStatus())) {
                alerts.add(EnhancedAnalysisResponse.RiskAlert.builder()
                        .type("OVERTRAINING").severity("HIGH")
                        .description("近期训练负荷偏高，疲劳积累风险显著")
                        .mitigation("立即降低本周训练量30%，安排主动恢复").build());
            }
            if ("DETRAINING_RISK".equals(rule.getRecoveryStatus())) {
                alerts.add(EnhancedAnalysisResponse.RiskAlert.builder()
                        .type("DETRAINING").severity("MEDIUM")
                        .description("训练间隔过长，存在体能退化风险")
                        .mitigation("本周开始恢复规律训练，从低强度起步").build());
            }
            if ("LOW".equals(rule.getConsistencyLevel())) {
                alerts.add(EnhancedAnalysisResponse.RiskAlert.builder()
                        .type("GOAL_DRIFT").severity("LOW")
                        .description("训练连续性不足，可能导致目标偏离")
                        .mitigation("设定固定训练时间，每周至少完成 3 次训练").build());
            }
        }

        if ("HIGH".equals(load.getOvertrainingRisk())) {
            alerts.add(EnhancedAnalysisResponse.RiskAlert.builder()
                    .type("INJURY").severity("HIGH")
                    .description(String.format("急慢性负荷比（ACWR）为 %.2f，超过安全阈值 1.5", load.getAcuteChronicRatio()))
                    .mitigation("立即减少训练量，未来两周避免突然增加训练强度").build());
        }

        if (goalAnalysis != null && "SERIOUSLY_OFF_TRACK".equals(goalAnalysis.getProgressStatus())) {
            alerts.add(EnhancedAnalysisResponse.RiskAlert.builder()
                    .type("GOAL_DRIFT").severity("HIGH")
                    .description("目标完成度严重偏离预期轨道")
                    .mitigation("重新评估目标可行性，或调整目标截止日期和强度计划").build());
        }

        return alerts;
    }

    private EnhancedAnalysisResponse.WeeklyPlan buildWeeklyPlan(
            RuleAnalysisResult rule, EnhancedAnalysisResponse.RecoveryStatus recovery,
            UserGoalProfile goal) {

        int recommendedFrequency = 3;
        int recommendedDuration = 45;
        String intensityDirection = "MAINTAIN";
        List<String> focusAreas = new ArrayList<>();
        String notes = "";

        if ("FATIGUE_RISK".equals(recovery.getStatus())) {
            intensityDirection = "DECREASE";
            recommendedFrequency = 2;
            recommendedDuration = 30;
            focusAreas.add("主动恢复");
            focusAreas.add("低强度有氧");
            notes = "本周以恢复为主，下周视恢复情况逐步恢复强度";
        } else if ("DETRAINING_RISK".equals(recovery.getStatus())) {
            intensityDirection = "MAINTAIN";
            recommendedFrequency = 3;
            recommendedDuration = 35;
            focusAreas.add("节奏重建");
            focusAreas.add("基础有氧");
            notes = "先建立稳定训练节奏，强度从低到中渐进";
        } else if (rule != null && "HIGH".equals(rule.getConsistencyLevel())) {
            intensityDirection = "INCREASE";
            recommendedFrequency = 4;
            recommendedDuration = 50;
            focusAreas.add("专项能力提升");
            notes = "当前状态良好，可适度增加训练强度或增加专项训练";
        }

        // 根据目标调整
        if (goal != null) {
            if (goal.getWeeklyTargetFrequency() != null) {
                recommendedFrequency = Math.max(recommendedFrequency, goal.getWeeklyTargetFrequency());
            }
            if (goal.getWeeklyTargetDuration() != null && goal.getWeeklyTargetFrequency() != null
                    && goal.getWeeklyTargetFrequency() > 0) {
                recommendedDuration = Math.max(recommendedDuration,
                        goal.getWeeklyTargetDuration() / goal.getWeeklyTargetFrequency());
            }
        }

        return EnhancedAnalysisResponse.WeeklyPlan.builder()
                .recommendedFrequency(recommendedFrequency)
                .recommendedDurationPerSession(recommendedDuration)
                .intensityDirection(intensityDirection)
                .focusAreas(focusAreas)
                .notes(notes)
                .build();
    }

    private EnhancedAnalysisResponse.GoalProgressPrediction buildGoalPrediction(
            GoalAnalysisResult goalAnalysis, UserHistorySummary summary, UserGoalProfile goal) {

        if (goal == null || goalAnalysis == null) {
            return EnhancedAnalysisResponse.GoalProgressPrediction.builder()
                    .goalType("NONE")
                    .estimatedWeeksToGoal(null)
                    .confidence("LOW")
                    .currentPace("UNKNOWN")
                    .adjustmentNeeded("请先设置明确的训练目标")
                    .build();
        }

        int score = goalAnalysis.getCompletionScore();
        String currentPace;
        Integer estimatedWeeks = null;
        String confidence;
        String adjustmentNeeded;

        if (score >= 80) {
            currentPace = "AHEAD";
            confidence = "HIGH";
            // 估算：当前进度超前，预计可提前完成
            if (goal.getTargetDate() != null) {
                long daysToGoal = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), goal.getTargetDate());
                estimatedWeeks = (int) Math.max(1, Math.round(daysToGoal / 7.0 * 0.8));
            }
            adjustmentNeeded = "当前进度良好，可考虑适当提高目标值或提前目标日期";
        } else if (score >= 60) {
            currentPace = "ON_TRACK";
            confidence = "MEDIUM";
            if (goal.getTargetDate() != null) {
                long daysToGoal = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), goal.getTargetDate());
                estimatedWeeks = (int) Math.round(daysToGoal / 7.0);
            }
            adjustmentNeeded = "按当前节奏推进，注意保持训练一致性";
        } else if (score >= 40) {
            currentPace = "BEHIND";
            confidence = "MEDIUM";
            if (goal.getTargetDate() != null) {
                long daysToGoal = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), goal.getTargetDate());
                estimatedWeeks = (int) Math.round(daysToGoal / 7.0 * 1.3);
            }
            adjustmentNeeded = "需要提升训练频率或强度才能按时达成目标，建议每周增加 1-2 次训练";
        } else {
            currentPace = "STALLED";
            confidence = "LOW";
            adjustmentNeeded = "当前进度严重落后，建议重新评估目标或寻求专业教练指导";
        }

        return EnhancedAnalysisResponse.GoalProgressPrediction.builder()
                .goalType(goalAnalysis.getGoalType())
                .estimatedWeeksToGoal(estimatedWeeks)
                .confidence(confidence)
                .currentPace(currentPace)
                .adjustmentNeeded(adjustmentNeeded)
                .build();
    }

    private int calculateOverallScore(GoalAnalysisResult goalAnalysis,
                                      EnhancedAnalysisResponse.RecoveryStatus recovery,
                                      EnhancedAnalysisResponse.ConsistencyMetrics consistency,
                                      EnhancedAnalysisResponse.TrainingLoad load) {
        int score = 0;
        // 目标完成度 40%
        score += (goalAnalysis != null ? goalAnalysis.getCompletionScore() : 50) * 0.4;
        // 恢复评分 30%
        score += recovery.getRecoveryScore() * 0.3;
        // 连续性 20%
        int consistencyScore = switch (consistency.getLevel()) {
            case "HIGH" -> 100;
            case "MEDIUM" -> 65;
            default -> 30;
        };
        score += consistencyScore * 0.2;
        // 负荷合理性 10%（LOW=高分，HIGH=低分，MEDIUM=中分）
        int loadScore = switch (load.getLoadLevel()) {
            case "LOW" -> 80;
            case "MEDIUM" -> 70;
            default -> 50;
        };
        if ("HIGH".equals(load.getOvertrainingRisk())) loadScore -= 30;
        score += Math.max(0, loadScore) * 0.1;

        return Math.max(0, Math.min(100, (int) score));
    }

    private String mapOverallStatus(int score) {
        if (score >= 80) return "EXCELLENT";
        if (score >= 65) return "GOOD";
        if (score >= 45) return "FAIR";
        return "NEEDS_IMPROVEMENT";
    }

    private String extractUserNoteInsight(String narrativeAnalysis, String userNote) {
        if (userNote == null || userNote.isBlank()) return null;
        // 从 AI 分析文本中找到与用户备注相关的洞察（简单截取策略）
        // 实际生产中可通过二次 AI 调用精确提取
        return "已融入分析：「" + userNote + "」";
    }

    private UserGoalProfile fetchGoalSafely(String userId) {
        try {
            return userGoalClient.getCurrentGoal(userId);
        } catch (Exception e) {
            log.warn("获取用户目标失败，跳过目标分析, userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultStr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void archiveEnhancedAnalysis(EnhancedAnalysisResponse response, AiAnalysisContext context) {
        try {
            Recommendation recommendation = Recommendation.builder()
                    .recommendationType("ENHANCED")
                    .activityId(response.getActivityId())
                    .userId(response.getUserId())
                    .activityType(context.getCurrentActivity() == null ? null : context.getCurrentActivity().getType())
                    .reportType(response.getReportType())
                    .periodStart(response.getPeriodStart())
                    .periodEnd(response.getPeriodEnd())
                    .overallScore(response.getOverallScore())
                    .overallStatus(response.getOverallStatus())
                    .recommendation(response.getNarrativeAnalysis())
                    .improvements(extractSection(response.getNarrativeAnalysis(), "问题分析：", "优化建议："))
                    .suggestions(response.getSuggestions() == null
                            ? List.of()
                            : response.getSuggestions().stream()
                            .map(EnhancedAnalysisResponse.StructuredSuggestion::getContent)
                            .filter(Objects::nonNull)
                            .toList())
                    .historySummary(context.getHistorySummary())
                    .ruleAnalysisResult(context.getRuleAnalysisResult())
                    .userGoalProfile(context.getUserGoalProfile())
                    .goalAnalysisResult(context.getGoalAnalysisResult())
                    .createdAt(response.getGeneratedAt() == null ? LocalDateTime.now() : response.getGeneratedAt())
                    .build();

            recommendationRepository.save(recommendation);
        } catch (Exception e) {
            log.warn("保存增强分析历史失败，userId={}, requestId={}", response.getUserId(), response.getRequestId(), e);
        }
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
                    .map(line -> line.startsWith("-") ? line.substring(1).trim() : line)
                    .toList();
        } catch (Exception e) {
            log.warn("提取增强分析段落失败: startMarker={}", startMarker, e);
            return List.of();
        }
    }

    private int sumActivityDuration(List<Activity> activities) {
        return activities.stream()
                .map(Activity::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int sumActivityCalories(List<Activity> activities) {
        return activities.stream()
                .map(Activity::getCalorieBurned)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String buildDominantTypesText(List<Activity> activities) {
        Map<String, Long> distribution = activities.stream()
                .map(Activity::getType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));

        if (distribution.isEmpty()) {
            return "暂无训练类型数据";
        }

        return distribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(entry -> entry.getKey() + " × " + entry.getValue())
                .collect(Collectors.joining("，"));
    }

    private String buildActivityTimeline(List<Activity> activities) {
        if (activities.isEmpty()) {
            return "本周期暂无训练记录";
        }

        return activities.stream()
                .sorted(Comparator.comparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(activity -> String.format("%s %s %d分钟",
                        formatPromptDate(activity.getStartTime()),
                        defaultStr(activity.getType(), "UNKNOWN"),
                        safeInt(activity.getDuration())))
                .collect(Collectors.joining("；"));
    }

    private String formatPromptDate(LocalDateTime dateTime) {
        return dateTime == null ? "未设置" : dateTime.toLocalDate().toString();
    }

    private record ReportWindow(LocalDateTime start, LocalDateTime end) {
    }
}
