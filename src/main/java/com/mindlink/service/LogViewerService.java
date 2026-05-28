package com.mindlink.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * 관리자 로그 뷰어 — logs/ 디렉토리 한정 읽기 전용 접근.
 * 보안:
 *  - logs/ 외부 경로 접근 차단 (path traversal 방어)
 *  - 허용 확장자: .log, .log.gz
 *  - 파일 내용은 HTML escape (템플릿 th:text가 처리)
 */
@Service
public class LogViewerService {

    private static final Path LOG_DIR = Paths.get("logs").toAbsolutePath().normalize();
    private static final int MAX_LINES = 5000;
    private static final int DEFAULT_LINES = 300;

    public List<LogFileInfo> listFiles() {
        if (!Files.isDirectory(LOG_DIR)) return List.of();
        try (Stream<Path> stream = Files.list(LOG_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".log") || n.endsWith(".log.gz");
                    })
                    .sorted(Comparator.comparing((Path p) -> {
                        try { return Files.getLastModifiedTime(p); }
                        catch (IOException e) { return null; }
                    }, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(p -> {
                        try {
                            return new LogFileInfo(
                                    p.getFileName().toString(),
                                    Files.size(p),
                                    Files.getLastModifiedTime(p).toMillis()
                            );
                        } catch (IOException e) {
                            return new LogFileInfo(p.getFileName().toString(), 0L, 0L);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 파일 마지막 N줄을 읽는다. 검색어가 있으면 해당 라인만 남긴다.
     */
    public String tail(String fileName, int lines, String query) {
        Path file = resolveSafe(fileName);
        if (file == null || !Files.isRegularFile(file)) {
            return "(파일을 찾을 수 없습니다: " + fileName + ")";
        }
        int n = Math.min(Math.max(lines, 1), MAX_LINES);

        Deque<String> buf = new ArrayDeque<>(n);
        boolean gz = fileName.endsWith(".gz");
        try (var in = gz ? new GZIPInputStream(Files.newInputStream(file))
                         : Files.newInputStream(file);
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            String q = (query == null || query.isBlank()) ? null : query.toLowerCase();
            while ((line = reader.readLine()) != null) {
                if (q != null && !line.toLowerCase().contains(q)) continue;
                if (buf.size() == n) buf.removeFirst();
                buf.addLast(line);
            }
        } catch (IOException e) {
            return "(파일 읽기 실패: " + e.getMessage() + ")";
        }
        return String.join("\n", buf);
    }

    /** logs/ 안쪽 경로인지 검증 후 반환. 외부면 null. */
    private Path resolveSafe(String name) {
        if (name == null || name.isBlank()) return null;
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        Path candidate = LOG_DIR.resolve(name).normalize();
        if (!candidate.startsWith(LOG_DIR)) return null;
        return candidate;
    }

    public int defaultLines() { return DEFAULT_LINES; }
    public int maxLines()     { return MAX_LINES; }

    public static record LogFileInfo(String name, long size, long modifiedMillis) {
        public String sizeReadable() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}
