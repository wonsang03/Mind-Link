package com.mindlink.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
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
     * 파일 마지막 N줄을 읽어 줄바꿈으로 합친 문자열을 돌려준다. 검색어가 있으면 해당 라인만 남긴다.
     */
    public String tail(String fileName, int lines, String query) {
        Path file = resolveSafe(fileName);
        if (file == null || !Files.isRegularFile(file)) {
            return "(파일을 찾을 수 없습니다: " + fileName + ")";
        }
        try {
            return String.join("\n", readTail(file, fileName.endsWith(".gz"), clamp(lines), query));
        } catch (IOException e) {
            return "(파일 읽기 실패: " + e.getMessage() + ")";
        }
    }

    /**
     * 파일 마지막 N줄을 라인 리스트로 돌려준다. 파일이 없거나 경로가 잘못되면 빈 리스트.
     * (AdminLogController 의 /recent 가 재사용한다.)
     */
    public List<String> tailLines(String fileName, int lines) {
        Path file = resolveSafe(fileName);
        if (file == null || !Files.isRegularFile(file)) return List.of();
        try {
            return new ArrayList<>(readTail(file, fileName.endsWith(".gz"), clamp(lines), null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** logs/ 안쪽으로 안전하게 해석된 경로(없으면 비어 있음). 메타 정보(존재·크기·절대경로) 조회에 사용. */
    public Optional<Path> resolve(String fileName) {
        return Optional.ofNullable(resolveSafe(fileName));
    }

    /** .gz 면 압축 해제하며, 마지막 n줄만 메모리에 유지. 일반 .log 는 파일 끝에서만 읽어 대용량에서도 빠르게. */
    private Deque<String> readTail(Path file, boolean gz, int n, String query) throws IOException {
        if (!gz) {
            return readPlainTailFast(file, n, query);
        }
        Deque<String> buf = new ArrayDeque<>(n);
        try (var in = new GZIPInputStream(Files.newInputStream(file));
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            String q = (query == null || query.isBlank()) ? null : query.toLowerCase();
            while ((line = reader.readLine()) != null) {
                if (q != null && !line.toLowerCase().contains(q)) continue;
                if (buf.size() == n) buf.removeFirst();
                buf.addLast(line);
            }
        }
        return buf;
    }

    /** 대용량 .log — 끝에서 최대 2MB 만 읽고 마지막 n줄 추출 */
    private Deque<String> readPlainTailFast(Path file, int n, String query) throws IOException {
        long size = Files.size(file);
        if (size == 0) return new ArrayDeque<>();

        int readBytes = (int) Math.min(size, 2L * 1024 * 1024);
        byte[] buf = new byte[readBytes];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(size - readBytes);
            raf.readFully(buf);
        }
        String chunk = new String(buf, StandardCharsets.UTF_8);
        if (size > readBytes) {
            int cut = chunk.indexOf('\n');
            if (cut >= 0) chunk = chunk.substring(cut + 1);
        }
        String q = (query == null || query.isBlank()) ? null : query.toLowerCase();
        List<String> lines = Arrays.asList(chunk.split("\\r?\\n", -1));
        Deque<String> out = new ArrayDeque<>(n);
        for (int i = lines.size() - 1; i >= 0 && out.size() < n; i--) {
            String line = lines.get(i);
            if (line.isEmpty() && i == lines.size() - 1) continue;
            if (q != null && !line.toLowerCase().contains(q)) continue;
            out.addFirst(line);
        }
        return out;
    }

    private int clamp(int lines) {
        return Math.min(Math.max(lines, 1), MAX_LINES);
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
