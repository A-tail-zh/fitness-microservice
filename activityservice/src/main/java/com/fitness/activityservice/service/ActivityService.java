package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    public ActivityResponse trackActivity(ActivityRequest request) {
        Activity activity = Activity.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .duration(request.getDuration())
                .calorieBurned(request.getCalorieBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();
        Activity savedActivity = activityRepository.save(activity);

        return mapToActivityResponse(savedActivity);
    }

    private ActivityResponse mapToActivityResponse(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getUserId(), activity.getType(), activity.getDuration(),
                activity.getCalorieBurned(), activity.getStartTime(), activity.getAdditionalMetrics(),
                activity.getCreatedAt(), activity.getUpdatedAt());
    }


    public List<ActivityResponse> getUserActivities(String userId) {
            return activityRepository.findByUserId(userId);
    }

    public ActivityResponse getActivityById(String activityId) {
        return activityRepository.findById(activityId)
                .map(this::mapToActivityResponse)
                .orElseThrow(()->new RuntimeException("活动不存在"));
    }
}
