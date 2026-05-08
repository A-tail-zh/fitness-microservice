package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.dto.AiAnalysisContext;
import com.fitness.aiservice.dto.EnhancedAnalysisResponse;
import com.fitness.aiservice.dto.GoalAnalysisResult;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.TrainingAnalysisAgentResult;
import com.fitness.aiservice.dto.UserGoalProfile;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingAnalysisAgent {

    private final DeepSeekAiProvider deepSeekAiProvider;
    private final QwenAiProvider qwenAiProvider;
    private final DeepSeekAnalysisPromptBuilder deepSeekPromptBuilder;
    private final QwenReportPromptBuilder qwenReportPromptBuilder;
    private final SuggestionDeduplicator suggestionDeduplicator;
    private final ObjectMapper objectMapper;

    public TrainingAnalysisAgentResult analyze(
            AiAnalysisContext context,
            String userNote,
            String reportType,
            LocalDateTime reportStart,
            LocalDateTime reportEnd) {
        String trainingContextJson = buildTrainingContextJson(context, userNote, reportType, reportStart, reportEnd);
        String deepSeekJson = null;
        String markdownReport = null;
        boolean fallbackUsed = false;

        try {
            deepSeekJson = deepSeekAiProvider.chat(
                    deepSeekPromptBuilder.systemPrompt(),
                    deepSeekPromptBuilder.userPrompt(trainingContextJson));
        } catch (Exception ex) {
            fallbackUsed = true;
            log.warn("DeepSeek structured analysis failed, falling back to Qwen-only flow, userId={}, reason={}",
                    context.getCurrentActivity().getUserId(), ex.getMessage());
            deepSeekJson = buildRuleStructuredJson(context, userNote);
        }

        try {
            markdownReport = qwenAiProvider.chat(
                    qwenReportPromptBuilder.systemPrompt(),
                    qwenReportPromptBuilder.userPrompt(trainingContextJson, deepSeekJson, userNote));
        } catch (Exception ex) {
            fallbackUsed = true;
            log.warn("Qwen report generation failed, building markdown from structured result, userId={}, reason={}",
                    context.getCurrentActivity().getUserId(), ex.getMessage());
            markdownReport = buildMarkdownFromStructuredJson(deepSeekJson, context);
        }

        return mapResult(deepSeekJson, markdownReport, fallbackUsed);
    }

    private TrainingAnalysisAgentResult mapResult(String deepSeekJson, String markdownReport, boolean fallbackUsed) {
        try {
            JsonNode root = objectMapper.readTree(deepSeekJson);
            List<EnhancedAnalysisResponse.StructuredSuggestion> suggestions =
                    suggestionDeduplicator.deduplicate(parseSuggestions(root.path("suggestions")));

            return TrainingAnalysisAgentResult.builder()
                    .summary(root.path("overall").path("summary").asText("训练分析报告已生成。"))
                    .score(root.path("overall").path("score").asInt(60))
                    .level(root.path("overall").path("level").asText("一般"))
                    .risks(parseRisks(root.path("risks")))
                    .suggestions(suggestions)
                    .nextTrainingPlan(parseNextTraining(root.path("nextTraining")))
                    .markdownReport(markdownReport)
                    .structuredAnalysisJson(deepSeekJson)
                    .providerTrace(fallbackUsed
                            ? "DeepSeek/Qwen 协作链路已启用，部分环节使用备用模型或规则引擎完成。"
                            : "DeepSeek 完成结构化分析，Qwen 完成报告生成。")
                    .fallbackUsed(fallbackUsed)
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to map agent result, using local fallback, reason={}", ex.getMessage());
            return TrainingAnalysisAgentResult.builder()
                    .summary("系统已基于规则引擎生成训练分析。")
                    .score(60)
                    .level("一般")
                    .risks(List.of())
                    .suggestions(List.of())
                    .markdownReport(StringUtils.hasText(markdownReport) ? markdownReport : "# 律动训练分析报告\n\n系统已完成基础训练分析。")
                    .structuredAnalysisJson(deepSeekJson)
                    .providerTrace("AI 输出解析失败，已使用后端兜底报告。")
                    .fallbackUsed(true)
                    .build();
        }
    }

    private List<EnhancedAnalysisResponse.RiskAlert> parseRisks(JsonNode risksNode) {
        if (!risksNode.isArray()) {
            return List.of();
        }
        return iterable(risksNode).stream()
                .map(node -> EnhancedAnalysisResponse.RiskAlert.builder()
                        .type(node.path("title").asText("训练风险"))
                        .severity(node.path("level").asText("medium").toUpperCase())
                        .description(node.path("evidence").asText("") + " " + node.path("impact").asText(""))
                        .mitigation(node.path("action").asText(""))
                        .build())
                .toList();
    }

    private List<EnhancedAnalysisResponse.StructuredSuggestion> parseSuggestions(JsonNode suggestionsNode) {
        if (!suggestionsNode.isArray()) {
            return List.of();
        }
        return iterable(suggestionsNode).stream()
                .map(node -> EnhancedAnalysisResponse.StructuredSuggestion.builder()
                        .category(node.path("category").asText("training_adjustment"))
                        .priority(node.path("priority").asText("medium"))
                        .title(node.path("title").asText("训练建议"))
                        .reason(node.path("reason").asText(""))
                        .action(node.path("action").asText(""))
                        .timeWindow(node.path("timeWindow").asText(""))
                        .timeframe(node.path("timeWindow").asText(""))
                        .content(buildSuggestionContent(node))
                        .actionable(true)
                        .build())
                .toList();
    }

    private EnhancedAnalysisResponse.WeeklyPlan parseNextTraining(JsonNode nextTrainingNode) {
        return EnhancedAnalysisResponse.WeeklyPlan.builder()
                .recommendedFrequency(1)
                .recommendedDurationPerSession(nextTrainingNode.path("durationMinutes").asInt(40))
                .intensityDirection(nextTrainingNode.path("intensity").asText("中"))
                .focusAreas(List.of(nextTrainingNode.path("recommendedType").asText("综合训练")))
                .notes(nextTrainingNode.path("notes").asText(""))
                .build();
    }

    private String buildSuggestionContent(JsonNode node) {
        return "建议：" + node.path("title").asText("") +
                "；原因：" + node.path("reason").asText("") +
                "；依据：" + node.path("evidence").asText("") +
                "；动作：" + node.path("action").asText("");
    }

    private List<JsonNode> iterable(JsonNode arrayNode) {
        List<JsonNode> nodes = new java.util.ArrayList<>();
        arrayNode.forEach(nodes::add);
        return nodes;
    }

    private String buildTrainingContextJson(
            AiAnalysisContext context,
            String userNote,
            String reportType,
            LocalDateTime reportStart,
            LocalDateTime reportEnd) {
        try {
            Activity activity = context.getCurrentActivity();
            UserHistorySummary summary = context.getHistorySummary();
            RuleAnalysisResult rule = context.getRuleAnalysisResult();
            GoalAnalysisResult goalAnalysis = context.getGoalAnalysisResult();
            UserGoalProfile goal = context.getUserGoalProfile();
            List<Activity> records = context.getRecentActivities() == null ? List.of() : context.getRecentActivities();

            Map<String, Object> payload = new HashMap<>();
            payload.put("userProfile", Map.of(
                    "userId", activity.getUserId(),
                    "goal", goal == null ? "未设置" : defaultText(goal.getGoalType(), "未设置"),
                    "notes", defaultText(userNote, "")
            ));
            payload.put("analysisContext", Map.of(
                    "type", reportType,
                    "range", reportStart + " ~ " + reportEnd,
                    "mode", "enhanced_training_report"
            ));
            payload.put("trainingSummary", Map.of(
                    "totalSessions", summary.getTotalActivities(),
                    "totalDurationLast7Days", summary.getTotalDurationLast7Days(),
                    "totalDurationLast30Days", summary.getTotalDurationLast30Days(),
                    "totalCaloriesLast7Days", summary.getTotalCaloriesLast7Days(),
                    "totalCaloriesLast30Days", summary.getTotalCaloriesLast30Days(),
                    "activitiesLast7Days", summary.getActivitiesLast7Days(),
                    "activitiesLast30Days", summary.getActivitiesLast30Days(),
                    "mainTypes", summary.getRecentActivityTypes() == null ? List.of() : summary.getRecentActivityTypes(),
                    "averageDuration", summary.getAvgDurationLast7Days(),
                    "typeDistribution", summary.getActivityTypeDistribution() == null ? Map.of() : summary.getActivityTypeDistribution()
            ));
            payload.put("currentActivity", toActivityMap(activity));
            payload.put("records", records.stream().map(this::toActivityMap).toList());
            payload.put("ruleAnalysis", Map.of(
                    "trainingLoadLevel", rule == null ? "UNKNOWN" : defaultText(rule.getTrainingLoadLevel(), "UNKNOWN"),
                    "recoveryStatus", rule == null ? "UNKNOWN" : defaultText(rule.getRecoveryStatus(), "UNKNOWN"),
                    "consistencyLevel", rule == null ? "UNKNOWN" : defaultText(rule.getConsistencyLevel(), "UNKNOWN"),
                    "highlights", rule == null || rule.getHighlights() == null ? List.of() : rule.getHighlights(),
                    "risks", rule == null || rule.getRisks() == null ? List.of() : rule.getRisks(),
                    "suggestions", rule == null || rule.getSuggestions() == null ? List.of() : rule.getSuggestions()
            ));
            payload.put("goal", goal == null ? Map.of() : goalToMap(goal));
            payload.put("goalAnalysis", goalAnalysis == null ? Map.of() : goalAnalysisToMap(goalAnalysis));
            payload.put("userComment", defaultText(userNote, ""));

            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to build training context JSON", ex);
        }
    }

    private Map<String, Object> toActivityMap(Activity activity) {
        if (activity == null) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", activity.getId());
        map.put("type", activity.getType());
        map.put("durationMinutes", valueOrZero(activity.getDuration()));
        map.put("calories", valueOrZero(activity.getCalorieBurned()));
        map.put("startTime", activity.getStartTime());
        map.put("additionalMetrics", activity.getAdditionalMetrics() == null ? Map.of() : activity.getAdditionalMetrics());
        return map;
    }

    private Map<String, Object> goalToMap(UserGoalProfile goal) {
        Map<String, Object> map = new HashMap<>();
        map.put("goalType", defaultText(goal.getGoalType(), ""));
        map.put("targetValue", goal.getTargetValue());
        map.put("targetUnit", defaultText(goal.getTargetUnit(), ""));
        map.put("weeklyTargetFrequency", goal.getWeeklyTargetFrequency());
        map.put("weeklyTargetDuration", goal.getWeeklyTargetDuration());
        map.put("targetDate", goal.getTargetDate());
        map.put("experienceLevel", defaultText(goal.getExperienceLevel(), ""));
        map.put("priority", defaultText(goal.getPriority(), ""));
        map.put("note", defaultText(goal.getNote(), ""));
        return map;
    }

    private Map<String, Object> goalAnalysisToMap(GoalAnalysisResult goalAnalysis) {
        Map<String, Object> map = new HashMap<>();
        map.put("progressStatus", defaultText(goalAnalysis.getProgressStatus(), "UNKNOWN"));
        map.put("alignmentLevel", defaultText(goalAnalysis.getAlignmentLevel(), "UNKNOWN"));
        map.put("completionScore", goalAnalysis.getCompletionScore());
        map.put("alignmentScore", goalAnalysis.getAlignmentScore());
        map.put("feasibilityScore", goalAnalysis.getFeasibilityScore());
        map.put("strengths", goalAnalysis.getStrengths() == null ? List.of() : goalAnalysis.getStrengths());
        map.put("gaps", goalAnalysis.getGaps() == null ? List.of() : goalAnalysis.getGaps());
        map.put("actionSuggestions", goalAnalysis.getActionSuggestions() == null ? List.of() : goalAnalysis.getActionSuggestions());
        return map;
    }

    private String buildRuleStructuredJson(AiAnalysisContext context, String userNote) {
        try {
            RuleAnalysisResult rule = context.getRuleAnalysisResult();
            UserHistorySummary summary = context.getHistorySummary();
            GoalAnalysisResult goal = context.getGoalAnalysisResult();
            int score = Math.max(35, Math.min(85, goal == null || goal.getCompletionScore() == null ? 60 : goal.getCompletionScore()));

            Map<String, Object> payload = new HashMap<>();
            payload.put("analysisQuality", Map.of(
                    "dataCompleteness", summary.getTotalActivities() >= 5 ? "medium" : "low",
                    "confidence", summary.getTotalActivities() >= 5 ? 0.65 : 0.45,
                    "missingData", List.of("主观疲劳评分", "睡眠", "心率")
            ));
            payload.put("overall", Map.of(
                    "score", score,
                    "level", score >= 75 ? "良好" : "一般",
                    "summary", "系统已基于训练记录、规则引擎和目标进度完成结构化分析。",
                    "mainProblem", rule == null ? "数据不足" : defaultText(rule.getRecoveryStatus(), "恢复状态不足以判断"),
                    "mainPositive", summary.getActivitiesLast7Days() > 0 ? "近期已有训练记录" : "暂无明显优势"
            ));
            payload.put("trainingLoad", Map.of(
                    "level", mapLoad(rule == null ? null : rule.getTrainingLoadLevel()),
                    "trend", defaultText(summary.getTrend(), "稳定"),
                    "reason", "依据近 7 天与近 30 天训练次数、时长和热量估算。",
                    "risk", rule == null ? "不足以判断" : defaultText(rule.getRecoveryStatus(), "不足以判断")
            ));
            payload.put("recovery", Map.of(
                    "status", "FATIGUE_RISK".equals(rule == null ? null : rule.getRecoveryStatus()) ? "疲劳风险" : "恢复一般",
                    "reason", defaultText(userNote, "未提供主观反馈，主要依据训练频率和负荷判断。"),
                    "suggestion", "根据疲劳和训练负荷调整下一次训练强度。"
            ));
            payload.put("goalProgress", Map.of(
                    "status", goal == null ? "不足以判断" : mapGoal(goal.getProgressStatus()),
                    "reason", goal == null ? "未设置目标或目标数据不足。" : defaultText(goal.getAlignmentLevel(), "目标匹配度不足以判断"),
                    "nextFocus", "保持训练连续性，并根据恢复状态安排下一次训练。"
            ));
            payload.put("risks", List.of(Map.of(
                    "title", "恢复窗口不足风险",
                    "level", "medium",
                    "evidence", "近 7 天训练次数：" + summary.getActivitiesLast7Days(),
                    "impact", "连续训练可能影响恢复质量。",
                    "action", "未来 24-48 小时优先安排低强度训练或主动恢复。"
            )));
            payload.put("suggestions", List.of(Map.of(
                    "category", "recovery",
                    "title", "安排主动恢复",
                    "reason", "当前恢复状态需要继续观察。",
                    "evidence", "训练负荷：" + (rule == null ? "UNKNOWN" : rule.getTrainingLoadLevel()),
                    "action", "安排 20-40 分钟低强度有氧、拉伸或休息。",
                    "priority", "medium",
                    "timeWindow", "未来 48 小时"
            )));
            payload.put("nextTraining", Map.of(
                    "recommendedType", "低强度有氧或综合训练",
                    "durationMinutes", 40,
                    "intensity", "低",
                    "notes", "根据身体反馈调整，不追求高强度。"
            ));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to build rule structured JSON", ex);
        }
    }

    private String buildMarkdownFromStructuredJson(String deepSeekJson, AiAnalysisContext context) {
        try {
            JsonNode root = objectMapper.readTree(deepSeekJson);
            UserHistorySummary summary = context.getHistorySummary();
            return """
                    # 律动训练分析报告

                    ## 一、本次分析结论
                    %s

                    ## 二、训练数据概览
                    - 近 7 天训练次数：%d 次
                    - 近 7 天总时长：%d 分钟
                    - 近 7 天总消耗：%d 千卡
                    - 近 30 天训练次数：%d 次
                    - 主要训练类型：%s

                    ## 三、训练状态评分
                    - 综合评分：%d
                    - 等级：%s
                    - 当前最大问题：%s

                    ## 四、下一次训练建议
                    - 类型：%s
                    - 时长：%d 分钟
                    - 强度：%s
                    - 注意事项：%s
                    """.formatted(
                    root.path("overall").path("summary").asText("系统已完成训练分析。"),
                    summary.getActivitiesLast7Days(),
                    summary.getTotalDurationLast7Days(),
                    summary.getTotalCaloriesLast7Days(),
                    summary.getActivitiesLast30Days(),
                    summary.getMostFrequentActivityType(),
                    root.path("overall").path("score").asInt(60),
                    root.path("overall").path("level").asText("一般"),
                    root.path("overall").path("mainProblem").asText("暂无"),
                    root.path("nextTraining").path("recommendedType").asText("低强度训练"),
                    root.path("nextTraining").path("durationMinutes").asInt(40),
                    root.path("nextTraining").path("intensity").asText("低"),
                    root.path("nextTraining").path("notes").asText("根据身体反馈调整。")
            );
        } catch (Exception ex) {
            return "# 律动训练分析报告\n\n系统已生成基础训练分析，但 AI 报告生成失败。";
        }
    }

    private String mapLoad(String value) {
        if ("HIGH".equals(value)) {
            return "高";
        }
        if ("LOW".equals(value)) {
            return "低";
        }
        return "中";
    }

    private String mapGoal(String value) {
        if ("AHEAD".equals(value)) {
            return "领先";
        }
        if ("ON_TRACK".equals(value)) {
            return "正常";
        }
        if ("OFF_TRACK".equals(value) || "SERIOUSLY_OFF_TRACK".equals(value)) {
            return "落后";
        }
        return "不足以判断";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
