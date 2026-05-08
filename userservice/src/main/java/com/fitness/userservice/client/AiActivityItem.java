package com.fitness.userservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActivityItem {
    private String id;
    private Integer duration;
    private LocalDateTime startTime;
    private LocalDateTime createdAt;
}
