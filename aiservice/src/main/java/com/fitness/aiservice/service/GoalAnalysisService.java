package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.GoalAnalysisResult;
import com.fitness.aiservice.dto.RuleAnalysisResult;
import com.fitness.aiservice.dto.UserGoalProfile;
import com.fitness.aiservice.dto.UserHistorySummary;
import com.fitness.aiservice.model.Activity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GoalAnalysisService {

    public GoalAnalysisResult analyze(UserGoalProfile goal,
                                      UserHistorySummary history,
                                      RuleAnalysisResult rule,
                                      List<Activity> recentActivities) {

        if (goal == null) {
            return GoalAnalysisResult.builder()
                    .goalType("NONE")
                    .progressStatus("NO_ACTIVE_GOAL")
                    .alignmentLevel("UNKNOWN")
                    .completionScore(0)
                    .alignmentScore(0)
                    .feasibilityScore(0)
                    .estimatedWeeksToGoal(null)
                    .nextMilestone("先完成一次可执行目标设置")
                    .adjustmentReason("缺少目标信息，无法评估目标可行性")
                    .recommendedGoalRevision("设置一个 8-12 周、每周 3 次左右的阶段目标")
                    .strengths(List.of())
                    .gaps(List.of("当前没有有效训练目标，无法进行目标驱动分析"))
                    .actionSuggestions(List.of("建议先设置明确目标，例如减脂、增肌、耐力提升或10公里提升"))
                    .build();
        }

        String status = goal.getStatus() == null ? "ACTIVE" : goal.getStatus().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(status)) {
            return GoalAnalysisResult.builder()
                    .goalType(goal.getGoalType() == null ? "GENERAL_FITNESS" : goal.getGoalType())
                    .progressStatus("GOAL_COMPLETED")
                    .alignmentLevel("HIGH")
                    .completionScore(100)
                    .alignmentScore(100)
                    .feasibilityScore(100)
                    .estimatedWeeksToGoal(0)
                    .nextMilestone("制定下一阶段目标")
                    .adjustmentReason("当前目标已完成")
                    .recommendedGoalRevision("基于最近 30 天训练表现，提高 10%-15% 的目标要求")
                    .strengths(List.of("该目标已达成，近期训练结果与目标一致"))
                    .gaps(List.of())
                    .actionSuggestions(List.of("建议设定新的阶段性目标，继续保持当前训练节奏"))
                    .build();
        }

        if ("PAUSED".equals(status)) {
            return GoalAnalysisResult.builder()
                    .goalType(goal.getGoalType() == null ? "GENERAL_FITNESS" : goal.getGoalType())
                    .progressStatus("GOAL_PAUSED")
                    .alignmentLevel("MEDIUM")
                    .completionScore(0)
                    .alignmentScore(0)
                    .feasibilityScore(40)
                    .estimatedWeeksToGoal(estimateWeeksToTargetDate(goal))
                    .nextMilestone("恢复目标追踪")
                    .adjustmentReason("目标暂停导致系统无法判断推进速度")
                    .recommendedGoalRevision("恢复目标后先执行 1 周低压力训练，再评估是否加量")
                    .strengths(List.of("当前已存在目标，但目标处于暂停状态"))
                    .gaps(List.of("目标暂停期间，系统不会按进行中目标评估推进度"))
                    .actionSuggestions(List.of("如需恢复目标追踪，请将目标状态改回进行中"))
                    .build();
        }

        if ("ABANDONED".equals(status) || "INACTIVE".equals(status)) {
            return GoalAnalysisResult.builder()
                    .goalType("NONE")
                    .progressStatus("NO_ACTIVE_GOAL")
                    .alignmentLevel("UNKNOWN")
                    .completionScore(0)
                    .alignmentScore(0)
                    .feasibilityScore(0)
                    .estimatedWeeksToGoal(null)
                    .nextMilestone("重新创建可执行目标")
                    .adjustmentReason("最近目标已放弃")
                    .recommendedGoalRevision("根据最近 30 天真实训练量，重新设置一个更保守的阶段目标")
                    .strengths(List.of())
                    .gaps(List.of("最近目标已放弃，当前没有可追踪的训练目标"))
                    .actionSuggestions(List.of("建议重新设置一个可执行的新目标"))
                    .build();
        }

        String goalType = goal.getGoalType() == null ? "GENERAL_FITNESS" : goal.getGoalType();

        return switch (goalType) {
            case "FAT_LOSS", "WEIGHT_LOSS" -> analyzeFatLoss(goal, history, rule);
            case "MUSCLE_GAIN" -> analyzeMuscleGain(goal, history, rule);
            case "ENDURANCE" -> analyzeEndurance(goal, history, rule);
            case "TEN_K_IMPROVEMENT" -> analyzeTenK(goal, history, rule);
            case "RECOVERY" -> analyzeRecovery(goal, history, rule);
            default -> analyzeGeneralFitness(goal, history, rule);
        };
    }

    private GoalAnalysisResult analyzeFatLoss(UserGoalProfile goal,
                                              UserHistorySummary history,
                                              RuleAnalysisResult rule) {
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        int frequencyScore = calculateFrequencyScore(history.getActivitiesLast7Days(), goal.getWeeklyTargetFrequency());
        int durationScore = calculateDurationScore(history.getTotalDurationLast7Days(), goal.getWeeklyTargetDuration());
        int structureScore = calculateStructureScore(history.getActivityTypeDistribution(),
                List.of("RUNNING", "CYCLING", "WALKING", "HIIT", "CARDIO"));
        int recoveryScore = calculateRecoveryScore(rule);

        int totalScore = frequencyScore + durationScore + structureScore + recoveryScore;

        if (history.getActivitiesLast7Days() >= defaultInt(goal.getWeeklyTargetFrequency(), 3)) {
            strengths.add("最近7天训练频率基本达到减脂目标要求");
        } else {
            gaps.add("最近7天训练频率低于减脂目标要求");
            actions.add("下周优先把训练频率提升到每周 " + defaultInt(goal.getWeeklyTargetFrequency(), 3) + " 次");
        }

        if (history.getTotalDurationLast7Days() >= defaultInt(goal.getWeeklyTargetDuration(), 180)) {
            strengths.add("最近7天训练总时长对减脂目标有一定支撑");
        } else {
            gaps.add("最近7天总训练时长偏低，热量消耗积累不足");
            actions.add("将每周训练总时长逐步提升到 " + defaultInt(goal.getWeeklyTargetDuration(), 180) + " 分钟左右");
        }

        if (isCardioDominant(history.getActivityTypeDistribution())) {
            strengths.add("当前训练类型与减脂目标匹配度较高");
        } else {
            gaps.add("当前训练结构与减脂目标匹配度一般");
            actions.add("增加中低强度有氧或间歇训练占比，提升热量消耗效率");
        }

        appendRecoveryAdvice(rule, gaps, actions);

        return buildResult(goal.getGoalType(), totalScore, strengths, gaps, actions);
    }

    private GoalAnalysisResult analyzeMuscleGain(UserGoalProfile goal,
                                                 UserHistorySummary history,
                                                 RuleAnalysisResult rule) {
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        int frequencyScore = calculateFrequencyScore(history.getActivitiesLast7Days(), goal.getWeeklyTargetFrequency());
        int durationScore = calculateDurationScore(history.getTotalDurationLast7Days(), goal.getWeeklyTargetDuration());
        int structureScore = calculateStructureScore(history.getActivityTypeDistribution(),
                List.of("STRENGTH", "WEIGHT_TRAINING", "RESISTANCE"));
        int recoveryScore = calculateRecoveryScore(rule);

        int totalScore = frequencyScore + durationScore + structureScore + recoveryScore;

        if (history.getActivitiesLast7Days() >= defaultInt(goal.getWeeklyTargetFrequency(), 3)) {
            strengths.add("最近7天训练频率对增肌目标基本够用");
        } else {
            gaps.add("训练频率不足，刺激累积不够");
            actions.add("建议每周完成至少 " + defaultInt(goal.getWeeklyTargetFrequency(), 3) + " 次力量训练");
        }

        if (hasStrengthDominance(history.getActivityTypeDistribution())) {
            strengths.add("训练类型与增肌目标匹配度较高");
        } else {
            gaps.add("力量训练占比不足，当前训练结构更偏向一般体能或有氧");
            actions.add("优先增加力量训练比重，减少无目的的低强度有氧堆积");
        }

        if (isRecoveryNeeded(rule)) {
            gaps.add("当前恢复状态一般，可能影响增肌效率");
            actions.add("增加休息日或降低连续高负荷训练，保证肌肉恢复");
        } else {
            strengths.add("当前恢复状态对增肌推进相对有利");
        }

        return buildResult(goal.getGoalType(), totalScore, strengths, gaps, actions);
    }

    private GoalAnalysisResult analyzeEndurance(UserGoalProfile goal,
                                                UserHistorySummary history,
                                                RuleAnalysisResult rule) {
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        int frequencyScore = calculateFrequencyScore(history.getActivitiesLast7Days(), goal.getWeeklyTargetFrequency());
        int durationScore = calculateDurationScore(history.getTotalDurationLast7Days(), goal.getWeeklyTargetDuration());
        int structureScore = calculateStructureScore(history.getActivityTypeDistribution(),
                List.of("RUNNING", "CYCLING", "SWIMMING", "CARDIO"));
        int recoveryScore = calculateRecoveryScore(rule);

        int totalScore = frequencyScore + durationScore + structureScore + recoveryScore;

        if ("IMPROVING".equalsIgnoreCase(history.getTrend())) {
            strengths.add("近期训练趋势在改善，耐力基础正在积累");
        } else {
            gaps.add("训练趋势不够积极，耐力提升的连续性不足");
            actions.add("优先保证每周稳定训练，而不是偶尔集中训练");
        }

        if (history.getConsecutiveActiveDays() >= 3) {
            strengths.add("近期训练连续性较好，对耐力提升有帮助");
        } else {
            gaps.add("训练连续性一般，耐力提升节奏不稳定");
            actions.add("优先建立连续训练节奏，例如隔天训练或固定周计划");
        }

        appendRecoveryAdvice(rule, gaps, actions);

        return buildResult(goal.getGoalType(), totalScore, strengths, gaps, actions);
    }

    private GoalAnalysisResult analyzeTenK(UserGoalProfile goal,
                                           UserHistorySummary history,
                                           RuleAnalysisResult rule) {
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        int frequencyScore = calculateFrequencyScore(history.getActivitiesLast7Days(), goal.getWeeklyTargetFrequency());
        int durationScore = calculateDurationScore(history.getTotalDurationLast7Days(), goal.getWeeklyTargetDuration());
        int structureScore = calculateStructureScore(history.getActivityTypeDistribution(),
                List.of("RUNNING", "INTERVAL", "TEMPO", "CARDIO"));
        int recoveryScore = calculateRecoveryScore(rule);

        int totalScore = frequencyScore + durationScore + structureScore + recoveryScore;

        if (hasRunningDominance(history.getActivityTypeDistribution())) {
            strengths.add("当前训练类型与10公里提升目标具备一定匹配度");
        } else {
            gaps.add("近期训练中跑步专项占比不足，难以有效支撑10公里进步");
            actions.add("增加跑步专项训练占比，例如轻松跑、节奏跑和间歇跑");
        }

        if (history.getActivitiesLast7Days() < defaultInt(goal.getWeeklyTargetFrequency(), 4)) {
            gaps.add("周训练频率不足，10公里能力提升节奏偏慢");
            actions.add("建议把周跑步/专项训练频率提升到 " + defaultInt(goal.getWeeklyTargetFrequency(), 4) + " 次");
        } else {
            strengths.add("近期训练频率对10公里能力提升具备一定支撑");
        }

        appendRecoveryAdvice(rule, gaps, actions);

        return buildResult(goal.getGoalType(), totalScore, strengths, gaps, actions);
    }

    private GoalAnalysisResult analyzeRecovery(UserGoalProfile goal,
                                               UserHistorySummary history,
                                               RuleAnalysisResult rule) {
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        int totalScore;

        if (isRecoveryNeeded(rule)) {
            strengths.add("当前分析已识别到恢复风险，说明系统能有效捕捉训练与恢复的不平衡");
            actions.add("本周训练以恢复和低强度活动为主，避免继续堆叠负荷");
            totalScore = 60;
        } else {
            strengths.add("恢复状态相对稳定");
            totalScore = 75;
        }

        if ("HIGH".equalsIgnoreCase(rule.getTrainingLoadLevel())) {
            gaps.add("近期训练负荷偏高，恢复目标下需要主动降负荷");
            actions.add("安排 1-2 天完全恢复或主动恢复训练");
            totalScore -= 15;
        }

        return buildResult(goal.getGoalType(), Math.max(0, totalScore), strengths, gaps, actions);
    }

    private GoalAnalysisResult analyzeGeneralFitness(UserGoalProfile goal,
                                                     UserHistorySummary history,
                                                     RuleAnalysisResult rule) {
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        int frequencyScore = calculateFrequencyScore(history.getActivitiesLast7Days(), goal.getWeeklyTargetFrequency());
        int durationScore = calculateDurationScore(history.getTotalDurationLast7Days(), goal.getWeeklyTargetDuration());
        int structureScore = 20;
        int recoveryScore = calculateRecoveryScore(rule);

        int totalScore = frequencyScore + durationScore + structureScore + recoveryScore;

        if (history.getActivitiesLast7Days() > 0) {
            strengths.add("近期保持了基本训练活动，符合健康维持型目标的基础要求");
        } else {
            gaps.add("近期训练缺失，不利于健康习惯维持");
            actions.add("先恢复每周 2-3 次规律训练，重建习惯优先于追求强度");
        }

        if (history.getInactiveDaysSinceLastActivity() > 5) {
            gaps.add("距离上次训练间隔偏长，规律性不足");
            actions.add("建议先安排一次低压力训练，重新进入稳定节奏");
        }

        appendRecoveryAdvice(rule, gaps, actions);

        return buildResult(goal.getGoalType(), totalScore, strengths, gaps, actions);
    }

    private GoalAnalysisResult buildResult(String goalType,
                                           int totalScore,
                                           List<String> strengths,
                                           List<String> gaps,
                                           List<String> actions) {

        int completionScore = Math.max(0, Math.min(100, totalScore));
        String progressStatus = mapProgressStatus(completionScore);
        String alignmentLevel = mapAlignmentLevel(completionScore);

        if (strengths.isEmpty()) {
            strengths.add("当前目标推进基础较弱，需从训练连续性开始建立");
        }
        if (actions.isEmpty()) {
            actions.add("保持当前节奏，并持续观察未来 1-2 周的执行情况");
        }

        int feasibilityScore = calculateFeasibilityScore(goalType, completionScore, strengths, gaps);
        int estimatedWeeks = Math.max(1, (int) Math.ceil((100 - completionScore) / 12.0));
        String nextMilestone = buildNextMilestone(goalType, completionScore);
        String adjustmentReason = buildAdjustmentReason(completionScore, gaps);
        String recommendedGoalRevision = buildRecommendedGoalRevision(goalType, completionScore);

        return GoalAnalysisResult.builder()
                .goalType(goalType)
                .progressStatus(progressStatus)
                .alignmentLevel(alignmentLevel)
                .completionScore(completionScore)
                .alignmentScore(completionScore)
                .feasibilityScore(feasibilityScore)
                .estimatedWeeksToGoal(estimatedWeeks)
                .nextMilestone(nextMilestone)
                .adjustmentReason(adjustmentReason)
                .recommendedGoalRevision(recommendedGoalRevision)
                .strengths(strengths)
                .gaps(gaps)
                .actionSuggestions(actions)
                .build();
    }

    private int calculateFeasibilityScore(String goalType, int completionScore, List<String> strengths, List<String> gaps) {
        int score = completionScore;
        if (strengths != null && strengths.size() >= 2) {
            score += 8;
        }
        if (gaps != null && gaps.size() >= 3) {
            score -= 12;
        }
        if ("RECOVERY".equalsIgnoreCase(goalType) && completionScore >= 60) {
            score += 5;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String buildNextMilestone(String goalType, int completionScore) {
        if (completionScore >= 80) {
            return "未来 7 天保持当前节奏，并尝试一次小幅专项提升";
        }
        if (completionScore >= 60) {
            return "未来 7 天补齐目标短板，优先保证目标频率和总时长";
        }
        return switch (goalType == null ? "" : goalType) {
            case "TEN_K_IMPROVEMENT" -> "未来 7 天先完成 2 次轻松跑和 1 次节奏训练";
            case "MUSCLE_GAIN" -> "未来 7 天先完成 3 次力量训练，记录主要动作重量";
            case "FAT_LOSS", "WEIGHT_LOSS" -> "未来 7 天完成 3 次中低强度有氧，累计至少 150 分钟";
            case "RECOVERY" -> "未来 7 天以低强度活动和休息为主，观察疲劳反馈";
            default -> "未来 7 天恢复稳定训练节奏，至少完成 2-3 次训练";
        };
    }

    private String buildAdjustmentReason(int completionScore, List<String> gaps) {
        if (completionScore >= 80) {
            return "目标执行情况较好，暂不需要明显下调目标";
        }
        if (gaps == null || gaps.isEmpty()) {
            return "目标推进一般，需要继续观察训练连续性";
        }
        return "主要限制因素：" + String.join("；", gaps.stream().limit(2).toList());
    }

    private String buildRecommendedGoalRevision(String goalType, int completionScore) {
        if (completionScore >= 80) {
            return "保持当前目标，可将下一阶段目标提高 5%-10%";
        }
        if (completionScore >= 60) {
            return "保留当前目标，把执行周期延长 1-2 周更稳妥";
        }
        return switch (goalType == null ? "" : goalType) {
            case "TEN_K_IMPROVEMENT" -> "先把目标改为稳定完成 5-8 公里训练，再推进 10 公里成绩";
            case "MUSCLE_GAIN" -> "先设置每周 3 次力量训练和动作递进记录，再设置体重或围度目标";
            case "FAT_LOSS", "WEIGHT_LOSS" -> "先设置每周训练总时长和频率目标，再设置更激进的体重目标";
            default -> "建议下调为 8 周内可完成的习惯建立目标";
        };
    }

    private Integer estimateWeeksToTargetDate(UserGoalProfile goal) {
        if (goal == null || goal.getTargetDate() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate());
        return (int) Math.max(0, Math.ceil(days / 7.0));
    }

    private int calculateFrequencyScore(int actual, Integer target) {
        int weeklyTarget = defaultInt(target, 3);
        if (weeklyTarget <= 0) return 0;
        double ratio = Math.min(1.0, actual * 1.0 / weeklyTarget);
        return (int) Math.round(ratio * 30);
    }

    private int calculateDurationScore(int actual, Integer target) {
        int weeklyTarget = defaultInt(target, 180);
        if (weeklyTarget <= 0) return 0;
        double ratio = Math.min(1.0, actual * 1.0 / weeklyTarget);
        return (int) Math.round(ratio * 25);
    }

    private int calculateStructureScore(Map<String, Long> distribution, List<String> preferredTypes) {
        if (distribution == null || distribution.isEmpty()) {
            return 5;
        }
        String topType = distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        return preferredTypes.stream().anyMatch(type -> type.equalsIgnoreCase(topType)) ? 25 : 10;
    }

    private int calculateRecoveryScore(RuleAnalysisResult rule) {
        if (rule == null) return 10;

        int score = 20;

        if ("HIGH".equalsIgnoreCase(rule.getTrainingLoadLevel())) {
            score -= 8;
        }
        if (isRecoveryNeeded(rule)) {
            score -= 8;
        }
        if ("LOW".equalsIgnoreCase(rule.getConsistencyLevel())) {
            score -= 4;
        }

        return Math.max(0, score);
    }

    private void appendRecoveryAdvice(RuleAnalysisResult rule, List<String> gaps, List<String> actions) {
        if (rule == null) {
            return;
        }

        if (isRecoveryNeeded(rule)) {
            gaps.add("当前恢复状态一般，说明训练推进和恢复节奏还不够平衡");
            actions.add("优先控制疲劳，恢复正常后再继续加量或加压");
        }

        if ("HIGH".equalsIgnoreCase(rule.getTrainingLoadLevel())) {
            gaps.add("当前训练负荷偏高，存在影响持续执行的风险");
            actions.add("下次训练建议适当减量，避免连续高负荷");
        }
    }

    private boolean isRecoveryNeeded(RuleAnalysisResult rule) {
        if (rule == null || rule.getRecoveryStatus() == null) {
            return false;
        }
        String recoveryStatus = rule.getRecoveryStatus().toUpperCase(Locale.ROOT);
        return "FATIGUE_RISK".equals(recoveryStatus) || "DETRAINING_RISK".equals(recoveryStatus);
    }

    private boolean isCardioDominant(Map<String, Long> distribution) {
        if (distribution == null || distribution.isEmpty()) return false;
        return distribution.keySet().stream()
                .map(key -> key.toUpperCase(Locale.ROOT))
                .anyMatch(key -> key.contains("RUN") || key.contains("CARDIO") || key.contains("CYCL")
                        || key.contains("WALK") || key.contains("HIIT"));
    }

    private boolean hasStrengthDominance(Map<String, Long> distribution) {
        if (distribution == null || distribution.isEmpty()) return false;
        return distribution.keySet().stream()
                .map(key -> key.toUpperCase(Locale.ROOT))
                .anyMatch(key -> key.contains("STRENGTH") || key.contains("WEIGHT") || key.contains("RESIST"));
    }

    private boolean hasRunningDominance(Map<String, Long> distribution) {
        if (distribution == null || distribution.isEmpty()) return false;
        return distribution.keySet().stream()
                .map(key -> key.toUpperCase(Locale.ROOT))
                .anyMatch(key -> key.contains("RUN") || key.contains("INTERVAL") || key.contains("TEMPO"));
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String mapProgressStatus(int score) {
        if (score >= 80) return "ON_TRACK";
        if (score >= 60) return "SLIGHTLY_OFF_TRACK";
        if (score >= 40) return "OFF_TRACK";
        return "SERIOUSLY_OFF_TRACK";
    }

    private String mapAlignmentLevel(int score) {
        if (score >= 80) return "HIGH";
        if (score >= 60) return "MEDIUM";
        return "LOW";
    }
}
