package com.fitness.activityservice.service;

import com.fitness.activityservice.model.ThirdPartyAccount;
import com.fitness.activityservice.model.ThirdPartyBindStatus;
import com.fitness.activityservice.model.ThirdPartyPlatform;
import com.fitness.activityservice.repository.ThirdPartyAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class GarminAutoSyncScheduler {

    private final ThirdPartyAccountRepository thirdPartyAccountRepository;
    private final GarminSyncService garminSyncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${garmin-auto-sync.enabled:false}")
    private boolean enabled;

    @Value("${garmin-auto-sync.days:3}")
    private int syncDays;

    @Scheduled(
            initialDelayString = "${garmin-auto-sync.initial-delay-ms:60000}",
            fixedDelayString = "${garmin-auto-sync.fixed-delay-ms:1800000}"
    )
    public void syncBoundGarminAccounts() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.info("Garmin 自动同步任务仍在运行，本轮跳过");
            return;
        }

        try {
            int days = Math.max(1, Math.min(90, syncDays));
            List<ThirdPartyAccount> accounts = thirdPartyAccountRepository
                    .findByPlatformAndBindStatus(ThirdPartyPlatform.GARMIN, ThirdPartyBindStatus.BOUND)
                    .stream()
                    .filter(account -> StringUtils.hasText(account.getAccessToken()))
                    .toList();

            if (accounts.isEmpty()) {
                log.debug("没有可自动同步的 Garmin 绑定账号");
                return;
            }

            log.info("开始 Garmin 自动同步，账号数量={}，同步天数={}", accounts.size(), days);
            for (ThirdPartyAccount account : accounts) {
                try {
                    garminSyncService.syncRecentActivities(account, days);
                    log.info("Garmin 自动同步完成，userId={}", account.getUserId());
                } catch (Exception ex) {
                    log.warn("Garmin 自动同步失败，userId={}", account.getUserId(), ex);
                }
            }
        } finally {
            running.set(false);
        }
    }
}
