package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.recommendation.auto-trigger.enabled", havingValue = "true")
public class ActivityMessageListener {

    private final ActivityAiService activityAiService;

    @RabbitListener(queues = "activity.queue")
    public void processActivity(Activity activity) {
        log.info("收到活动消息，activityId: {}, userId: {}, type: {}",
                activity.getId(), activity.getUserId(), activity.getType());
        try {
            Recommendation recommendation = activityAiService.generateAndSaveRecommendation(activity);
            log.info("活动 [{}] 的 AI 建议已生成并保存，recommendationId: {}",
                    activity.getId(), recommendation.getId());
        } catch (Exception e) {
            log.error("处理活动 [{}] 时发生错误", activity.getId(), e);
            // 可根据需要抛出异常触发 RabbitMQ 重试或死信队列
        }
    }

}
