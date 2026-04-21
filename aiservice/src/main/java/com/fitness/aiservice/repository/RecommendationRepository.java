package com.fitness.aiservice.repository;

import com.fitness.aiservice.model.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationRepository extends MongoRepository<Recommendation,String> {
    List<Recommendation> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query(value = "{ 'activityId': ?0, '$or': [ { 'recommendationType': 'STANDARD' }, { 'recommendationType': null } ] }",
            sort = "{ 'createdAt': -1 }")
    Optional<Recommendation> findLatestStandardByActivityId(String activityId);
}
