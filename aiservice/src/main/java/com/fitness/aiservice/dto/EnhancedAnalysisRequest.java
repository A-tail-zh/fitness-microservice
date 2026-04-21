package com.fitness.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 增强分析请求 — 接收用户综合健身数据，支持多模态文本输入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedAnalysisRequest {

    /** 可选：日报或指定训练记录分析时使用的活动 ID */
    private String activityId;

    /** 必填：用户 ID */
    private String userId;

    /** 报告类型：DAILY（默认）/ WEEKLY / MONTHLY */
    private String reportType;

    /**
     * 可选：用户对当日训练感受的文字描述（多模态输入）
     * 例如："今天腿部很酸，但完成了全程，感觉比上周轻松"
     */
    private String userNote;
}
