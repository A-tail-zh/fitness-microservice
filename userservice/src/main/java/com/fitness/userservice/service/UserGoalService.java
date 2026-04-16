package com.fitness.userservice.service;

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

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGoalService {

    private final UserGoalRepository userGoalRepository;
    private final UserRepository userRepository;

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

        UserGoal saved = userGoalRepository.save(goal);
        log.info("创建用户目标成功，userId={}, goalId={}, goalType={}", userId, saved.getId(), saved.getGoalType());
        return toResponse(saved);
    }

    public UserGoalResponse getActiveGoal(String userId) {
        ensureUserExists(userId);
        UserGoal goal = userGoalRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, GoalStatus.ACTIVE)
                .orElseThrow(() -> new UserGoalNotFoundException("未找到当前有效目标，userId=" + userId));
        return toResponse(goal);
    }

    public List<UserGoalResponse> getAllGoals(String userId) {
        ensureUserExists(userId);
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

        UserGoal saved = userGoalRepository.save(goal);
        log.info("更新用户目标成功，userId={}, goalId={}", userId, goalId);
        return toResponse(saved);
    }

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
        log.info("更新目标状态成功，userId={}, goalId={}, status={}", userId, goalId, status);
        return toResponse(saved);
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