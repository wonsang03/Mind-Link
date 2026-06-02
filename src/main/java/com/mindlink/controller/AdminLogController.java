package com.mindlink.controller;

import com.mindlink.domain.UserRole;
import com.mindlink.service.LogViewerService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/admin/logs")
public class AdminLogController {

    private final UserService userService;
    private final LogViewerService logViewerService;

    @Value("${logging.file.name:logs/mindlink.log}")
    private String logFilePath;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "admin-log-tail");
        t.setDaemon(true);
        return t;
    });

    private volatile long lastPosition = 0L;
    private volatile boolean started = false;

    public AdminLogController(UserService userService, LogViewerService logViewerService) {
        this.userService = userService;
        this.logViewerService = logViewerService;
    }

    /** 활성 로그 파일명 (예: mindlink.log) */
    private String currentFileName() {
        return Paths.get(logFilePath).getFileName().toString();
    }

    // 로그 파일 목록 (현재 로그 + 롤링 .gz)
    @GetMapping("/files")
    public ResponseEntity<?> files(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다."));
        String current = currentFileName();
        List<Map<String, Object>> files = new ArrayList<>();
        boolean hasCurrent = false;
        for (LogViewerService.LogFileInfo f : logViewerService.listFiles()) {
            boolean isCur = f.name().equals(current);
            if (isCur) hasCurrent = true;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.name());
            m.put("sizeReadable", f.sizeReadable());
            m.put("modifiedMillis", f.modifiedMillis());
            m.put("isCurrent", isCur);
            files.add(m);
        }
        // 활성 로그가 아직 목록에 없으면(방금 생성 등) 항목 추가
        if (!hasCurrent) {
            logViewerService.resolve(current).ifPresent(path -> {
                try {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", current);
                    var info = new LogViewerService.LogFileInfo(current, Files.size(path),
                            Files.getLastModifiedTime(path).toMillis());
                    m.put("sizeReadable", info.sizeReadable());
                    m.put("modifiedMillis", Files.getLastModifiedTime(path).toMillis());
                    m.put("isCurrent", true);
                    files.add(0, m);
                } catch (IOException ignore) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", current);
                    m.put("sizeReadable", "0 B");
                    m.put("modifiedMillis", 0L);
                    m.put("isCurrent", true);
                    files.add(0, m);
                }
            });
        }
        return ResponseEntity.ok(Map.of("files", files, "current", current));
    }

    // 선택 파일 최근 N 라인 조회 (초기 로드 / 파일 전환). file 미지정 시 활성 로그.
    @GetMapping("/recent")
    public ResponseEntity<?> recent(@RequestParam(required = false) String file,
                                    @RequestParam(defaultValue = "300") int lines,
                                    HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다."));
        if (lines <= 0) lines = 300;
        String name = (file == null || file.isBlank()) ? currentFileName() : file;
        try {
            Optional<Path> resolved = logViewerService.resolve(name);
            if (resolved.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "잘못된 로그 파일 경로입니다."));
            }
            Path path = resolved.get();
            if (!Files.exists(path)) {
                return ResponseEntity.ok(Map.of(
                        "lines", List.of(),
                        "file", name,
                        "path", path.toAbsolutePath().toString(),
                        "exists", false
                ));
            }
            List<String> tail = logViewerService.tailLines(name, lines);
            return ResponseEntity.ok(Map.of(
                    "lines", tail,
                    "file", name,
                    "path", path.toAbsolutePath().toString(),
                    "exists", true,
                    "size", Files.size(path)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "로그 파일 읽기 실패: " + e.getMessage()));
        }
    }

    // 실시간 SSE 스트림
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession session) {
        if (!isAdmin(session)) {
            SseEmitter denied = new SseEmitter(0L);
            try {
                denied.send(SseEmitter.event().name("error").data("관리자만 접근할 수 있습니다."));
            } catch (IOException ignore) {}
            denied.complete();
            return denied;
        }

        SseEmitter emitter = new SseEmitter(0L); // 무한 타임아웃
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("ready").data("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        ensureTailStarted();
        return emitter;
    }

    private synchronized void ensureTailStarted() {
        if (started) return;
        started = true;
        try {
            Path path = Paths.get(logFilePath);
            if (Files.exists(path)) {
                lastPosition = Files.size(path);
            }
        } catch (IOException ignore) {}

        scheduler.scheduleWithFixedDelay(this::pollNewLines, 0, 800, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::sendKeepAlive, 25, 25, TimeUnit.SECONDS);
    }

    private void pollNewLines() {
        if (emitters.isEmpty()) return;
        try {
            Path path = Paths.get(logFilePath);
            if (!Files.exists(path)) return;
            long size = Files.size(path);
            if (size < lastPosition) {
                // 파일이 롤링되었거나 truncate 됨
                lastPosition = 0L;
            }
            if (size == lastPosition) return;

            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                raf.seek(lastPosition);
                byte[] buf = new byte[(int) Math.min(size - lastPosition, 1024 * 1024)];
                int read = raf.read(buf);
                if (read > 0) {
                    lastPosition += read;
                    String chunk = new String(buf, 0, read, StandardCharsets.UTF_8);
                    // 부분 라인 처리: 마지막 줄바꿈 이후는 남기기 위해 위치 보정
                    int lastNl = chunk.lastIndexOf('\n');
                    if (lastNl >= 0 && lastNl < chunk.length() - 1) {
                        int leftover = chunk.length() - 1 - lastNl;
                        lastPosition -= leftover;
                        chunk = chunk.substring(0, lastNl + 1);
                    } else if (lastNl < 0) {
                        // 줄바꿈이 없으면 다음 폴링까지 보류
                        lastPosition -= read;
                        return;
                    }
                    broadcast(chunk);
                }
            }
        } catch (IOException ignore) {}
    }

    private void broadcast(String chunk) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name("log").data(chunk));
            } catch (IOException | IllegalStateException e) {
                dead.add(em);
            }
        }
        emitters.removeAll(dead);
    }

    private void sendKeepAlive() {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name("ping").data(System.currentTimeMillis()));
            } catch (IOException | IllegalStateException e) {
                dead.add(em);
            }
        }
        emitters.removeAll(dead);
    }

    private boolean isAdmin(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return false;
        return userService.findById(uid)
                .map(u -> u.getRole() == UserRole.ADMIN)
                .orElse(false);
    }
}
