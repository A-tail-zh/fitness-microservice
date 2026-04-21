package com.fitness.userservice.service;

import com.fitness.userservice.client.ActivityHistoryClient;
import com.fitness.userservice.dto.UserGoalRequest;
import com.fitness.userservice.dto.UserGoalResponse;
import com.fitness.userservice.exception.UserGoalNotFoundException;
import com.fitness.userservice.exception.UserNotFoundException;
import com.fitness.userservice.model.GoalPriority;
import com.fitness.userservice.model.GoalStatus;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserGoal;
import com.fitness.userservice.repository.UserGoalRepository;
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGoalService {

    private static final int AUTO_COMPLETE_WINDOW_DAYS = 7;

    private final ActivityHistoryClient activityHistoryClient;
    private final UserGoalRepository userGoalRepository;
    private final UserRepository userRepository;


    /**
     * 创建用户健身目标
     * <p>
     * 为指定用户创建新的健身目标。该方法执行以下操作：
     * - 验证用户是否存在，不存在则抛出UserNotFoundException；
     * - 检查用户是否有正在进行的目标（ACTIVE状态），如有则将其暂停（PAUSED）；
     * - 创建新的目标记录，设置目标类型、目标值、单位、周频率、周时长、目标日期、经验等级、优先级等信息；
     * - 默认将新目标状态设置为ACTIVE；
     * - 如果未指定优先级，则默认为MEDIUM；
     * - 保存后自动检测目标是否已完成；
     * - 记录创建成功的日志并返回目标信息。
     *
     * @param userId  用户唯一标识符，用于定位创建目标的用户
     * @param request 用户目标请求对象，包含目标的详细信息（目标类型、目标值、单位、频率、时长、日期、经验等级、优先级、备注等）
     * @return UserGoalResponse对象，包含创建成功的目标完整信息
     * @throws UserNotFoundException 当指定的用户ID在数据库中不存在时抛出
     */
    public UserGoalResponse createGoal(String userId, UserGoalRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("未找到用户，id=" + userId));

        userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, GoalStatus.ACTIVE)
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

        UserGoal saved = refreshGoalCompletion(userGoalRepository.save(goal));
        log.info("创建用户目标成功，userId={}, goalId={}, goalType={}", userId, saved.getId(), saved.getGoalType());
        return toResponse(saved);
    }


    /**
     * 获取用户当前活跃的健身目标
     * <p>
     * 查询指定用户当前正在进行的健身目标（ACTIVE状态）。该方法会：
     * - 验证用户是否存在，不存在则抛出UserNotFoundException；
     * - 自动刷新用户所有活跃目标的完成状态；
     * - 查询用户最新的ACTIVE状态目标（按更新时间降序）；
     * - 如果未找到活跃目标则抛出UserGoalNotFoundException；
     * - 将目标对象转换为响应对象返回。
     *
     * @param userId 用户唯一标识符，用于定位要查询的用户
     * @return UserGoalResponse对象，包含用户当前活跃目标的详细信息
     * @throws UserNotFoundException     当指定的用户ID在数据库中不存在时抛出
     * @throws UserGoalNotFoundException 当用户没有任何ACTIVE状态的目标时抛出
     */
    public UserGoalResponse getActiveGoal(String userId) {
        ensureUserExists(userId);
        refreshAutomaticCompletion(userId);
        UserGoal goal = userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, GoalStatus.ACTIVE)
                .orElseThrow(() -> new UserGoalNotFoundException("未找到当前有效目标，userId=" + userId));
        return toResponse(goal);
    }


    public List<UserGoalResponse> getAllGoals(String userId) {
        ensureUserExists(userId);
        refreshAutomaticCompletion(userId);
        return userGoalRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    public UserGoalResponse updateGoal(String userId, String goalId, UserGoalRequest request) {
        ensureUserExists(userId);

        UserGoal goal = userGoalRepository.findById(goalId)
                .filter(item -> item.getUserId().equals(userId))
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

        UserGoal saved = refreshGoalCompletion(userGoalRepository.save(goal));
        log.info("更新用户目标成功，userId={}, goalId={}", userId, goalId);
        return toResponse(saved);
    }


    /**
     * 更新用户健身目标的状态
     * <p>
     * 修改指定健身目标的状态（如ACTIVE、PAUSED、COMPLETED等）。该方法会：
     * - 验证用户是否存在；
     * - 验证目标是否存在且属于该用户；
     * - 如果新状态为ACTIVE，则自动将用户其他ACTIVE状态的目标暂停（确保只有一个活跃目标）；
     * - 更新目标状态并保存到数据库；
     * - 如果新状态为ACTIVE，保存后自动检测目标是否已完成；
     * - 记录状态更新成功的日志并返回更新后的目标信息。
     *
     * @param userId 用户唯一标识符，用于验证目标归属权
     * @param goalId 目标唯一标识符，用于定位要更新状态的目标
     * @param status 新的目标状态，可选值包括ACTIVE、PAUSED、COMPLETED等
     * @return UserGoalResponse对象，包含更新状态后的目标详细信息
     * @throws UserNotFoundException     当指定的用户ID在数据库中不存在时抛出
     * @throws UserGoalNotFoundException 当指定的目标ID不存在或不属于该用户时抛出
     */
    public UserGoalResponse updateGoalStatus(String userId, String goalId, GoalStatus status) {
        ensureUserExists(userId);

        UserGoal goal = userGoalRepository.findById(goalId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new UserGoalNotFoundException("未找到目标，goalId=" + goalId));

        if (status == GoalStatus.ACTIVE) {
            userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, GoalStatus.ACTIVE)
                    .filter(activeGoal -> !activeGoal.getId().equals(goalId))
                    .ifPresent(activeGoal -> {
                        activeGoal.setStatus(GoalStatus.PAUSED);
                        userGoalRepository.save(activeGoal);
                    });
        }

        goal.setStatus(status);
        UserGoal saved = userGoalRepository.save(goal);
        if (status == GoalStatus.ACTIVE) {
            saved = refreshGoalCompletion(saved);
        }
        log.info("更新目标状态成功，userId={}, goalId={}, status={}", userId, goalId, status);
        return toResponse(saved);
    }


    /**
     * 刷新用户所有活跃目标的自动完成状态
     * <p>
     * 批量检查并更新指定用户的所有ACTIVE状态目标。该方法会：
     * - 查询用户所有处于ACTIVE状态的目标；
     * - 如果没有活跃目标则直接返回；
     * - 调用活动历史服务获取用户的活动数据；
     * - 遍历所有活跃目标，逐一判断并更新完成状态；
     * - 如果调用活动历史服务失败，记录警告日志但不抛出异常。
     *
     * @param userId 用户唯一标识符，用于查询该用户下的所有活跃目标
     */
    private void refreshAutomaticCompletion(String userId) {
        List<UserGoal> activeGoals = userGoalRepository.findByUserIdAndStatus(userId, GoalStatus.ACTIVE);
        if (activeGoals.isEmpty()) {
            return;
        }

        try {
            List<ActivityHistoryClient.ActivitySnapshot> activities = activityHistoryClient.getUserActivities(userId);
            activeGoals.forEach(goal -> refreshGoalCompletion(goal, activities));
        } catch (Exception e) {
            log.warn("自动判断目标是否达标失败，userId={}", userId, e);
        }
    }


    /**
     * 刷新单个目标的完成状态
     * <p>
     * 获取用户的历史活动数据并判断指定目标是否应标记为完成。该方法会：
     * - 调用活动历史服务获取用户的活动快照列表；
     * - 委托给重载方法refreshGoalCompletion(goal, activities)进行实际的完成状态判断；
     * - 如果调用活动历史服务失败，记录警告日志并返回原目标（不抛出异常）。
     *
     * @param goal 用户目标对象，包含需要检查完成状态的目标信息
     * @return UserGoal对象，如果满足自动完成条件则返回已更新为COMPLETED状态的目标，否则返回原目标或发生异常时返回原目标
     */
    private UserGoal refreshGoalCompletion(UserGoal goal) {
        try {
            List<ActivityHistoryClient.ActivitySnapshot> activities = activityHistoryClient.getUserActivities(goal.getUserId());
            return refreshGoalCompletion(goal, activities);
        } catch (Exception e) {
            log.warn("创建或更新后自动判断目标失败，goalId={}", goal.getId(), e);
            return goal;
        }
    }


    /**
     * 刷新目标完成状态
     * <p>
     * 根据用户的历史活动数据判断并更新目标的完成状态。该方法会：
     * - 检查目标是否满足自动完成的条件（通过shouldAutoComplete方法判断）；
     * - 如果满足条件，将目标状态更新为COMPLETED；
     * - 保存更新后的目标到数据库；
     * - 记录自动完成日志并返回更新后的目标对象。
     *
     * @param goal       用户目标对象，包含需要检查完成状态的目标信息
     * @param activities 用户历史活动快照列表，用于判断目标是否达成
     * @return UserGoal对象，如果满足自动完成条件则返回已更新为COMPLETED状态的目标，否则返回原目标
     */
    private UserGoal refreshGoalCompletion(UserGoal goal, List<ActivityHistoryClient.ActivitySnapshot> activities) {
        if (!shouldAutoComplete(goal, activities)) {
            return goal;
        }

        goal.setStatus(GoalStatus.COMPLETED);
        UserGoal saved = userGoalRepository.save(goal);
        log.info("系统已自动将目标标记为已完成，userId={}, goalId={}", goal.getUserId(), goal.getId());
        return saved;
    }


    /**
     * 判断目标是否应自动标记为完成
     * <p>
     * 根据用户的历史活动数据判断指定目标是否满足自动完成的条件。该方法会：
     * - 验证目标状态是否为ACTIVE，非活跃状态直接返回false；
     * - 检查目标是否设置了频率或时长指标，两者都没有则返回false；
     * - 截取最近7天（AUTO_COMPLETE_WINDOW_DAYS）内的活动记录；
     * - 分别判断频率指标和时长指标是否都达成；
     * - 只有当所有设定的指标都满足时，才返回true。
     *
     * @param goal       用户目标对象，包含需要判断完成状态的目标信息，必须包含周频率或周时长目标
     * @param activities 用户历史活动快照列表，用于计算近期活动数据
     * @return boolean值，true表示目标应自动标记为完成，false表示不满足自动完成条件
     */
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


    /**
     * 获取活动时间
     * <p>
     * 获取指定活动记录的开始时间或创建时间，用于判断活动是否在指定时间段内。
     *
     * @param activity 活动记录对象，包含开始时间和创建时间
     * @return LocalDateTime对象，表示活动时间，如果开始时间和创建时间都为null则返回null
     */
    private LocalDateTime activityTime(ActivityHistoryClient.ActivitySnapshot activity) {
        if (activity == null) {
            return null;
        }
        return activity.getStartTime() != null ? activity.getStartTime() : activity.getCreatedAt();
    }


    private void ensureUserExists(String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("未找到用户，id=" + userId));
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
