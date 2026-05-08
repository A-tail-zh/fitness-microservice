package com.fitness.activityservice.service;

import com.fitness.activityservice.client.GarminPythonSyncClient;
import com.fitness.activityservice.dto.GarminBindStatusResponse;
import com.fitness.activityservice.dto.GarminLoginSyncRequest;
import com.fitness.activityservice.model.ThirdPartyAccount;
import com.fitness.activityservice.model.ThirdPartyBindStatus;
import com.fitness.activityservice.model.ThirdPartyPlatform;
import com.fitness.activityservice.repository.ThirdPartyAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GarminAuthService {

    private final ThirdPartyAccountRepository thirdPartyAccountRepository;
    private final UserValidationService userValidationService;
    private final GarminPythonSyncClient garminPythonSyncClient;

    public ThirdPartyAccount loginAndBind(String userIdentifier, GarminLoginSyncRequest request) {
        if (!userValidationService.validateUser(userIdentifier)) {
            throw new IllegalStateException("当前用户不存在，无法发起 Garmin 同步");
        }
        if (!StringUtils.hasText(request.getSessionToken())
                && (!StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getPassword()))) {
            throw new IllegalStateException("请输入 Garmin 邮箱和密码，或填写本地会话令牌");
        }

        GarminPythonSyncClient.GarminSessionResponse session = garminPythonSyncClient.login(
                GarminPythonSyncClient.GarminSessionRequest.builder()
                        .email(trimToNull(request.getEmail()))
                        .password(trimToNull(request.getPassword()))
                        .sessionToken(trimToNull(request.getSessionToken()))
                        .build());

        if (!StringUtils.hasText(session.getSessionToken())) {
            throw new IllegalStateException("Garmin 同步服务未返回可用会话令牌");
        }

        ThirdPartyAccount account = thirdPartyAccountRepository.findByUserIdAndPlatform(userIdentifier, ThirdPartyPlatform.GARMIN)
                .orElseGet(() -> ThirdPartyAccount.builder()
                        .userId(userIdentifier)
                        .platform(ThirdPartyPlatform.GARMIN)
                        .build());
        account.setAccessToken(session.getSessionToken());
        account.setRefreshToken(null);
        account.setTokenExpireTime(null);
        account.setExternalUserId(firstNonBlank(session.getExternalUserId(), session.getDisplayName()));
        account.setOauthState(null);
        account.setBindStatus(ThirdPartyBindStatus.BOUND);
        account.setLastSyncStatus("READY");
        account.setLastSyncMessage(firstNonBlank(session.getMessage(), "Garmin 会话已建立，等待同步活动数据"));
        return thirdPartyAccountRepository.save(account);
    }

    public GarminBindStatusResponse getBindStatus(String userIdentifier) {
        return thirdPartyAccountRepository.findByUserIdAndPlatform(userIdentifier, ThirdPartyPlatform.GARMIN)
                .map(account -> GarminBindStatusResponse.builder()
                        .bound(account.getBindStatus() == ThirdPartyBindStatus.BOUND && StringUtils.hasText(account.getAccessToken()))
                        .sessionStored(StringUtils.hasText(account.getAccessToken()))
                        .bindStatus(account.getBindStatus() == null ? "UNBOUND" : account.getBindStatus().name())
                        .externalUserId(account.getExternalUserId())
                        .tokenExpireTime(account.getTokenExpireTime())
                        .lastSyncAt(account.getLastSyncAt())
                        .lastSyncStatus(account.getLastSyncStatus())
                        .lastSyncMessage(account.getLastSyncMessage())
                        .syncMode("PYTHON_SERVICE")
                        .officialApiReserved(true)
                        .fitImportReserved(true)
                        .build())
                .orElseGet(() -> GarminBindStatusResponse.builder()
                        .bound(false)
                        .sessionStored(false)
                        .bindStatus("UNBOUND")
                        .lastSyncMessage("当前未建立 Garmin 本地同步会话")
                        .syncMode("PYTHON_SERVICE")
                        .officialApiReserved(true)
                        .fitImportReserved(true)
                        .build());
    }

    public void disconnect(String userIdentifier) {
        thirdPartyAccountRepository.findByUserIdAndPlatform(userIdentifier, ThirdPartyPlatform.GARMIN)
                .ifPresent(account -> {
                    if (StringUtils.hasText(account.getAccessToken())) {
                        try {
                            garminPythonSyncClient.deleteSession(account.getAccessToken());
                        } catch (Exception ignored) {
                            // Python 原型服务可能已重启，本地会话文件不存在时不阻塞解绑。
                        }
                    }
                    account.setAccessToken(null);
                    account.setRefreshToken(null);
                    account.setExternalUserId(null);
                    account.setOauthState(null);
                    account.setTokenExpireTime(null);
                    account.setBindStatus(ThirdPartyBindStatus.UNBOUND);
                    account.setLastSyncStatus("UNBOUND");
                    account.setLastSyncMessage("已解除 Garmin 原型同步绑定");
                    thirdPartyAccountRepository.save(account);
                });
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
