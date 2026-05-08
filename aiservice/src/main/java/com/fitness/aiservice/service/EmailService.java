package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.EmailNotificationEvent;
import com.fitness.aiservice.model.EmailNotificationLog;
import com.fitness.aiservice.model.NotificationSendStatus;
import com.fitness.aiservice.model.NotificationType;
import com.fitness.aiservice.repository.EmailNotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final EmailTemplateBuilder emailTemplateBuilder;
    private final EmailNotificationLogRepository emailNotificationLogRepository;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${notification.mail.from:${spring.mail.username:}}")
    private String fromAddress;

    public void send(EmailNotificationEvent event) {
        if (event.getType() == NotificationType.GOAL_COMPLETED) {
            sendGoalCompletedEmail(event);
            return;
        }
        if (event.getType() == NotificationType.AI_REPORT_GENERATED) {
            sendAiReportGeneratedEmail(event);
            return;
        }
        log.debug("Unsupported email notification type, eventId={}, type={}", event.getEventId(), event.getType());
    }

    public void sendGoalCompletedEmail(EmailNotificationEvent event) {
        sendHtml(event, emailTemplateBuilder.buildGoalCompletedHtml(event));
    }

    public void sendAiReportGeneratedEmail(EmailNotificationEvent event) {
        sendHtml(event, emailTemplateBuilder.buildAiReportGeneratedHtml(event));
    }

    private void sendHtml(EmailNotificationEvent event, String html) {
        EmailNotificationLog logEntry = emailNotificationLogRepository
                .findFirstByEventIdOrderByCreatedAtDesc(event.getEventId())
                .orElse(null);

        if (!StringUtils.hasText(event.getTo())) {
            log.debug("Skip email sending because recipient is empty, eventId={}", event.getEventId());
            markFailed(logEntry, "recipient email is empty");
            return;
        }
        if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
            log.debug("Skip email sending because MAIL_USERNAME or MAIL_PASSWORD is empty, eventId={}", event.getEventId());
            markFailed(logEntry, "mail credentials missing");
            return;
        }

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(StringUtils.hasText(fromAddress) ? fromAddress : mailUsername);
            helper.setTo(event.getTo());
            helper.setSubject(event.getSubject());
            helper.setText(html, true);
            javaMailSender.send(message);

            if (logEntry != null) {
                logEntry.setSendStatus(NotificationSendStatus.SENT);
                logEntry.setSentAt(LocalDateTime.now());
                logEntry.setErrorMessage(null);
                emailNotificationLogRepository.save(logEntry);
            }
            log.debug("Email sent successfully, eventId={}, type={}, to={}",
                    event.getEventId(), event.getType(), event.getTo());
        } catch (Exception ex) {
            markFailed(logEntry, ex.getMessage());
            log.error("Email sending failed, eventId={}, type={}, to={}, reason={}",
                    event.getEventId(), event.getType(), event.getTo(), ex.getMessage(), ex);
        }
    }

    private void markFailed(EmailNotificationLog logEntry, String reason) {
        if (logEntry == null) {
            return;
        }
        logEntry.setSendStatus(NotificationSendStatus.FAILED);
        logEntry.setErrorMessage(reason);
        emailNotificationLogRepository.save(logEntry);
    }
}
