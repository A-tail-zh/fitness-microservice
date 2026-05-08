package com.fitness.activityservice.repository;

import com.fitness.activityservice.model.ImportedActivityRecord;
import com.fitness.activityservice.model.ThirdPartyPlatform;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ImportedActivityRecordRepository extends MongoRepository<ImportedActivityRecord, String> {
    Optional<ImportedActivityRecord> findByPlatformAndExternalActivityId(ThirdPartyPlatform platform, String externalActivityId);

    List<ImportedActivityRecord> findByUserIdAndPlatform(String userId, ThirdPartyPlatform platform);
}
