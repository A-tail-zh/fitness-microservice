package com.fitness.aiservice.service;

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

    /**
     * 根据活动数据生成 AI 建议，并持久化到 MongoDB
     */
    public Recommendation generateAndSaveRecommendation(Activity activity) {
        log.info("开始为活动 [{}] 生成 AI 建议，用户: {}", activity.getId(), activity.getUserId());

        // 1. 构建 prompt
        String prompt = createPromptForActivity(activity);

        // 2. 调用千问 API
        String rawResponse = qwenService.getAnswer(prompt);
        log.info("收到千问原始响应");

        // 3. 提取 content
        String content = qwenService.extractContent(rawResponse);
        log.info("AI 建议内容:\n{}", content);

        // 4. 构建 Recommendation 对象
        Recommendation recommendation = buildRecommendation(activity, content);

        // 5. 保存到 MongoDB（如已存在则更新）
        recommendationRepository.findByActivityId(activity.getId()).ifPresent(existing -> {
            recommendation.setId(existing.getId());
            log.info("活动 [{}] 已有建议，执行更新", activity.getId());
        });

        Recommendation saved = recommendationRepository.save(recommendation);
        log.info("AI 建议已保存，ID: {}", saved.getId());
        return saved;
    }

    /**
     * 从 AI 内容中解析 improvements 和 suggestions
     */
    private Recommendation buildRecommendation(Activity activity, String content) {
        List<String> improvements = extractSection(content, "问题分析：", "优化建议：");
        List<String> suggestions = extractSection(content, "优化建议：", "下次训练计划：");

        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation(content)
                .improvements(improvements)
                .suggestions(suggestions)
                .createAt(LocalDateTime.now())
                .build();
    }

    /**
     * 从文本中提取两个标题之间的内容，按行分割
     */
    private List<String> extractSection(String content, String startMarker, String endMarker) {
        try {
            int start = content.indexOf(startMarker);
            int end = endMarker != null ? content.indexOf(endMarker) : -1;

            if (start == -1) return List.of();

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

    /**
     * 构建面向运动科学教练的提示词
     */
    public String createPromptForActivity(Activity activity) {
        StringBuilder metricsBuilder = new StringBuilder();
        if (activity.getAdditionalMetrics() != null) {
            activity.getAdditionalMetrics().forEach((key, value) ->
                    metricsBuilder.append("- ").append(key).append("：").append(value).append("\n")
            );
        }

        return String.format("""
                        你是一名专业的运动科学教练，擅长跑步训练、体能提升和运动恢复管理。
                        
                        请基于以下用户运动数据进行分析，并提供专业建议。
                        
                        【基础信息】
                        - 运动类型：%s
                        - 持续时间：%d 分钟
                        - 消耗卡路里：%d kcal
                        - 开始时间：%s
                        
                        【详细指标】
                        %s
                        
                        【分析任务】
                        1. 判断本次训练强度（低 / 中 / 高）
                        2. 分析训练是否科学（时间、强度是否合理）
                        3. 判断是否存在疲劳或受伤风险
                        4. 给出优化建议（强度 / 时长 / 频率）
                        5. 给出下一次训练建议（具体可执行）
                        
                        【输出格式（必须严格遵守）】
                        训练评估：
                        （总结本次训练情况）
                        
                        问题分析：
                        （指出问题）
                        
                        优化建议：
                        （给出具体改进方案）
                        
                        下次训练计划：
                        （明确到时间/强度/频率）
                        
                        要求：
                        - 使用中文
                        - 语言简洁专业
                        - 不要输出无关内容
                        """,
                activity.getType(),
                activity.getDuration(),
                activity.getCalorieBurned(),
                activity.getStartTime(),
                metricsBuilder
        );
    }
}
