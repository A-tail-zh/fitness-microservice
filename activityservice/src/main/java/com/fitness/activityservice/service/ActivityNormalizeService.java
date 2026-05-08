package com.fitness.activityservice.service;

import com.fitness.activityservice.client.GarminApiClient;
import com.fitness.activityservice.client.GarminPythonSyncClient;
import com.fitness.activityservice.dto.StandardActivityDTO;
import com.fitness.activityservice.model.ActivitySource;
import com.fitness.activityservice.model.ActivityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@Slf4j
public class ActivityNormalizeService {

    public StandardActivityDTO normalizeGarminActivity(String userId, GarminApiClient.GarminActivityItem activity) {
        if (activity == null) {
            return null;
        }

        ActivityType activityType = mapActivityType(activity.getActivityType());
        StandardActivityDTO dto = new StandardActivityDTO();
        dto.setUserId(userId);
        dto.setSource(ActivitySource.GARMIN);
        dto.setExternalActivityId(buildExternalActivityId(activity));
        dto.setType(activityType);
        dto.setDistance(normalizeGarminDistanceKilometers(activity.getDistance()));
        dto.setDurationSeconds(activity.getDurationSeconds());
        dto.setCalories(activity.getCalories());
        dto.setAvgHeartRate(activity.getAvgHeartRate());
        dto.setMaxHeartRate(activity.getMaxHeartRate());
        dto.setAvgPace(activity.getAvgPace());
        dto.setStartTime(activity.getStartTime());
        dto.setRawData(activity.getRawData());
        return dto;
    }

    public StandardActivityDTO normalizeGarminActivity(String userId, GarminPythonSyncClient.GarminActivityItem activity) {
        if (activity == null) {
            return null;
        }

        ActivityType activityType = mapActivityType(activity.getActivityType());
        StandardActivityDTO dto = new StandardActivityDTO();
        dto.setUserId(userId);
        dto.setSource(ActivitySource.GARMIN);
        dto.setExternalActivityId(buildExternalActivityId(activity));
        dto.setType(activityType);
        dto.setDistance(normalizeGarminDistanceKilometers(activity.getDistance()));
        dto.setDurationSeconds(activity.getDurationSeconds());
        dto.setCalories(activity.getCalories());
        dto.setAvgHeartRate(activity.getAvgHeartRate());
        dto.setMaxHeartRate(activity.getMaxHeartRate());
        dto.setAvgPace(activity.getAvgPace());
        dto.setStartTime(activity.getStartTime());
        dto.setRawData(activity.getRawData());
        return dto;
    }

    private String buildExternalActivityId(GarminApiClient.GarminActivityItem activity) {
        if (StringUtils.hasText(activity.getActivityId())) {
            return activity.getActivityId();
        }
        return "%s_%s_%s".formatted(
                activity.getActivityType(),
                activity.getStartTime(),
                activity.getDurationSeconds());
    }

    private String buildExternalActivityId(GarminPythonSyncClient.GarminActivityItem activity) {
        if (StringUtils.hasText(activity.getActivityId())) {
            return activity.getActivityId();
        }
        return "%s_%s_%s".formatted(
                activity.getActivityType(),
                activity.getStartTime(),
                activity.getDurationSeconds());
    }

    private Double normalizeGarminDistanceKilometers(Double distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        return distanceMeters / 1000.0d;
    }

    private ActivityType mapActivityType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return ActivityType.UNCATEGORIZED;
        }

        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("UNCATEGORIZED") || normalized.contains("UNCLASSIFIED") || normalized.contains("UNKNOWN")) {
            return ActivityType.UNCATEGORIZED;
        }
        if (normalized.contains("RUN")) return ActivityType.RUNNING;
        if (normalized.contains("CYCLE") || normalized.contains("BIKE")) return ActivityType.CYCLING;
        if (normalized.contains("SWIM")) return ActivityType.SWIMMING;
        if (normalized.contains("YOGA")) return ActivityType.YOGA;
        if (normalized.contains("PILATES")) return ActivityType.PILATES;
        if (normalized.contains("HIIT")) return ActivityType.HIIT;
        if (normalized.contains("STRENGTH") || normalized.contains("WEIGHT")) return ActivityType.STRENGTH_TRAINING;
        if (normalized.contains("STRETCH")) return ActivityType.STRETCHING;
        if (normalized.contains("BOX")) return ActivityType.BOXING;
        if (normalized.contains("DANCE")) return ActivityType.DANCING;
        if (normalized.contains("MEDITATION")) return ActivityType.MEDITATION;
        return ActivityType.UNCATEGORIZED;
    }
}
