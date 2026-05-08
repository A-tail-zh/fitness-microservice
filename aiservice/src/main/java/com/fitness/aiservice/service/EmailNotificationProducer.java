package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.EmailNotificationEvent;
import com.fitness.aiservice.model.EmailNotificationLog;
import com.fitness.aiservice.model.NotificationSendStatus;
import com.fitness.aiservice.repository.EmailNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationProducer {

    private final RabbitTemplate rabbitTemplate;
    private final EmailNotificationLogRepository emailNotificationLogRepository;

    @Value("${notification.exchange.name}")
    private String notificationExchange;

    @Value("${notification.routing-key.email}")
    private String emailRoutingKey;

    public boolean publish(EmailNotificationEvent event) {
        if (event == null) {
            return false;
        }
        if (!StringUtils.hasText(event.getTo())) {
            log.debug("Skip email notification because recipient is empty, eventId={}, type={}",
                    event.getEventId(), event.getType());
            return false;
        }

        EmailNotificationEvent normalized = normalize(event);
        if (emailNotificationLogRepository.existsByEventIdAndNotificationTypeAndSendStatusIn(
                normalized.getEventId(),
                normalized.getType(),
                List.of(NotificationSendStatus.PENDING, NotificationSendStatus.SENT))) {
            log.debug("Skip duplicated email notification, eventId={}, type={}",
                    normalized.getEventId(), normalized.getType());
            return false;
        }

        EmailNotificationLog logEntry = EmailNotificationLog.builder()
                .eventId(normalized.getEventId())
                .userId(normalized.getUserId())
                .email(normalized.getTo())
                .notificationType(normalized.getType())
                .subject(normalized.getSubject())
                .payload(normalized.getPayload())
                .sendStatus(NotificationSendStatus.PENDING)
                .createdAt(normalized.getCreatedAt())
                .build();
        emailNotificationLogRepository.save(logEntry);
        log.debug("Email notification event created, eventId={}, type={}, to={}",
                normalized.getEventId(), normalized.getType(), normalized.getTo());

        rabbitTemplate.convertAndSend(notificationExchange, emailRoutingKey, normalized);
        log.debug("Email notification event sent to RabbitMQ, exchange={}, routingKey={}, eventId={}",
                notificationExchange, emailRoutingKey, normalized.getEventId());
        return true;
    }

    private EmailNotificationEvent normalize(EmailNotificationEvent event) {
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }
        if (!StringUtils.hasText(event.getEventId())) {
            event.setEventId(event.getType() + ":" + event.getUserId() + ":" + event.getCreatedAt());
        }
        return event;
    }
}
