package com.fitness.activityservice.repository;

import com.fitness.activityservice.model.ThirdPartyAccount;
import com.fitness.activityservice.model.ThirdPartyBindStatus;
import com.fitness.activityservice.model.ThirdPartyPlatform;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ThirdPartyAccountRepository extends MongoRepository<ThirdPartyAccount, String> {
    Optional<ThirdPartyAccount> findByUserIdAndPlatform(String userId, ThirdPartyPlatform platform);

    Optional<ThirdPartyAccount> findByPlatformAndOauthState(ThirdPartyPlatform platform, String oauthState);

    List<ThirdPartyAccount> findByPlatformAndBindStatus(ThirdPartyPlatform platform, ThirdPartyBindStatus bindStatus);
}
