package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.dto.FitnessAssessmentAnalysisRequest;
import com.fitness.aiservice.dto.FitnessAssessmentAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class FitnessAssessmentAnalysisService {

    private final QwenService qwenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FitnessAssessmentAnalysisResponse analyze(FitnessAssessmentAnalysisRequest request) {
        validate(request);

        try {
            String rawResponse = qwenService.getAnswer(buildSystemPrompt(), buildUserPrompt(request));
            String content = qwenService.extractContent(rawResponse);
            FitnessAssessmentAnalysisResponse parsed = parseAiResponse(content);
            parsed.setRawReport(content);
            if (!StringUtils.hasText(parsed.getFitnessLevel())) {
                parsed.setFitnessLevel(defaultLevel(request));
            }
            if (!StringUtils.hasText(parsed.getSuggestion()) && StringUtils.hasText(parsed.getTrainingSuggestion())) {
                parsed.setSuggestion(parsed.getTrainingSuggestion());
            }
            return parsed;
        } catch (Exception ex) {
            log.warn("体能评估 AI 分析失败，已使用降级结果，userId={}", request.getUserId(), ex);
            return fallback(request, "AI 服务不可用，已返回降级结果。");
        }
    }

    private void validate(FitnessAssessmentAnalysisRequest request) {
        if (!StringUtils.hasText(request.getUserId())) {
            throw new IllegalArgumentException("userId 不能为空");
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一名专业运动教练和运动健康分析师。
                你会结合用户基础信息、问卷信息以及近 30 天真实运动数据评估运动水平。
                如果活动数据包含距离、时长、心率、配速等指标，要优先使用这些真实数据判断。
                只返回 JSON，不要使用 Markdown，不要输出额外解释。
                fitnessLevel 只能是 BEGINNER、NOVICE、INTERMEDIATE、ADVANCED 之一。
                """;
    }

    private String buildUserPrompt(FitnessAssessmentAnalysisRequest request) {
        return """
                请根据以下信息判断用户当前运动水平，并特别关注真实运动数据所反映的训练频率、负荷、有氧基础和受伤风险。

                基础信息：
                年龄：%s
                身高：%s
                体重：%s
                性别：%s
                目标：%s
                每周运动次数：%s
                最近一次运动时间：%s
                伤病情况：%s
                运动经验：%s
                规则等级：%s

                近期运动摘要：
                %s

                近期真实运动明细：
                %s

                请输出 JSON：
                {
                  "fitnessLevel": "BEGINNER/NOVICE/INTERMEDIATE/ADVANCED",
                  "summary": "用户当前水平总结",
                  "reason": "判断依据，需说明训练频率、强度和真实数据特征",
                  "suggestion": "下一阶段训练建议",
                  "riskWarning": "风险提示"
                }
                """.formatted(
                defaultText(numberText(request.getAge())),
                defaultText(numberText(request.getHeight())),
                defaultText(numberText(request.getWeight())),
                defaultText(request.getGender()),
                defaultText(request.getGoal()),
                defaultText(numberText(request.getWeeklyExerciseFrequency())),
                defaultText(request.getRecentExerciseTime()),
                defaultText(request.getInjuryStatus()),
                defaultText(request.getExerciseExperience()),
                defaultLevel(request),
                defaultText(request.getRecentActivitySummary()),
                buildRecentActivitiesText(request.getRecentActivities())
        );
    }

    private String buildRecentActivitiesText(List<FitnessAssessmentAnalysisRequest.ActivityItem> recentActivities) {
        if (recentActivities == null || recentActivities.isEmpty()) {
            return "暂无近期训练记录。";
        }

        return recentActivities.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::activityTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(20)
                .map(activity -> String.format(
                        Locale.ROOT,
                        "时间=%s，类型=%s，来源=%s，时长=%s 分钟，距离=%s 米，热量=%s 千卡，平均心率=%s，最高心率=%s，平均配速=%s",
                        activityTime(activity) == null ? "未知" : activityTime(activity).toString(),
                        defaultText(activity.getType()),
                        defaultText(activity.getSource()),
                        defaultText(numberText(activity.getDuration())),
                        defaultText(numberText(activity.getDistance())),
                        defaultText(numberText(activity.getCalories())),
                        defaultText(numberText(activity.getAvgHeartRate())),
                        defaultText(numberText(activity.getMaxHeartRate())),
                        defaultText(numberText(activity.getAvgPace()))))
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无近期训练记录。");
    }

    private FitnessAssessmentAnalysisResponse parseAiResponse(String content) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);
            return FitnessAssessmentAnalysisResponse.builder()
                    .fitnessLevel(root.path("fitnessLevel").asText())
                    .summary(root.path("summary").asText())
                    .reason(root.path("reason").asText())
                    .suggestion(firstText(root, "suggestion", "trainingSuggestion"))
                    .trainingSuggestion(firstText(root, "trainingSuggestion", "suggestion"))
                    .riskWarning(root.path("riskWarning").asText())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("解析 AI 评估结果失败", ex);
        }
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return "{}";
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }

    private FitnessAssessmentAnalysisResponse fallback(FitnessAssessmentAnalysisRequest request, String rawReport) {
        String level = defaultLevel(request);
        return FitnessAssessmentAnalysisResponse.builder()
                .fitnessLevel(level)
                .summary("已根据规则结果和近期真实运动摘要生成基础评估。")
                .reason("规则等级为 %s，近期运动摘要为：%s".formatted(level, defaultText(request.getRecentActivitySummary())))
                .suggestion(defaultSuggestion(level))
                .trainingSuggestion(defaultSuggestion(level))
                .riskWarning(StringUtils.hasText(request.getInjuryStatus())
                        ? "存在伤病信息，建议控制训练强度，并根据身体反馈及时调整。"
                        : "当前未发现明显风险，建议继续关注疲劳与恢复状态。")
                .rawReport(rawReport)
                .build();
    }

    private String defaultLevel(FitnessAssessmentAnalysisRequest request) {
        return StringUtils.hasText(request.getRuleLevel())
                ? request.getRuleLevel().trim().toUpperCase(Locale.ROOT)
                : inferLevelFromActivities(request.getRecentActivities());
    }

    private String inferLevelFromActivities(List<FitnessAssessmentAnalysisRequest.ActivityItem> activities) {
        if (activities == null || activities.isEmpty()) {
            return "NOVICE";
        }
        long longSessions = activities.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getDurationSeconds() != null && item.getDurationSeconds() >= 2400)
                .count();
        if (activities.size() >= 16 || longSessions >= 8) return "ADVANCED";
        if (activities.size() >= 10 || longSessions >= 4) return "INTERMEDIATE";
        if (activities.size() >= 4) return "NOVICE";
        return "BEGINNER";
    }

    private String defaultSuggestion(String level) {
        return switch (level) {
            case "BEGINNER" -> "建议从低强度有氧和基础动作练习开始，每周训练 2 到 3 次。";
            case "NOVICE" -> "建议保持每周 3 次左右的规律训练，并逐步补充力量和有氧内容。";
            case "INTERMEDIATE" -> "建议采用更结构化的进阶计划，控制每周负荷增长。";
            case "ADVANCED" -> "建议采用更精细的周期化训练，并重点监控恢复质量。";
            default -> "建议先建立稳定的训练节奏，再逐步增加难度。";
        };
    }

    private String firstText(JsonNode root, String primary, String secondary) {
        String first = root.path(primary).asText();
        return StringUtils.hasText(first) ? first : root.path(secondary).asText();
    }

    private String numberText(Number value) {
        return value == null ? null : value.toString();
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "暂无";
    }

    private LocalDateTime activityTime(FitnessAssessmentAnalysisRequest.ActivityItem activity) {
        if (activity == null) {
            return null;
        }
        return activity.getStartTime() != null ? activity.getStartTime() : activity.getCreatedAt();
    }
}
