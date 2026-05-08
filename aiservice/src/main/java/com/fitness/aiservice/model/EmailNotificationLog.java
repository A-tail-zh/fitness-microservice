package com.fitness.aiservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "email_notification_logs")
public class EmailNotificationLog {
    @Id
    private String id;
    private String eventId;
    private String userId;
    private String email;
    private NotificationType notificationType;
    private String subject;
    private String content;
    private Map<String, Object> payload;
    private NotificationSendStatus sendStatus;
    private String errorMessage;
    @CreatedDate
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
