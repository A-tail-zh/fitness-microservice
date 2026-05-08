package com.fitness.aiservice.repository;

import com.fitness.aiservice.model.EmailNotificationLog;
import com.fitness.aiservice.model.NotificationSendStatus;
import com.fitness.aiservice.model.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.Optional;

public interface EmailNotificationLogRepository extends MongoRepository<EmailNotificationLog, String> {
    boolean existsByEventIdAndNotificationTypeAndSendStatusIn(
            String eventId,
            NotificationType notificationType,
            Collection<NotificationSendStatus> statuses);

    Optional<EmailNotificationLog> findFirstByEventIdOrderByCreatedAtDesc(String eventId);
}
