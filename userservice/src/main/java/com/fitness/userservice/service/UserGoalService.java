package com.fitness.userservice.service;

import com.fitness.userservice.client.ActivityHistoryClient;
import com.fitness.userservice.client.GoalNotificationClient;
import com.fitness.userservice.dto.UserGoalRequest;
import com.fitness.userservice.dto.UserGoalResponse;
import com.fitness.userservice.exception.UserGoalNotFoundException;
import com.fitness.userservice.model.GoalPriority;
import com.fitness.userservice.model.GoalStatus;
import com.fitness.userservice.model.GoalType;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserGoal;
import com.fitness.userservice.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGoalService {

    private static final int AUTO_COMPLETE_WINDOW_DAYS = 7;

    private final ActivityHistoryClient activityHistoryClient;
    private final UserGoalRepository userGoalRepository;
    private final UserService userService;
    private final GoalNotificationClient goalNotificationClient;

    public UserGoalResponse createGoal(String userId, UserGoalRequest request) {
        User user = userService.resolveUserOrThrow(userId);

        userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(user.getId(), GoalStatus.ACTIVE)
                .ifPresent(activeGoal -> {
                    activeGoal.setStatus(GoalStatus.PAUSED);
                    userGoalRepository.save(activeGoal);
                });

        UserGoal goal = new UserGoal();
        goal.setUserId(user.getId());
        goal.setGoalType(request.getGoalType());
        goal.setTargetValue(request.getTargetValue());
        goal.setTargetUnit(request.getTargetUnit());
        goal.setWeeklyTargetFrequency(request.getWeeklyTargetFrequency());
        goal.setWeeklyTargetDuration(request.getWeeklyTargetDuration());
        goal.setTargetDate(request.getTargetDate());
        goal.setExperienceLevel(request.getExperienceLevel());
        goal.setPriority(request.getPriority() == null ? GoalPriority.MEDIUM : request.getPriority());
        goal.setNote(request.getNote());
        goal.setStatus(GoalStatus.ACTIVE);

        UserGoal saved = refreshGoalCompletion(user, userGoalRepository.save(goal));
        log.info("创建用户目标成功，userId={}, goalId={}, goalType={}", user.getId(), saved.getId(), saved.getGoalType());
        return toResponse(saved);
    }

    public UserGoalResponse getActiveGoal(String userId) {
        User user = userService.resolveUserOrThrow(userId);
        refreshAutomaticCompletion(user);
        UserGoal goal = userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(user.getId(), GoalStatus.ACTIVE)
                .orElseThrow(() -> new UserGoalNotFoundException("未找到当前有效目标，userId=" + userId));
        return toResponse(goal);
    }

    public List<UserGoalResponse> getAllGoals(String userId) {
        User user = userService.resolveUserOrThrow(userId);
        refreshAutomaticCompletion(user);
        return userGoalRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserGoalResponse updateGoal(String userId, String goalId, UserGoalRequest request) {
        User user = userService.resolveUserOrThrow(userId);

        UserGoal goal = userGoalRepository.findById(goalId)
                .filter(item -> item.getUserId().equals(user.getId()))
                .orElseThrow(() -> new UserGoalNotFoundException("未找到目标，goalId=" + goalId));

        goal.setGoalType(request.getGoalType());
        goal.setTargetValue(request.getTargetValue());
        goal.setTargetUnit(request.getTargetUnit());
        goal.setWeeklyTargetFrequency(request.getWeeklyTargetFrequency());
        goal.setWeeklyTargetDuration(request.getWeeklyTargetDuration());
        goal.setTargetDate(request.getTargetDate());
        goal.setExperienceLevel(request.getExperienceLevel());
        goal.setPriority(request.getPriority() == null ? goal.getPriority() : request.getPriority());
        goal.setNote(request.getNote());

        UserGoal saved = refreshGoalCompletion(user, userGoalRepository.save(goal));
        log.info("更新用户目标成功，userId={}, goalId={}", user.getId(), goalId);
        return toResponse(saved);
    }

    public UserGoalResponse updateGoalStatus(String userId, String goalId, GoalStatus status) {
        User user = userService.resolveUserOrThrow(userId);

        UserGoal goal = userGoalRepository.findById(goalId)
                .filter(item -> item.getUserId().equals(user.getId()))
                .orElseThrow(() -> new UserGoalNotFoundException("未找到目标，goalId=" + goalId));

        GoalStatus previousStatus = goal.getStatus();

        if (status == GoalStatus.ACTIVE) {
            userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(user.getId(), GoalStatus.ACTIVE)
                    .filter(activeGoal -> !activeGoal.getId().equals(goalId))
                    .ifPresent(activeGoal -> {
                        activeGoal.setStatus(GoalStatus.PAUSED);
                        userGoalRepository.save(activeGoal);
                    });
        }

        goal.setStatus(status);
        UserGoal saved = userGoalRepository.save(goal);
        if (status == GoalStatus.ACTIVE) {
            saved = refreshGoalCompletion(user, saved);
        } else if (status == GoalStatus.COMPLETED && previousStatus != GoalStatus.COMPLETED) {
            sendGoalCompletedNotification(user, saved, loadActivities(user));
        }

        log.info("更新目标状态成功，userId={}, goalId={}, status={}", user.getId(), goalId, status);
        return toResponse(saved);
    }

    private void refreshAutomaticCompletion(User user) {
        List<UserGoal> activeGoals = userGoalRepository.findByUserIdAndStatus(user.getId(), GoalStatus.ACTIVE);
        if (activeGoals.isEmpty()) {
            return;
        }

        try {
            List<ActivityHistoryClient.ActivitySnapshot> activities = loadActivities(user);
            activeGoals.forEach(goal -> refreshGoalCompletion(user, goal, activities));
        } catch (Exception e) {
            log.warn("刷新目标自动完成状态失败，userId={}", user.getId(), e);
        }
    }

    private UserGoal refreshGoalCompletion(User user, UserGoal goal) {
        try {
            return refreshGoalCompletion(user, goal, loadActivities(user));
        } catch (Exception e) {
            log.warn("保存后刷新目标完成状态失败，goalId={}", goal.getId(), e);
            return goal;
        }
    }

    private UserGoal refreshGoalCompletion(User user, UserGoal goal, List<ActivityHistoryClient.ActivitySnapshot> activities) {
        if (!shouldAutoComplete(goal, activities)) {
            return goal;
        }

        goal.setStatus(GoalStatus.COMPLETED);
        UserGoal saved = userGoalRepository.save(goal);
        log.info("系统已自动将目标标记为已完成，userId={}, goalId={}", goal.getUserId(), goal.getId());
        sendGoalCompletedNotification(user, saved, activities);
        return saved;
    }

    private boolean shouldAutoComplete(UserGoal goal, List<ActivityHistoryClient.ActivitySnapshot> activities) {
        if (goal == null || goal.getStatus() != GoalStatus.ACTIVE) {
            return false;
        }

        boolean hasFrequencyTarget = goal.getWeeklyTargetFrequency() != null && goal.getWeeklyTargetFrequency() > 0;
        boolean hasDurationTarget = goal.getWeeklyTargetDuration() != null && goal.getWeeklyTargetDuration() > 0;
        if (!hasFrequencyTarget && !hasDurationTarget) {
            return false;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(AUTO_COMPLETE_WINDOW_DAYS);
        List<ActivityHistoryClient.ActivitySnapshot> recentActivities = activities == null ? List.of() : activities.stream()
                .filter(activity -> activityTime(activity) != null)
                .filter(activity -> !activityTime(activity).isBefore(cutoff))
                .toList();

        boolean frequencyMet = !hasFrequencyTarget || recentActivities.size() >= goal.getWeeklyTargetFrequency();
        boolean durationMet = !hasDurationTarget || recentActivities.stream()
                .mapToInt(activity -> activity.getDuration() == null ? 0 : activity.getDuration())
                .sum() >= goal.getWeeklyTargetDuration();

        return frequencyMet && durationMet;
    }

    private void sendGoalCompletedNotification(
            User user,
            UserGoal goal,
            List<ActivityHistoryClient.ActivitySnapshot> activities) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.debug("Skip goal completed email because user email is empty, userId={}, goalId={}", user.getId(), goal.getId());
            return;
        }
        try {
            ActivityHistoryClient.ActivitySnapshot latestActivity = latestActivity(activities);
            goalNotificationClient.sendGoalCompletedEmail(GoalNotificationClient.GoalCompletedNotificationRequest.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .username(resolveUsername(user))
                    .goalId(goal.getId())
                    .goalName(resolveGoalName(goal.getGoalType()))
                    .goalType(goal.getGoalType() == null ? null : goal.getGoalType().name())
                    .completedAt(LocalDateTime.now().toString())
                    .goalPeriod(resolveGoalPeriod(goal))
                    .completionDescription("目标进度已达到 100%，系统已判定本阶段目标完成。")
                    .nextStepAdvice(buildGoalSuggestion(goal, latestActivity))
                    .distance(extractDoubleMetric(latestActivity, "距离（米）", "distance", "distanceInMeters"))
                    .durationSeconds(latestActivity == null || latestActivity.getDuration() == null
                            ? null
                            : latestActivity.getDuration() * 60)
                    .avgHeartRate(extractIntegerMetric(latestActivity, "平均心率", "avgHeartRate", "averageHeartRateInBeatsPerMinute"))
                    .calories(latestActivity == null || latestActivity.getCalorieBurned() == null
                            ? null
                            : latestActivity.getCalorieBurned().doubleValue())
                    .aiSuggestion(buildGoalSuggestion(goal, latestActivity))
                    .build());
        } catch (Exception ex) {
            log.warn("发送目标完成邮件失败，userId={}, goalId={}", user.getId(), goal.getId(), ex);
        }
    }

    private ActivityHistoryClient.ActivitySnapshot latestActivity(List<ActivityHistoryClient.ActivitySnapshot> activities) {
        if (activities == null || activities.isEmpty()) {
            return null;
        }
        return activities.stream()
                .filter(Objects::nonNull)
                .max((left, right) -> {
                    LocalDateTime leftTime = activityTime(left);
                    LocalDateTime rightTime = activityTime(right);
                    if (leftTime == null && rightTime == null) return 0;
                    if (leftTime == null) return -1;
                    if (rightTime == null) return 1;
                    return leftTime.compareTo(rightTime);
                })
                .orElse(null);
    }

    private LocalDateTime activityTime(ActivityHistoryClient.ActivitySnapshot activity) {
        if (activity == null) {
            return null;
        }
        return activity.getStartTime() != null ? activity.getStartTime() : activity.getCreatedAt();
    }

    private List<ActivityHistoryClient.ActivitySnapshot> loadActivities(User user) {
        return activityHistoryClient.getUserActivities(Stream.of(user.getId(), user.getKeycloakId())
                .filter(Objects::nonNull)
                .toList());
    }

    private String resolveUsername(User user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName())
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (user.getEmail() != null && user.getEmail().contains("@")) {
            return user.getEmail().substring(0, user.getEmail().indexOf('@'));
        }
        return "用户";
    }

    private String resolveGoalName(GoalType goalType) {
        if (goalType == null) {
            return "本阶段目标";
        }
        return switch (goalType) {
            case FAT_LOSS -> "减脂目标";
            case WEIGHT_LOSS -> "减重目标";
            case MUSCLE_GAIN -> "增肌目标";
            case ENDURANCE -> "耐力提升目标";
            case TEN_K_IMPROVEMENT -> "10 公里成绩提升目标";
            case GENERAL_FITNESS -> "综合体能目标";
            case RECOVERY -> "恢复训练目标";
        };
    }

    private String resolveGoalPeriod(UserGoal goal) {
        String start = goal.getCreatedAt() == null ? "创建时间未知" : goal.getCreatedAt().toLocalDate().toString();
        String end = goal.getTargetDate() == null ? "未设置截止日期" : goal.getTargetDate().toString();
        return start + " 至 " + end;
    }

    private String buildGoalSuggestion(UserGoal goal, ActivityHistoryClient.ActivitySnapshot latestActivity) {
        String type = latestActivity == null ? "" : String.valueOf(latestActivity.getType());
        return switch (goal.getGoalType()) {
            case FAT_LOSS, WEIGHT_LOSS -> "你已经达成阶段目标，建议下一阶段保持稳定有氧频率，并继续控制恢复与饮食节奏。";
            case MUSCLE_GAIN -> "恭喜完成增肌阶段目标，建议下一阶段重点关注力量递进和睡眠恢复。";
            case ENDURANCE, TEN_K_IMPROVEMENT -> "有氧能力正在提升，本次强度完成度较高，建议下一次安排轻松跑或恢复训练。";
            case RECOVERY -> "恢复阶段目标已完成，建议后续逐步恢复常规训练强度，避免一次性加量过快。";
            case GENERAL_FITNESS -> type != null && type.contains("RUN")
                    ? "综合体能目标已完成，建议继续保持跑步与力量训练的组合安排。"
                    : "综合体能目标已完成，建议继续保持有氧与力量训练均衡。";
        };
    }

    private Double extractDoubleMetric(ActivityHistoryClient.ActivitySnapshot activity, String... keys) {
        if (activity == null || activity.getAdditionalMetrics() == null) {
            return null;
        }
        for (String key : keys) {
            Object value = activity.getAdditionalMetrics().get(key);
            if (value instanceof Number number) {
                return normalizeMetricValue(activity, key, number.doubleValue());
            }
            if (value instanceof String text) {
                try {
                    return normalizeMetricValue(activity, key, Double.parseDouble(text));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Double normalizeMetricValue(ActivityHistoryClient.ActivitySnapshot activity, String key, Double value) {
        if (value == null || activity == null || activity.getSource() == null) {
            return value;
        }
        if (!"GARMIN".equalsIgnoreCase(activity.getSource())) {
            return value;
        }
        if ("distanceKm".equals(key)) {
            return value;
        }
        if ("distanceInMeters".equals(key) || "distance_m".equals(key)) {
            return value / 1000.0d;
        }
        if ("distance".equals(key)) {
            return activity.getAdditionalMetrics().containsKey("distanceKm")
                    ? value
                    : value / 1000.0d;
        }
        return value;
    }

    private Integer extractIntegerMetric(ActivityHistoryClient.ActivitySnapshot activity, String... keys) {
        Double number = extractDoubleMetric(activity, keys);
        return number == null ? null : (int) Math.round(number);
    }

    private UserGoalResponse toResponse(UserGoal goal) {
        return UserGoalResponse.builder()
                .id(goal.getId())
                .userId(goal.getUserId())
                .goalType(goal.getGoalType())
                .targetValue(goal.getTargetValue())
                .targetUnit(goal.getTargetUnit())
                .weeklyTargetFrequency(goal.getWeeklyTargetFrequency())
                .weeklyTargetDuration(goal.getWeeklyTargetDuration())
                .targetDate(goal.getTargetDate())
                .experienceLevel(goal.getExperienceLevel())
                .priority(goal.getPriority())
                .status(goal.getStatus())
                .note(goal.getNote())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}
