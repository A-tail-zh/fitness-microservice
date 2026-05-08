package com.fitness.activityservice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Slf4j
public class GarminPythonServiceLauncher {

    @Value("${garmin-python-service.launch.enabled:true}")
    private boolean enabled;

    @Value("${garmin-python-service.launch.working-dir:}")
    private String configuredWorkingDir;

    @Value("${garmin-python-service.launch.python-executable:}")
    private String configuredPythonExecutable;

    @Value("${garmin-python-service.launch.app:app:app}")
    private String appReference;

    @Value("${garmin-python-service.launch.host:127.0.0.1}")
    private String host;

    @Value("${garmin-python-service.launch.port:8090}")
    private int port;

    @Value("${garmin-python-service.launch.startup-timeout-seconds:20}")
    private int startupTimeoutSeconds;

    private volatile Process launchedProcess;
    private volatile boolean startedByCurrentApp;

    @EventListener(ApplicationReadyEvent.class)
    public void ensurePythonServiceStarted() {
        if (!enabled) {
            log.info("Garmin Python 同步服务自动启动已关闭");
            return;
        }

        if (isServiceReachable()) {
            log.info("Garmin Python 同步服务已在运行，跳过自动启动，port={}", port);
            return;
        }

        try {
            Path workingDir = resolveWorkingDirectory();
            String pythonExecutable = resolvePythonExecutable(workingDir);
            Path logFile = prepareLogFile(workingDir);
            List<String> command = buildCommand(pythonExecutable);

            log.info("准备自动启动 Garmin Python 同步服务，workingDir={}，command={}", workingDir, command);

            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

            launchedProcess = processBuilder.start();
            startedByCurrentApp = true;

            if (waitUntilReachable(Duration.ofSeconds(Math.max(5, startupTimeoutSeconds)))) {
                log.info("Garmin Python 同步服务启动成功，访问地址=http://{}:{}/docs", host, port);
                return;
            }

            if (launchedProcess != null && !launchedProcess.isAlive()) {
                log.error("Garmin Python 同步服务启动失败，进程已退出。请检查日志：{}", logFile);
            } else {
                log.error("Garmin Python 同步服务在规定时间内未就绪，请检查日志：{}", logFile);
            }
        } catch (Exception ex) {
            log.error("自动启动 Garmin Python 同步服务失败", ex);
        }
    }

    @PreDestroy
    public void stopLaunchedPythonService() {
        if (!startedByCurrentApp || launchedProcess == null || !launchedProcess.isAlive()) {
            return;
        }

        log.info("正在停止当前 activity-service 启动的 Garmin Python 同步服务");
        launchedProcess.destroy();
        try {
            launchedProcess.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            launchedProcess.destroyForcibly();
        }
    }

    private List<String> buildCommand(String pythonExecutable) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("uvicorn");
        command.add(appReference);
        command.add("--host");
        command.add(host);
        command.add("--port");
        command.add(String.valueOf(port));
        return command;
    }

    private Path prepareLogFile(Path workingDir) throws IOException {
        try {
            return createLogFile(workingDir.resolve("logs"));
        } catch (IOException ex) {
            Path fallbackDir = Paths.get(System.getProperty("java.io.tmpdir"), "garmin-sync-python-service-logs");
            Path fallbackLogFile = createLogFile(fallbackDir);
            log.warn("无法写入 Garmin Python 服务目录日志，已改用临时日志文件：{}", fallbackLogFile, ex);
            return fallbackLogFile;
        }
    }

    private Path createLogFile(Path logsDir) throws IOException {
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("garmin-sync-python-service.log");
        if (!Files.exists(logFile)) {
            Files.createFile(logFile);
        }
        return logFile;
    }

    private String resolvePythonExecutable(Path workingDir) {
        if (StringUtils.hasText(configuredPythonExecutable)) {
            return configuredPythonExecutable.trim();
        }

        Path venvPython = workingDir.resolve(".venv")
                .resolve(isWindows() ? "Scripts/python.exe" : "bin/python");
        if (Files.isRegularFile(venvPython)) {
            return venvPython.toAbsolutePath().toString();
        }

        return "python";
    }

    private Path resolveWorkingDirectory() {
        if (StringUtils.hasText(configuredWorkingDir)) {
            Path configured = Paths.get(configuredWorkingDir.trim()).toAbsolutePath().normalize();
            if (Files.isRegularFile(configured.resolve("app.py"))) {
                return configured;
            }
            throw new IllegalStateException("GARMIN_SYNC_PYTHON_WORKDIR 未找到 app.py：" + configured);
        }

        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Optional<Path> discovered = discoverWorkingDirectory(current);
        if (discovered.isPresent()) {
            return discovered.get();
        }

        throw new IllegalStateException("未找到 garmin-sync-python-service 目录，请配置 GARMIN_SYNC_PYTHON_WORKDIR");
    }

    private Optional<Path> discoverWorkingDirectory(Path current) {
        Path cursor = current;
        for (int depth = 0; depth < 6 && cursor != null; depth++) {
            Path candidate = cursor.resolve("garmin-sync-python-service");
            if (Files.isRegularFile(candidate.resolve("app.py"))) {
                return Optional.of(candidate);
            }
            if (Files.isRegularFile(cursor.resolve("app.py"))
                    && "garmin-sync-python-service".equalsIgnoreCase(cursor.getFileName().toString())) {
                return Optional.of(cursor);
            }
            cursor = cursor.getParent();
        }
        return Optional.empty();
    }

    private boolean waitUntilReachable(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (isServiceReachable()) {
                return true;
            }
            if (launchedProcess != null && !launchedProcess.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isServiceReachable();
    }

    private boolean isServiceReachable() {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create("http://127.0.0.1:" + port + "/docs");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception ex) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
