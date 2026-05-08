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
    private final TrainingAnalysisAgent trainingAnalysisAgent;

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

        TrainingAnalysisAgentResult agentResult = null;
        String narrativeAnalysis;
        try {
            agentResult = trainingAnalysisAgent.analyze(context, request.getUserNote(), reportType, reportStart, reportEnd);
            narrativeAnalysis = agentResult.getMarkdownReport();
        } catch (Exception e) {
            log.warn("Multi-provider training analysis agent failed, falling back to legacy enhanced prompt, userId={}, reason={}",
                    userId, e.getMessage());
            narrativeAnalysis = callAiWithEnhancedPrompt(
                    context, request.getUserNote(), reportType, reportStart, reportEnd);
        }

        // 5. 解析 AI 输出中的文本备注洞察
        String userNoteInsight = extractUserNoteInsight(narrativeAnalysis, request.getUserNote());

        // 6. 构建结构化建议和风险列表
        List<EnhancedAnalysisResponse.StructuredSuggestion> structuredSuggestions =
                agentResult != null && agentResult.getSuggestions() != null && !agentResult.getSuggestions().isEmpty()
                        ? agentResult.getSuggestions()
                        : buildStructuredSuggestions(ruleResult, goalAnalysis, recovery);
        List<EnhancedAnalysisResponse.RiskAlert> riskAlerts =
                agentResult != null && agentResult.getRisks() != null && !agentResult.getRisks().isEmpty()
                        ? agentResult.getRisks()
                        : buildRiskAlerts(ruleResult, load, goalAnalysis);

        // 7. 构建下周训练计划和目标预测
        EnhancedAnalysisResponse.WeeklyPlan weeklyPlan =
                agentResult != null && agentResult.getNextTrainingPlan() != null
                        ? agentResult.getNextTrainingPlan()
                        : buildWeeklyPlan(ruleResult, recovery, userGoalProfile);
        EnhancedAnalysisResponse.GoalProgressPrediction prediction = buildGoalPrediction(goalAnalysis, summary, userGoalProfile);

        // 8. 计算综合评分
        int overallScore = agentResult != null && agentResult.getScore() != null
                ? agentResult.getScore()
                : calculateOverallScore(goalAnalysis, recovery, consistency, load);
        String overallStatus = agentResult != null && agentResult.getLevel() != null
                ? mapAgentStatus(agentResult.getLevel(), overallScore)
                : mapOverallStatus(overallScore);

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
                .markdownReport(agentResult == null ? narrativeAnalysis : agentResult.getMarkdownReport())
                .structuredAnalysisJson(agentResult == null ? null : agentResult.getStructuredAnalysisJson())
                .providerTrace(agentResult == null
                        ? "已使用备用模型完成分析"
                        : agentResult.getProviderTrace())
                .fallbackUsed(agentResult == null || agentResult.isFallbackUsed())
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
            prompt = buildStrictJsonPromptPrefix(context, reportType, reportStart, reportEnd) + "\n\n" + prompt;
            String rawResponse = qwenService.getAnswer(prompt);
            return qwenService.extractContent(rawResponse);
        } catch (Exception e) {
            log.warn("增强分析 AI 调用失败，已使用本地智能规则生成报告, userId={}: {}",
                    context.getCurrentActivity().getUserId(), e.getMessage());
            return buildLocalEnhancedNarrative(context, userNote, reportType, e.getMessage());
        }
    }

    private String buildStrictJsonPromptPrefix(
            AiAnalysisContext context,
            String reportType,
            LocalDateTime reportStart,
            LocalDateTime reportEnd) {
        Activity activity = context.getCurrentActivity();
        UserHistorySummary summary = context.getHistorySummary();
        RuleAnalysisResult rule = context.getRuleAnalysisResult();
        GoalAnalysisResult goal = context.getGoalAnalysisResult();

        return """
                你必须只输出一个合法 JSON 对象，不要输出 Markdown 代码围栏，不要输出额外解释。
                所有字段必须使用中文内容；枚举字段按指定英文值输出。
                如果信息不足，也要基于已给训练数据输出可执行建议，不要写空泛建议。
                suggestions 必须去重：含义相近、重复提到“安排 1-2 天恢复”的建议只保留最具体的一条。
                每个 category 最多输出 2 条建议，总建议数控制在 5-8 条。
                建议必须引用具体训练数据，例如近 7 天训练次数、训练时长、热量、恢复状态、目标完成度、负荷水平。

                输出 JSON 结构必须严格如下：
                {
                  "summary": "本次训练状态总结",
                  "status": {
                    "score": 49,
                    "level": "一般",
                    "reason": "训练负荷偏高，恢复不足"
                  },
                  "risks": [
                    {
                      "title": "过度训练风险",
                      "level": "high",
                      "reason": "本周训练负荷上升，但恢复评分偏低",
                      "action": "未来 1-2 天降低训练强度"
                    }
                  ],
                  "suggestions": [
                    {
                      "category": "recovery",
                      "title": "安排主动恢复",
                      "reason": "恢复评分偏低，继续高强度训练会增加疲劳积累",
                      "action": "安排 1-2 天轻松跑、拉伸或完全休息",
                      "priority": "high",
                      "timeWindow": "未来 48 小时"
                    }
                  ],
                  "markdownReport": "## 训练状态总结\\n\\n本周训练状态一般...\\n\\n### 建议\\n\\n- 安排主动恢复\\n- 降低下一次训练强度"
                }

                category 只能使用：recovery、training、goal、risk、data。
                priority 只能使用：high、medium、low。
                risks.level 只能使用：high、medium、low。
                markdownReport 必须是完整 Markdown，至少包含：训练状态、风险预警、恢复状态、目标进度、训练趋势、个性化建议。

                当前关键数据：
                - 报告类型：%s，周期：%s 至 %s
                - 近 7 天训练：%d 次，%d 分钟，%d 千卡
                - 近 30 天训练：%d 次，%d 分钟，%d 千卡
                - 训练负荷：%s；恢复状态：%s；连续性：%s
                - 当前活动：%s，%d 分钟，%d 千卡
                - 目标完成度：%s/100；目标状态：%s
                """.formatted(
                reportType,
                reportStart,
                reportEnd,
                summary.getActivitiesLast7Days(),
                summary.getTotalDurationLast7Days(),
                summary.getTotalCaloriesLast7Days(),
                summary.getActivitiesLast30Days(),
                summary.getTotalDurationLast30Days(),
                summary.getTotalCaloriesLast30Days(),
                rule == null ? "UNKNOWN" : defaultStr(rule.getTrainingLoadLevel(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : defaultStr(rule.getRecoveryStatus(), "UNKNOWN"),
                rule == null ? "UNKNOWN" : defaultStr(rule.getConsistencyLevel(), "UNKNOWN"),
                activity == null ? "UNKNOWN" : defaultStr(activity.getType(), "UNKNOWN"),
                activity == null ? 0 : safeInt(activity.getDuration()),
                activity == null ? 0 : safeInt(activity.getCalorieBurned()),
                goal == null ? "0" : String.valueOf(goal.getCompletionScore()),
                goal == null ? "NO_GOAL" : defaultStr(goal.getProgressStatus(), "UNKNOWN")
        );
    }

    private String buildLocalEnhancedNarrative(
            AiAnalysisContext context,
            String userNote,
            String reportType,
            String fallbackReason) {
        Activity activity = context.getCurrentActivity();
        UserHistorySummary summary = context.getHistorySummary();
        RuleAnalysisResult rule = context.getRuleAnalysisResult();
        GoalAnalysisResult goalAnalysis = context.getGoalAnalysisResult();
        UserGoalProfile goal = context.getUserGoalProfile();

        String recovery = rule == null ? "UNKNOWN" : defaultStr(rule.getRecoveryStatus(), "UNKNOWN");
        String load = rule == null ? "UNKNOWN" : defaultStr(rule.getTrainingLoadLevel(), "UNKNOWN");
        String consistency = rule == null ? "UNKNOWN" : defaultStr(rule.getConsistencyLevel(), "UNKNOWN");
        String profile = determineTrainingProfile(summary, rule);
        String priority = determineNextPriority(recovery, consistency, goalAnalysis);
        String noteInsight = buildUserNoteLocalInsight(userNote, recovery, load);
        String goalDecision = buildGoalDecision(goal, goalAnalysis);

        return String.format("""
                训练评估：
                - 当前训练画像：%s。系统结合最近 7 天 %d 次、最近 30 天 %d 次训练判断，当前负荷为 %s，恢复状态为 %s，连续性为 %s。
                - 本次/本周期主要训练为 %s，累计时长 %d 分钟，消耗约 %d kcal。它对当前目标的价值取决于是否能持续形成稳定频率，而不是单次表现。
                - 目标判断：%s
                - 备注判断：%s
                - 在线大模型暂不可用，本报告由本地规则引擎、目标分析和历史趋势生成；原因：%s

                问题分析：
                %s
                - 当前最优先处理的问题是：%s。
                - 如果未来 7 天训练频率仍低于目标，系统会继续判断为目标推进不足；如果连续加量过快，则会转为疲劳风险优先。

                优化建议：
                %s
                - 下周只保留一个主目标：%s。不要同时追求频率、强度和训练量全部提升。
                - 每次训练后补充记录主观疲劳、疼痛、心率或配速，后续分析会优先使用这些真实指标，而不是只按时长判断。

                下次训练计划：
                - 训练类型：%s。
                - 建议时长：%d-%d 分钟。
                - 强度方向：%s。
                - 执行标准：训练后仍能正常恢复，第二天没有明显疼痛或异常疲劳；否则下一次继续下调强度。
                """,
                profile,
                summary.getActivitiesLast7Days(),
                summary.getActivitiesLast30Days(),
                load,
                recovery,
                consistency,
                defaultStr(activity.getType(), "UNKNOWN"),
                safeInt(activity.getDuration()),
                safeInt(activity.getCalorieBurned()),
                goalDecision,
                noteInsight,
                defaultStr(fallbackReason, "AI 服务配置不可用"),
                joinLines(rule == null ? null : rule.getRisks(), "- 暂无明确高风险，但仍需观察训练后的恢复反馈"),
                priority,
                joinLines(rule == null ? null : rule.getSuggestions(), "- 先保持稳定训练频率，再逐步增加专项训练"),
                priority,
                chooseNextSessionType(goal, activity),
                recommendedMinDuration(recovery, activity),
                recommendedMaxDuration(recovery, activity),
                recommendedIntensity(recovery, load, consistency)
        );
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
                【目标智能判断】可行性：%s/100，预计达成：%s 周，下一里程碑：%s
                【目标调整建议】%s，原因：%s

                %s

                【写作要求】
                1. 必须优先判断训练模式是否与用户目标一致
                2. 结合历史判断是短期波动还是长期趋势
                3. FATIGUE_RISK 时明确提醒控制疲劳；DETRAINING_RISK 时先恢复节奏
                4. 建议必须具体、可执行，包含时长/强度/频率
                5. 如训练与目标不匹配，明确指出不匹配点
                6. 如有用户备注，优先结合主观感受给出针对性建议
                7. 必须给出“训练画像”：例如节奏重建型、稳定推进型、负荷偏高型、目标偏离型
                8. 必须说明判断证据，至少引用 2 个具体指标，例如 7天次数、30天次数、总时长、恢复状态、目标完成度
                9. 必须给出一个优先级最高的问题，不要平均罗列所有问题
                10. 必须判断目标是否保持、下调、延期或上调，并解释原因
                11. 建议要像真人教练，不要出现“建议合理安排训练”这种空泛表达

                【输出格式（严格遵守）】
                训练评估：
                - 训练画像：...
                - 关键证据：...
                - 目标决策：...

                问题分析：
                - 优先问题：...
                - 形成原因：...

                优化建议：
                - 本周主策略：...
                - 具体执行：...

                下次训练计划：
                - 类型：...
                - 时长：...
                - 强度：...
                - 完成标准：...

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
                goalAnalysis == null || goalAnalysis.getFeasibilityScore() == null ? "0" : String.valueOf(goalAnalysis.getFeasibilityScore()),
                goalAnalysis == null || goalAnalysis.getEstimatedWeeksToGoal() == null ? "未知" : String.valueOf(goalAnalysis.getEstimatedWeeksToGoal()),
                goalAnalysis == null ? "未设置" : defaultStr(goalAnalysis.getNextMilestone(), "未设置"),
                goalAnalysis == null ? "未设置" : defaultStr(goalAnalysis.getRecommendedGoalRevision(), "未设置"),
                goalAnalysis == null ? "未设置" : defaultStr(goalAnalysis.getAdjustmentReason(), "未设置"),
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

        if ("FATIGUE_RISK".equals(recovery.getStatus())) {
            suggestions.add(buildSuggestion(
                    "recovery",
                    "high",
                    "安排主动恢复窗口",
                    String.format("恢复状态为 %s，疲劳累计风险较高。", recovery.getStatus()),
                    "未来 48 小时安排轻松跑、拉伸或完全休息，暂停高强度训练。",
                    "未来 48 小时"
            ));
        } else if ("DETRAINING_RISK".equals(recovery.getStatus())) {
            suggestions.add(buildSuggestion(
                    "training",
                    "high",
                    "重建稳定训练节奏",
                    "近期训练间隔偏长，存在训练适应下降风险。",
                    "本周完成 3 次 30-40 分钟低强度训练，连续执行 2 周后再增加强度。",
                    "本周"
            ));
        }

        if (rule != null && rule.getSuggestions() != null) {
            for (String suggestion : rule.getSuggestions()) {
                if (suggestion == null || suggestion.isBlank()) {
                    continue;
                }
                String category = suggestion.contains("恢复") || suggestion.contains("休息") ? "recovery" : "training";
                suggestions.add(buildSuggestion(
                        category,
                        "medium",
                        category.equals("recovery") ? "调整恢复安排" : "优化训练安排",
                        String.format("规则分析显示训练负荷为 %s，连续性为 %s，恢复状态为 %s。",
                                defaultStr(rule.getTrainingLoadLevel(), "UNKNOWN"),
                                defaultStr(rule.getConsistencyLevel(), "UNKNOWN"),
                                defaultStr(rule.getRecoveryStatus(), "UNKNOWN")),
                        suggestion,
                        "本周"
                ));
            }
        }

        if (rule != null && "LOW".equals(rule.getConsistencyLevel())) {
            suggestions.add(buildSuggestion(
                    "risk",
                    "medium",
                    "降低目标偏离风险",
                    "训练连续性偏低，目标推进容易出现断档。",
                    "固定每周训练时段，优先保证至少 3 次可完成训练。",
                    "未来 7 天"
            ));
        }

        if (goalAnalysis != null) {
            Integer score = goalAnalysis.getCompletionScore();
            String progressStatus = defaultStr(goalAnalysis.getProgressStatus(), "UNKNOWN");

            if (score != null && score < 60) {
                suggestions.add(buildSuggestion(
                        "goal",
                        score < 40 ? "high" : "medium",
                        "修正目标推进节奏",
                        String.format("目标完成评分为 %d，进度状态为 %s。", score, progressStatus),
                        defaultStr(goalAnalysis.getRecommendedGoalRevision(),
                                "把下一阶段目标拆成每周训练频率、训练时长和负荷上限三个可执行指标。"),
                        "未来 7 天"
                ));
            }

            if (goalAnalysis.getActionSuggestions() != null) {
                for (String suggestion : goalAnalysis.getActionSuggestions()) {
                    if (suggestion == null || suggestion.isBlank()) {
                        continue;
                    }
                    suggestions.add(buildSuggestion(
                            "goal",
                            "medium",
                            "推进目标执行",
                            String.format("目标分析显示进度状态为 %s，完成评分为 %s。",
                                    progressStatus, score == null ? "UNKNOWN" : score.toString()),
                            suggestion,
                            "本周"
                    ));
                }
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add(buildSuggestion(
                    "data",
                    "low",
                    "补充训练记录",
                    "当前可用于分析的训练数据不足，建议质量会受限。",
                    "补充最近 7-30 天的训练频率、训练时长、热量消耗和主观恢复记录。",
                    "下次训练后"
            ));
        }

        return dedupeSuggestions(suggestions);
    }

    private EnhancedAnalysisResponse.StructuredSuggestion buildSuggestion(
            String category,
            String priority,
            String title,
            String reason,
            String action,
            String timeWindow) {

        String normalizedPriority = defaultStr(priority, "medium").toLowerCase(Locale.ROOT);
        String content = String.format("%s：%s", title, action);
        String timeframe = "high".equals(normalizedPriority) ? "IMMEDIATE" : "THIS_WEEK";

        return EnhancedAnalysisResponse.StructuredSuggestion.builder()
                .category(category)
                .priority(normalizedPriority)
                .title(title)
                .reason(reason)
                .action(action)
                .timeWindow(timeWindow)
                .content(content)
                .actionable(true)
                .timeframe(timeframe)
                .build();
    }

    private List<EnhancedAnalysisResponse.StructuredSuggestion> dedupeSuggestions(
            List<EnhancedAnalysisResponse.StructuredSuggestion> suggestions) {

        Map<String, List<EnhancedAnalysisResponse.StructuredSuggestion>> byCategory = new LinkedHashMap<>();
        for (EnhancedAnalysisResponse.StructuredSuggestion suggestion : suggestions) {
            if (suggestion == null || suggestion.getAction() == null || suggestion.getAction().isBlank()) {
                continue;
            }

            String category = defaultStr(suggestion.getCategory(), "data").toLowerCase(Locale.ROOT);
            List<EnhancedAnalysisResponse.StructuredSuggestion> existing =
                    byCategory.computeIfAbsent(category, key -> new ArrayList<>());

            boolean duplicate = existing.stream().anyMatch(item -> isSimilarSuggestion(item, suggestion));
            if (!duplicate) {
                existing.add(suggestion);
            }
        }

        return byCategory.values().stream()
                .flatMap(items -> items.stream()
                        .sorted(Comparator.comparingInt(item -> priorityRank(item.getPriority())))
                        .limit(2))
                .collect(Collectors.toList());
    }

    private boolean isSimilarSuggestion(
            EnhancedAnalysisResponse.StructuredSuggestion left,
            EnhancedAnalysisResponse.StructuredSuggestion right) {

        String leftText = normalizeSuggestionText(left.getTitle() + " " + left.getAction());
        String rightText = normalizeSuggestionText(right.getTitle() + " " + right.getAction());
        if (leftText.equals(rightText)) {
            return true;
        }

        boolean bothRecoveryWindow = leftText.contains("12") && rightText.contains("12")
                && leftText.contains("恢复") && rightText.contains("恢复");
        if (bothRecoveryWindow) {
            return true;
        }

        Set<String> leftTokens = Arrays.stream(leftText.split("\\s+"))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
        Set<String> rightTokens = Arrays.stream(rightText.split("\\s+"))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }

        long overlap = leftTokens.stream().filter(rightTokens::contains).count();
        double similarity = overlap / (double) Math.min(leftTokens.size(), rightTokens.size());
        return similarity >= 0.72;
    }

    private String normalizeSuggestionText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("1-2", "12")
                .replace("1 到 2", "12")
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .trim();
    }

    private int priorityRank(String priority) {
        if ("high".equalsIgnoreCase(priority) || "HIGH".equals(priority)) {
            return 0;
        }
        if ("medium".equalsIgnoreCase(priority) || "MEDIUM".equals(priority)) {
            return 1;
        }
        return 2;
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

        if (goalAnalysis.getRecommendedGoalRevision() != null && !goalAnalysis.getRecommendedGoalRevision().isBlank()) {
            adjustmentNeeded = goalAnalysis.getRecommendedGoalRevision();
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

    private String mapAgentStatus(String level, int score) {
        if ("优秀".equals(level)) return "EXCELLENT";
        if ("良好".equals(level)) return "GOOD";
        if ("一般".equals(level)) return "FAIR";
        if ("风险".equals(level)) return "NEEDS_IMPROVEMENT";
        return mapOverallStatus(score);
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

    private String joinLines(List<String> lines, String defaultValue) {
        if (lines == null || lines.isEmpty()) {
            return defaultValue;
        }
        return lines.stream()
                .filter(Objects::nonNull)
                .filter(line -> !line.isBlank())
                .map(line -> line.startsWith("-") ? line : "- " + line)
                .collect(Collectors.joining("\n"));
    }

    private String determineTrainingProfile(UserHistorySummary summary, RuleAnalysisResult rule) {
        String recovery = rule == null ? "UNKNOWN" : defaultStr(rule.getRecoveryStatus(), "UNKNOWN");
        String consistency = rule == null ? "UNKNOWN" : defaultStr(rule.getConsistencyLevel(), "UNKNOWN");
        String load = rule == null ? "UNKNOWN" : defaultStr(rule.getTrainingLoadLevel(), "UNKNOWN");

        if ("FATIGUE_RISK".equals(recovery) || "HIGH".equals(load)) {
            return "负荷偏高型，需要先控制疲劳再继续加量";
        }
        if ("DETRAINING_RISK".equals(recovery) || "LOW".equals(consistency)) {
            return "节奏重建型，当前首要任务是恢复规律训练";
        }
        if (summary.getActivitiesLast7Days() >= 3 && "BALANCED".equals(recovery)) {
            return "稳定推进型，可以在保持恢复的前提下小幅提升专项训练";
        }
        return "基础积累型，适合先扩大训练容量并观察恢复反馈";
    }

    private String determineNextPriority(String recovery, String consistency, GoalAnalysisResult goalAnalysis) {
        if ("FATIGUE_RISK".equals(recovery)) {
            return "恢复管理";
        }
        if ("LOW".equals(consistency) || "DETRAINING_RISK".equals(recovery)) {
            return "训练连续性";
        }
        if (goalAnalysis != null && ("OFF_TRACK".equals(goalAnalysis.getProgressStatus())
                || "SERIOUSLY_OFF_TRACK".equals(goalAnalysis.getProgressStatus()))) {
            return "目标对齐";
        }
        return "专项能力提升";
    }

    private String buildUserNoteLocalInsight(String userNote, String recovery, String load) {
        if (userNote == null || userNote.isBlank()) {
            return "未填写主观备注，本次主要依据客观训练记录判断";
        }
        if (userNote.contains("痛") || userNote.contains("不适") || userNote.contains("累")) {
            return "用户备注提示存在疲劳或不适，应优先降低强度并观察恢复";
        }
        if ("FATIGUE_RISK".equals(recovery) || "HIGH".equals(load)) {
            return "用户备注已记录，但客观负荷偏高，建议以恢复反馈为主要决策依据";
        }
        return "用户备注已纳入判断，可作为调整训练强度和恢复安排的补充依据";
    }

    private String buildGoalDecision(UserGoalProfile goal, GoalAnalysisResult goalAnalysis) {
        if (goal == null || goalAnalysis == null) {
            return "当前未设置有效目标，建议先建立 8-12 周阶段目标，再进行目标驱动分析";
        }
        int score = goalAnalysis.getCompletionScore() == null ? 0 : goalAnalysis.getCompletionScore();
        if (score >= 80) {
            return "目标可以保持，若未来 2 周仍稳定，可小幅上调目标 5%-10%";
        }
        if (score >= 60) {
            return "目标暂时保持，但需要补齐频率或总时长短板";
        }
        if (score >= 40) {
            return "目标建议延期 1-2 周，先提高执行稳定性";
        }
        return "目标建议下调或重设，当前训练节奏不足以支撑原目标";
    }

    private String chooseNextSessionType(UserGoalProfile goal, Activity activity) {
        String goalType = goal == null ? "" : defaultStr(goal.getGoalType(), "");
        return switch (goalType) {
            case "TEN_K_IMPROVEMENT", "ENDURANCE" -> "低强度有氧或轻松跑";
            case "MUSCLE_GAIN" -> "全身力量训练，优先基础动作";
            case "FAT_LOSS", "WEIGHT_LOSS" -> "中低强度有氧加短时力量训练";
            case "RECOVERY" -> "主动恢复、拉伸或轻松步行";
            default -> defaultStr(activity.getType(), "综合体能训练");
        };
    }

    private int recommendedMinDuration(String recovery, Activity activity) {
        int base = Math.max(20, safeInt(activity.getDuration()) - 10);
        if ("FATIGUE_RISK".equals(recovery)) {
            return 20;
        }
        if ("DETRAINING_RISK".equals(recovery)) {
            return 25;
        }
        return base;
    }

    private int recommendedMaxDuration(String recovery, Activity activity) {
        int base = Math.max(30, safeInt(activity.getDuration()) + 10);
        if ("FATIGUE_RISK".equals(recovery)) {
            return 35;
        }
        if ("DETRAINING_RISK".equals(recovery)) {
            return 40;
        }
        return base;
    }

    private String recommendedIntensity(String recovery, String load, String consistency) {
        if ("FATIGUE_RISK".equals(recovery) || "HIGH".equals(load)) {
            return "低强度，主观用力控制在 10 分制 3-4 分";
        }
        if ("LOW".equals(consistency) || "DETRAINING_RISK".equals(recovery)) {
            return "中低强度，先完成训练不要追求极限";
        }
        return "中等强度，可加入少量专项刺激，但不要连续高强度";
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
