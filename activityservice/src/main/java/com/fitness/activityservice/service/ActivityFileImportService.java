package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.FileImportResponse;
import com.fitness.activityservice.dto.StandardActivityDTO;
import com.fitness.activityservice.model.ActivitySource;
import com.fitness.activityservice.model.ActivityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityFileImportService {

    private static final int MAX_WARNING_COUNT = 20;

    private final ActivityService activityService;

    public FileImportResponse importCsv(String userId, MultipartFile file) {
        validateFile(file, ".csv");

        List<String> warnings = new ArrayList<>();
        int totalRows = 0;
        int importedCount = 0;
        int skippedCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = readFirstDataLine(reader);
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV 文件为空");
            }

            List<String> headers = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);
            if (!hasAnyHeader(headerIndex, "type", "activity_type", "运动类型")) {
                throw new IllegalArgumentException("CSV 缺少运动类型列，支持 type、activity_type 或 运动类型");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (!hasText(line)) {
                    continue;
                }
                totalRows++;
                try {
                    StandardActivityDTO activity = toStandardActivity(
                            userId,
                            file.getOriginalFilename(),
                            totalRows,
                            parseCsvLine(line),
                            headerIndex
                    );
                    activityService.importActivity(activity);
                    importedCount++;
                } catch (Exception ex) {
                    skippedCount++;
                    addWarning(warnings, "第 " + totalRows + " 行跳过：" + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取 CSV 文件失败：" + ex.getMessage(), ex);
        }

        return FileImportResponse.builder()
                .importType("CSV")
                .fileName(file.getOriginalFilename())
                .status("COMPLETED")
                .totalRows(totalRows)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .warnings(warnings)
                .message("CSV 导入完成，已写入统一活动模型")
                .build();
    }

    public FileImportResponse reserveFitImport(String userId, MultipartFile file) {
        validateFile(file, ".fit");
        return FileImportResponse.builder()
                .importType("FIT")
                .fileName(file.getOriginalFilename())
                .status("RESERVED")
                .totalRows(0)
                .importedCount(0)
                .skippedCount(0)
                .warnings(List.of("FIT 上传入口已预留，后续接入 Garmin FIT SDK 或 fitdecode 后可直接解析并写入统一活动模型"))
                .message("FIT 文件导入能力已预留，当前版本暂不解析 FIT 二进制内容")
                .build();
    }

    private StandardActivityDTO toStandardActivity(
            String userId,
            String fileName,
            int rowNumber,
            List<String> values,
            Map<String, Integer> headerIndex) {
        ActivityType type = parseActivityType(read(values, headerIndex, "type", "activity_type", "运动类型"));
        LocalDateTime startTime = parseStartTime(read(values, headerIndex, "start_time", "startTime", "date", "开始时间", "日期"));
        Integer durationSeconds = parseDurationSeconds(
                read(values, headerIndex, "duration_seconds", "durationSeconds", "时长秒"),
                read(values, headerIndex, "duration_min", "duration", "duration_minutes", "时长分钟", "时长")
        );
        Double calories = parseDouble(read(values, headerIndex, "calories", "calorieBurned", "热量", "消耗热量"));
        Double distance = parseDistanceMeters(
                read(values, headerIndex, "distance_m", "distance", "距离米"),
                read(values, headerIndex, "distance_km", "距离公里", "公里")
        );
        Integer avgHeartRate = parseInteger(read(values, headerIndex, "avg_heart_rate", "avgHeartRate", "平均心率"));
        Integer maxHeartRate = parseInteger(read(values, headerIndex, "max_heart_rate", "maxHeartRate", "最高心率"));
        Double avgPace = parseDouble(read(values, headerIndex, "avg_pace", "avgPace", "平均配速"));

        Map<String, Object> rawData = new LinkedHashMap<>();
        headerIndex.forEach((key, index) -> {
            if (index < values.size()) {
                rawData.put(key, values.get(index));
            }
        });
        rawData.put("导入文件", fileName);
        rawData.put("导入行号", rowNumber);

        StandardActivityDTO dto = new StandardActivityDTO();
        dto.setUserId(userId);
        dto.setSource(ActivitySource.CSV_IMPORT);
        dto.setExternalActivityId("CSV-" + stableId(fileName + ":" + rowNumber + ":" + rawData));
        dto.setType(type);
        dto.setDistance(distance);
        dto.setDurationSeconds(durationSeconds);
        dto.setCalories(calories);
        dto.setAvgHeartRate(avgHeartRate);
        dto.setMaxHeartRate(maxHeartRate);
        dto.setAvgPace(avgPace);
        dto.setStartTime(startTime);
        dto.setRawData(rawData);
        return dto;
    }

    private void validateFile(MultipartFile file, String expectedSuffix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (!hasText(fileName) || !fileName.toLowerCase(Locale.ROOT).endsWith(expectedSuffix)) {
            throw new IllegalArgumentException("文件格式不正确，请上传 " + expectedSuffix + " 文件");
        }
    }

    private String readFirstDataLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (hasText(line)) {
                return line;
            }
        }
        return null;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalizeHeader(headers.get(i));
            if (hasText(normalized)) {
                headerIndex.put(normalized, i);
            }
        }
        return headerIndex;
    }

    private boolean hasAnyHeader(Map<String, Integer> headerIndex, String... names) {
        for (String name : names) {
            if (headerIndex.containsKey(normalizeHeader(name))) {
                return true;
            }
        }
        return false;
    }

    private String read(List<String> values, Map<String, Integer> headerIndex, String... names) {
        for (String name : names) {
            Integer index = headerIndex.get(normalizeHeader(name));
            if (index != null && index < values.size() && hasText(values.get(index))) {
                return values.get(index).trim();
            }
        }
        return null;
    }

    private ActivityType parseActivityType(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("运动类型不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        Map<String, ActivityType> aliases = Map.ofEntries(
                Map.entry("UNCATEGORIZED", ActivityType.UNCATEGORIZED),
                Map.entry("UNKNOWN", ActivityType.UNCATEGORIZED),
                Map.entry("未分类", ActivityType.UNCATEGORIZED),
                Map.entry("RUN", ActivityType.RUNNING),
                Map.entry("RUNNING", ActivityType.RUNNING),
                Map.entry("跑步", ActivityType.RUNNING),
                Map.entry("RIDE", ActivityType.CYCLING),
                Map.entry("CYCLING", ActivityType.CYCLING),
                Map.entry("骑行", ActivityType.CYCLING),
                Map.entry("BIKE", ActivityType.CYCLING),
                Map.entry("SWIM", ActivityType.SWIMMING),
                Map.entry("SWIMMING", ActivityType.SWIMMING),
                Map.entry("游泳", ActivityType.SWIMMING),
                Map.entry("STRENGTH", ActivityType.STRENGTH_TRAINING),
                Map.entry("STRENGTH_TRAINING", ActivityType.STRENGTH_TRAINING),
                Map.entry("力量训练", ActivityType.STRENGTH_TRAINING),
                Map.entry("WEIGHT_TRAINING", ActivityType.WEIGHT_TRAINING),
                Map.entry("YOGA", ActivityType.YOGA),
                Map.entry("瑜伽", ActivityType.YOGA),
                Map.entry("HIIT", ActivityType.HIIT),
                Map.entry("CARDIO", ActivityType.CARDIO),
                Map.entry("有氧", ActivityType.CARDIO)
        );
        ActivityType alias = aliases.get(normalized);
        if (alias != null) {
            return alias;
        }
        try {
            return ActivityType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的运动类型：" + value);
        }
    }

    private LocalDateTime parseStartTime(String value) {
        if (!hasText(value)) {
            return LocalDateTime.now();
        }
        String text = value.trim().replace('/', '-');
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-M-d H:mm")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // try date only
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("开始时间格式无法识别：" + value);
        }
    }

    private Integer parseDurationSeconds(String secondsText, String minutesText) {
        Integer seconds = parseInteger(secondsText);
        if (seconds != null && seconds > 0) {
            return seconds;
        }
        Double minutes = parseDouble(minutesText);
        if (minutes != null && minutes > 0) {
            return Math.max(1, (int) Math.round(minutes * 60));
        }
        throw new IllegalArgumentException("时长不能为空，支持 duration_seconds 或 duration_min");
    }

    private Double parseDistanceMeters(String metersText, String kilometersText) {
        Double meters = parseDouble(metersText);
        if (meters != null) {
            return meters;
        }
        Double kilometers = parseDouble(kilometersText);
        return kilometers == null ? null : kilometers * 1000;
    }

    private Integer parseInteger(String value) {
        Double number = parseDouble(value);
        return number == null ? null : (int) Math.round(number);
    }

    private Double parseDouble(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(",", "");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("数值格式不正确：" + value);
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void addWarning(List<String> warnings, String message) {
        if (warnings.size() < MAX_WARNING_COUNT) {
            warnings.add(message);
        }
    }
}
