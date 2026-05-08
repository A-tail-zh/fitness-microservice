package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.FileImportResponse;
import com.fitness.activityservice.service.ActivityFileImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/activities/import/files")
@RequiredArgsConstructor
public class ActivityFileImportController {

    private final ActivityFileImportService activityFileImportService;

    @PostMapping("/csv")
    public ResponseEntity<FileImportResponse> importCsv(
            @RequestHeader("X-User-ID") String userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(activityFileImportService.importCsv(userId, file));
    }

    @PostMapping("/fit")
    public ResponseEntity<FileImportResponse> importFit(
            @RequestHeader("X-User-ID") String userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(activityFileImportService.reserveFitImport(userId, file));
    }
}
