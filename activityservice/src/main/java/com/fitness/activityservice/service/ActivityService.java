package com.fitness.activityservice.service;

import com.fitness.activityservice.client.AiRecommendationClient;
import com.fitness.activityservice.dto.ActivityImportResult;
import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.dto.StandardActivityDTO;
import com.fitness.activityservice.exception.ActivityNotFoundException;
import com.fitness.activityservice.exception.InvalidUserException;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.model.ActivitySource;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final RabbitTemplate rabbitTemplate;
    private final AiRecommendationClient aiRecommendationClient;

    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    @Value("${ai.recommendation.auto-trigger.enabled:false}")
    private boolean autoTriggerAiRecommendation;

    public ActivityResponse trackActivity(ActivityRequest request) {
        ensureValidUser(request.getUserId());

        Activity activity = Activity.builder()
                .userId(request.getUserId())
                .source(ActivitySource.LOCAL)
                .type(request.getType())
                .duration(request.getDuration())
                .calorieBurned(request.getCalorieBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();

        Activity savedActivity = activityRepository.save(activity);
        sendActivityMessage(savedActivity);
        return mapToActivityResponse(savedActivity);
    }

    public ActivityResponse importActivity(StandardActivityDTO request) {
        return importActivityWithResult(request).getActivity();
    }

    public ActivityImportResult importActivityWithResult(StandardActivityDTO request) {
        ensureValidUser(request.getUserId());

        ActivitySource source = request.getSource() == null ? ActivitySource.GARMIN : request.getSource();
        Map<String, Object> importedMetrics = buildImportedMetrics(request);

        if (StringUtils.hasText(request.getExternalActivityId())) {
            Activity existing = activityRepository.findBySourceAndExternalActivityId(source, request.getExternalActivityId())
                    .orElse(null);
            if (existing != null) {
                boolean changed = applyImportedChanges(existing, request, source, importedMetrics);
                if (!changed) {
                    return new ActivityImportResult("skipped", mapToActivityResponse(existing));
                }

                Activity savedActivity = activityRepository.save(existing);
                sendActivityMessage(savedActivity);
                return new ActivityImportResult("updated", mapToActivityResponse(savedActivity));
            }
        }

        Activity activity = Activity.builder()
                .userId(request.getUserId())
                .source(source)
                .externalActivityId(request.getExternalActivityId())
                .type(request.getType())
                .duration(normalizeDurationMinutes(request.getDurationSeconds()))
                .calorieBurned(normalizeCalories(request.getCalories()))
                .startTime(request.getStartTime())
                .syncedAt(LocalDateTime.now())
                .additionalMetrics(importedMetrics)
                .build();

        Activity savedActivity = activityRepository.save(activity);
        sendActivityMessage(savedActivity);
        return new ActivityImportResult("created", mapToActivityResponse(savedActivity));
    }

    public List<ActivityResponse> getUserActivities(String userId) {
        return activityRepository.findByUserId(userId)
                .stream()
                .map(this::mapToActivityResponse)
                .toList();
    }

    public ActivityResponse getActivityById(String activityId) {
        return activityRepository.findById(activityId)
                .map(this::mapToActivityResponse)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found, id=" + activityId));
    }

    public void deleteActivity(String activityId, String userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found, id=" + activityId));
        if (!activity.getUserId().equals(userId)) {
            throw new InvalidUserException("Cannot delete another user's activity");
        }
        activityRepository.delete(activity);
        aiRecommendationClient.deleteByActivityId(activityId);
    }

    private void ensureValidUser(String userId) {
        if (!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid user: " + userId);
        }
    }

    private ActivityResponse mapToActivityResponse(Activity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getUserId(),
                activity.getSource(),
                activity.getExternalActivityId(),
                activity.getType(),
                activity.getDuration(),
                activity.getCalorieBurned(),
                activity.getStartTime(),
                activity.getSyncedAt(),
                activity.getAdditionalMetrics(),
                activity.getCreatedAt(),
                activity.getUpdatedAt()
        );
    }

    private void sendActivityMessage(Activity activity) {
        if (!autoTriggerAiRecommendation) {
            log.debug("Skip automatic AI recommendation trigger, activityId={}", activity.getId());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, activity);
        } catch (Exception e) {
            log.error("Failed to publish activity message to RabbitMQ, activityId={}", activity.getId(), e);
        }
    }

    private Integer normalizeDurationMinutes(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(durationSeconds / 60.0d));
    }

    private Integer normalizeCalories(Double calories) {
        if (calories == null) {
            return 0;
        }
        return Math.max(0, (int) Math.round(calories));
    }

    private Map<String, Object> buildImportedMetrics(StandardActivityDTO request) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        putIfPresent(metrics, "distance", request.getDistance());
        if (request.getSource() == ActivitySource.GARMIN) {
            putIfPresent(metrics, "distanceKm", request.getDistance());
        }
        putIfPresent(metrics, "durationSeconds", request.getDurationSeconds());
        putIfPresent(metrics, "averageHeartRate", request.getAvgHeartRate());
        putIfPresent(metrics, "maxHeartRate", request.getMaxHeartRate());
        putIfPresent(metrics, "averagePace", request.getAvgPace());
        if (request.getRawData() != null && !request.getRawData().isEmpty()) {
            metrics.put("rawData", request.getRawData());
        }
        return metrics;
    }

    private void putIfPresent(Map<String, Object> metrics, String key, Object value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    private boolean applyImportedChanges(
            Activity existing,
            StandardActivityDTO request,
            ActivitySource source,
            Map<String, Object> importedMetrics) {
        boolean changed = false;
        changed |= setIfChanged(existing.getUserId(), request.getUserId(), existing::setUserId);
        changed |= setIfChanged(existing.getSource(), source, existing::setSource);
        changed |= setIfChanged(existing.getExternalActivityId(), request.getExternalActivityId(), existing::setExternalActivityId);
        changed |= setIfChanged(existing.getType(), request.getType(), existing::setType);
        changed |= setIfChanged(existing.getDuration(), normalizeDurationMinutes(request.getDurationSeconds()), existing::setDuration);
        changed |= setIfChanged(existing.getCalorieBurned(), normalizeCalories(request.getCalories()), existing::setCalorieBurned);
        changed |= setIfChanged(existing.getStartTime(), request.getStartTime(), existing::setStartTime);
        changed |= setIfChanged(existing.getAdditionalMetrics(), importedMetrics, existing::setAdditionalMetrics);
        if (changed) {
            existing.setSyncedAt(LocalDateTime.now());
        }
        return changed;
    }

    private <T> boolean setIfChanged(T oldValue, T newValue, Consumer<T> setter) {
        if (Objects.equals(oldValue, newValue)) {
            return false;
        }
        setter.accept(newValue);
        return true;
    }
}
