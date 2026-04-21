package com.fitness.aiservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 规则引擎阈值配置 — 可通过 application.yml 调整，无需修改代码
 */
@Data
@Component
@ConfigurationProperties(prefix = "fitness.rule-engine")
public class RuleEngineProperties {

    /** 训练负荷判断阈值（分钟） */
    private Load load = new Load();

    /** 恢复状态判断阈值 */
    private Recovery recovery = new Recovery();

    /** 连续性等级判断阈值 */
    private Consistency consistency = new Consistency();

    /** 急慢性负荷比阈值 */
    private AcuteChronic acuteChronic = new AcuteChronic();

    @Data
    public static class Load {
        /** 单次训练时长高负荷阈值（分钟） */
        private int singleSessionHighMinutes = 90;
        /** 单次训练时长中等负荷阈值（分钟） */
        private int singleSessionMediumMinutes = 45;
        /** 7天总时长高负荷阈值（分钟） */
        private int weeklyHighMinutes = 300;
        /** 7天总时长中等负荷阈值（分钟） */
        private int weeklyMediumMinutes = 150;
    }

    @Data
    public static class Recovery {
        /** 触发 DETRAINING_RISK 的最小不活跃天数 */
        private int detrainingInactiveDays = 5;
        /** 触发 DETRAINING_RISK 的最大7天训练次数 */
        private int detrainingMaxWeeklyActivities = 1;
        /** 触发 FATIGUE_RISK 的7天最小总时长（分钟） */
        private int fatigueWeeklyMinutes = 300;
    }

    @Data
    public static class Consistency {
        /** 高连续性所需7天最小训练次数 */
        private int highWeeklyActivities = 4;
        /** 高连续性所需最小连续天数 */
        private int highConsecutiveDays = 3;
        /** 中等连续性所需7天最小训练次数 */
        private int mediumWeeklyActivities = 2;
    }

    @Data
    public static class AcuteChronic {
        /** 过度训练风险高阈值 */
        private double overttrainingHighRatio = 1.5;
        /** 过度训练风险中阈值 */
        private double overtrainingMediumRatio = 1.3;
    }
}
