package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.GarminBindStatusResponse;
import com.fitness.activityservice.dto.GarminLoginSyncRequest;
import com.fitness.activityservice.dto.GarminManualSyncRequest;
import com.fitness.activityservice.dto.GarminSyncResponse;
import com.fitness.activityservice.model.ThirdPartyAccount;
import com.fitness.activityservice.service.GarminAuthService;
import com.fitness.activityservice.service.GarminSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities/import/garmin")
@RequiredArgsConstructor
public class GarminAuthController {

    private final GarminAuthService garminAuthService;
    private final GarminSyncService garminSyncService;

    @Value("${garmin.sync.default-days:3}")
    private int defaultSyncDays;

    @PostMapping("/login-sync")
    public ResponseEntity<GarminSyncResponse> loginAndSync(
            @RequestHeader("X-User-ID") String userIdentifier,
            @RequestBody GarminLoginSyncRequest request) {
        ThirdPartyAccount account = garminAuthService.loginAndBind(userIdentifier, request);
        int days = request.getDays() == null ? defaultSyncDays : request.getDays();
        return ResponseEntity.ok(garminSyncService.syncRecentActivities(account, days));
    }

    @PostMapping("/sync")
    public ResponseEntity<GarminSyncResponse> syncGarminActivities(
            @RequestHeader("X-User-ID") String userIdentifier,
            @RequestBody(required = false) GarminManualSyncRequest request) {
        int days = request == null || request.getDays() == null ? defaultSyncDays : request.getDays();
        return ResponseEntity.ok(garminSyncService.syncRecentActivities(userIdentifier, days));
    }

    @GetMapping("/status")
    public ResponseEntity<GarminBindStatusResponse> getGarminStatus(
            @RequestHeader("X-User-ID") String userIdentifier) {
        return ResponseEntity.ok(garminAuthService.getBindStatus(userIdentifier));
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnectGarmin(
            @RequestHeader("X-User-ID") String userIdentifier) {
        garminAuthService.disconnect(userIdentifier);
        return ResponseEntity.noContent().build();
    }
}
