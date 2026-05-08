package com.fitness.activityservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "third_party_accounts")
@CompoundIndex(name = "uk_user_platform", def = "{'userId': 1, 'platform': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyAccount {
    @Id
    private String id;
    private String userId;
    private ThirdPartyPlatform platform;
    private String externalUserId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpireTime;
    private ThirdPartyBindStatus bindStatus;
    private String oauthState;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastSyncMessage;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
