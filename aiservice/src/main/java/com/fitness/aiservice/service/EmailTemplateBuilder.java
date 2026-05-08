package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.EmailNotificationEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailTemplateBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String buildGoalCompletedHtml(EmailNotificationEvent event) {
        Map<String, Object> payload = payload(event);
        return page(
                "目标已完成",
                "【律动】目标已完成：" + text(payload.get("goalName"), "训练目标"),
                """
                <p class="lead">%s，你已经完成了 <strong>%s</strong>。</p>
                %s
                <h3>完成说明</h3>
                <p>%s</p>
                <h3>下一步建议</h3>
                <ul>%s</ul>
                <p class="note">保持节奏，下一阶段可以把目标拆得更具体，让每一次训练都有反馈。</p>
                """.formatted(
                        escape(event.getUsername()),
                        escape(text(payload.get("goalName"), "训练目标")),
                        stats(Map.of(
                                "完成时间", text(payload.get("completedAt"), nowText()),
                                "目标周期", text(payload.get("goalPeriod"), "当前周期"),
                                "目标类型", text(payload.get("goalType"), "训练目标")
                        )),
                        escape(text(payload.get("completionDescription"), "目标进度已达到 100%，系统已判定本阶段目标完成。")),
                        list(text(payload.get("nextStepAdvice"), "设置新的阶段目标，并在接下来 7 天保持稳定训练。"))
                ));
    }

    public String buildAiReportGeneratedHtml(EmailNotificationEvent event) {
        Map<String, Object> payload = payload(event);
        return page(
                "训练分析报告已生成",
                "【律动】你的训练分析报告已生成",
                """
                <p class="lead">%s，你的 <strong>%s</strong> 已生成。</p>
                %s
                <h3>风险提示</h3>
                <ul>%s</ul>
                <h3>恢复建议</h3>
                <p>%s</p>
                <h3>个性化建议</h3>
                <ul>%s</ul>
                <h3>完整报告摘要</h3>
                <p>%s</p>
                <h3>Markdown 报告</h3>
                %s
                """.formatted(
                        escape(event.getUsername()),
                        escape(text(payload.get("analysisType"), "训练分析报告")),
                        stats(Map.of(
                                "分析时间", text(payload.get("analysisTime"), nowText()),
                                "综合评分", text(payload.get("overallScore"), "暂无"),
                                "训练状态", text(payload.get("overallStatus"), "暂无"),
                                "训练概览", text(payload.get("overview"), "已完成训练数据汇总")
                        )),
                        list(payload.get("riskTips")),
                        escape(text(payload.get("recoveryAdvice"), "根据恢复状态安排下一次训练强度。")),
                        list(payload.get("suggestions")),
                        escape(text(payload.get("summary"), "系统已结合训练、恢复与目标数据生成本次分析。")),
                        markdownBlock(text(payload.get("markdownReport"), ""))
                ));
    }

    private String page(String preheader, String title, String body) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;background:#f8fafc;font-family:Inter,-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',Arial,sans-serif;color:#0f172a;">
                  <span style="display:none!important;opacity:0;color:transparent;height:0;width:0;overflow:hidden;">%s</span>
                  <div style="padding:32px 16px;">
                    <div style="max-width:680px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:18px;overflow:hidden;box-shadow:0 18px 50px rgba(15,23,42,.08);">
                      <div style="padding:28px 32px;background:linear-gradient(135deg,#2EC4B6,#22c55e);color:#fff;">
                        <div style="font-size:14px;font-weight:800;letter-spacing:.12em;">律动</div>
                        <h1 style="margin:12px 0 0;font-size:26px;line-height:1.25;">%s</h1>
                      </div>
                      <div style="padding:30px 32px;">
                        %s
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escape(title), escape(preheader), escape(title), inlineStyles(body));
    }

    private String stats(Map<String, String> items) {
        return "<div class=\"stats\">" + items.entrySet().stream()
                .map(entry -> """
                        <div class="stat">
                          <span>%s</span>
                          <strong>%s</strong>
                        </div>
                        """.formatted(escape(entry.getKey()), escape(entry.getValue())))
                .collect(Collectors.joining()) + "</div>";
    }

    private String list(Object value) {
        if (value instanceof Collection<?> collection && !collection.isEmpty()) {
            return collection.stream()
                    .map(item -> "<li>" + escape(String.valueOf(item)) + "</li>")
                    .collect(Collectors.joining());
        }
        return "<li>" + escape(text(value, "暂无")) + "</li>";
    }

    private String markdownBlock(String value) {
        if (value == null || value.isBlank()) {
            return "<p>暂无完整 Markdown 报告。</p>";
        }
        return "<pre style=\"white-space:pre-wrap;margin:0 0 14px;font-size:13px;line-height:1.7;color:#334155;background:#f8fafc;border:1px solid #e2e8f0;border-radius:14px;padding:16px;\">"
                + escape(value)
                + "</pre>";
    }

    private String inlineStyles(String body) {
        return body
                .replace("<p class=\"lead\">", "<p style=\"margin:0 0 22px;font-size:16px;line-height:1.8;color:#334155;\">")
                .replace("<p class=\"note\">", "<p style=\"margin:24px 0 0;font-size:14px;line-height:1.8;color:#64748b;background:#f8fafc;border-radius:12px;padding:14px 16px;\">")
                .replace("<h3>", "<h3 style=\"margin:24px 0 10px;font-size:15px;color:#0f172a;\">")
                .replace("<p>", "<p style=\"margin:0 0 14px;font-size:14px;line-height:1.8;color:#475569;\">")
                .replace("<ul>", "<ul style=\"margin:0 0 14px;padding-left:20px;color:#475569;font-size:14px;line-height:1.8;\">")
                .replace("<div class=\"stats\">", "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin:18px 0 8px;\">")
                .replace("<div class=\"stat\">", "<div style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:14px;padding:14px;\">")
                .replace("<span>", "<span style=\"display:block;font-size:12px;color:#64748b;margin-bottom:6px;\">")
                .replace("<strong>", "<strong style=\"font-size:18px;color:#0f172a;\">");
    }

    private Map<String, Object> payload(EmailNotificationEvent event) {
        return event.getPayload() == null ? Map.of() : event.getPayload();
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
