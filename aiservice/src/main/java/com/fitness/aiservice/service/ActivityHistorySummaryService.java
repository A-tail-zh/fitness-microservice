package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ActivityHistorySummaryService {

    public UserHistorySummary buildSummary(String userId, List<Activity> activities) {
        List<Activity> sortedActivities = activities.stream()
                .filter(activity -> activity.getStartTime() != null)
                .sorted(Comparator.comparing(Activity::getStartTime))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        List<Activity> last7Days = filterByDays(sortedActivities, now, 7);
        List<Activity> last30Days = filterByDays(sortedActivities, now, 30);

        Map<String, Long> distribution = sortedActivities.stream()
                .filter(activity -> activity.getType() != null)
                .collect(Collectors.groupingBy(Activity::getType, Collectors.counting()));

        Activity latestActivity = sortedActivities.isEmpty() ? null : sortedActivities.get(sortedActivities.size() - 1);
        Activity firstActivity = sortedActivities.isEmpty() ? null : sortedActivities.get(0);

        return UserHistorySummary.builder()
                .userId(userId)
                .totalActivities(sortedActivities.size())
                .activitiesLast7Days(last7Days.size())
                .activitiesLast30Days(last30Days.size())
                .totalDurationLast7Days(sumDuration(last7Days))
                .totalDurationLast30Days(sumDuration(last30Days))
                .totalCaloriesLast7Days(sumCalories(last7Days))
                .totalCaloriesLast30Days(sumCalories(last30Days))
                .avgDurationLast7Days(avgDuration(last7Days))
                .avgDurationLast30Days(avgDuration(last30Days))
                .avgCaloriesLast7Days(avgCalories(last7Days))
                .avgCaloriesLast30Days(avgCalories(last30Days))
                .mostFrequentActivityType(determineMostFrequentType(distribution))
                .consecutiveActiveDays(calculateConsecutiveActiveDays(sortedActivities))
                .inactiveDaysSinceLastActivity(calculateInactiveDays(latestActivity, now))
                .trend(determineTrend(last7Days.size(), last30Days.size()))
                .adherenceLevel(determineAdherenceLevel(last7Days.size(), last30Days.size()))
                .firstActivityTime(firstActivity != null ? firstActivity.getStartTime() : null)
                .latestActivityTime(latestActivity != null ? latestActivity.getStartTime() : null)
                .recentActivityTypes(last7Days.stream()
                        .map(Activity::getType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .limit(5)
                        .toList())
                .activityTypeDistribution(distribution)
                .build();
    }

    private List<Activity> filterByDays(List<Activity> activities, LocalDateTime now, int days) {
        LocalDateTime threshold = now.minusDays(days);
        return activities.stream()
                .filter(activity -> activity.getStartTime() != null && !activity.getStartTime().isBefore(threshold))
                .toList();
    }

    private int sumDuration(List<Activity> activities) {
        return activities.stream()
                .map(Activity::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int sumCalories(List<Activity> activities) {
        return activities.stream()
                .map(Activity::getCalorieBurned)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private double avgDuration(List<Activity> activities) {
        return activities.stream()
                .map(Activity::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private double avgCalories(List<Activity> activities) {
        return activities.stream()
                .map(Activity::getCalorieBurned)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private String determineMostFrequentType(Map<String, Long> distribution) {
        return distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }

    private int calculateConsecutiveActiveDays(List<Activity> activities) {
        if (activities.isEmpty()) {
            return 0;
        }

        List<LocalDate> distinctDates = activities.stream()
                .map(Activity::getStartTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        int streak = 0;
        LocalDate cursor = distinctDates.get(0);
        for (LocalDate date : distinctDates) {
            if (date.equals(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateInactiveDays(Activity latestActivity, LocalDateTime now) {
        if (latestActivity == null || latestActivity.getStartTime() == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(latestActivity.getStartTime().toLocalDate(), now.toLocalDate());
    }

    private String determineTrend(int last7DaysCount, int last30DaysCount) {
        double weeklyBaseline = last30DaysCount / 4.0;
        if (last7DaysCount >= weeklyBaseline + 2) {
            return "IMPROVING";
        }
        if (last7DaysCount <= Math.max(0, weeklyBaseline - 2)) {
            return "DECLINING";
        }
        return "STABLE";
    }

    private String determineAdherenceLevel(int last7DaysCount, int last30DaysCount) {
        if (last7DaysCount >= 4 || last30DaysCount >= 16) {
            return "HIGH";
        }
        if (last7DaysCount >= 2 || last30DaysCount >= 8) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
