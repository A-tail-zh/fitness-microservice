package com.fitness.aiservice.dto;

import com.fitness.aiservice.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationEvent {
    private String eventId;
    private String userId;
    private String to;
    private String username;
    private NotificationType type;
    private String subject;
    private Map<String, Object> payload;
    private LocalDateTime createdAt;

    public String getEmail() {
        return to;
    }
}
