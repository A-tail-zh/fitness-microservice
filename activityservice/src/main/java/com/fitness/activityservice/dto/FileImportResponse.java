package com.fitness.activityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileImportResponse {
    private String importType;
    private String fileName;
    private String status;
    private int totalRows;
    private int importedCount;
    private int skippedCount;
    private List<String> warnings;
    private String message;
}
