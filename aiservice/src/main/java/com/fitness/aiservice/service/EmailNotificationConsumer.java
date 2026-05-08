package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.EmailNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = "${notification.queue.email}")
    public void handleEmailNotification(EmailNotificationEvent event) {
        log.debug("Email notification consume started, eventId={}, type={}, to={}",
                event.getEventId(), event.getType(), event.getTo());
        emailService.send(event);
    }
}
