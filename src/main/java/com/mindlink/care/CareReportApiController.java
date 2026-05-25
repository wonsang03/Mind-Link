package com.mindlink.care;

import com.mindlink.domain.User;
import com.mindlink.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 종합 보고서 REST API.
 * 비로그인: 401 · 타인 보고서 ID: 404 (존재 노출 방지)
 */
@RestController
@RequestMapping("/api/care-reports")
public class CareReportApiController {

    private final CareReportService service;
    private final UserService userService;
    private final CareReportPdfBuilder pdfBuilder;

    public CareReportApiController(CareReportService service,
                                    UserService userService,
                                    CareReportPdfBuilder pdfBuilder) {
        this.service = service;
        this.userService = userService;
        this.pdfBuilder = pdfBuilder;
    }

    @GetMapping
    public ResponseEntity<?> listMine(HttpSession session) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return unauthorized();
        List<CareReportDtos.SummaryResponse> list = service.myList(user.getId()).stream()
                .map(r -> new CareReportDtos.SummaryResponse(
                        r.getId(), r.getCreatedAt(),
                        CareWebSupport.excerpt(r.getLetterBody(), 140),
                        r.getRiskLevel() != null ? r.getRiskLevel().name() : "NORMAL",
                        CareReportService.splitThemes(r.getThemes())))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id, HttpSession session) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return unauthorized();
        Optional<CareReport> opt = service.findMine(user.getId(), id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "보고서를 찾을 수 없습니다."));
        }
        CareReport r = opt.get();
        CareReportDtos.DetailResponse body = new CareReportDtos.DetailResponse(
                r.getId(), r.getCreatedAt(), r.getLetterBody(),
                r.getRiskLevel() != null ? r.getRiskLevel().name() : "NORMAL",
                CareReportService.splitThemes(r.getThemes()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody(required = false) CareReportDtos.GenerateRequest body,
                                       HttpSession session) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return unauthorized();
        try {
            CareReportService.GenerationResult result =
                    service.generate(user, body != null ? body : new CareReportDtos.GenerateRequest());
            CareReport r = result.report();
            CareReportDtos.GenerateResponse resp = new CareReportDtos.GenerateResponse(
                    r.getId(), r.getCreatedAt(), r.getLetterBody(),
                    r.getRiskLevel() != null ? r.getRiskLevel().name() : "NORMAL",
                    result.themes(), result.usedFallback());
            return ResponseEntity.ok(resp);
        } catch (CareReportService.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (CareReportService.RateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return unauthorized();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "보고서 생성 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요."));
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Long id, HttpSession session) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return unauthorized();
        Optional<CareReport> opt = service.findMine(user.getId(), id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "보고서를 찾을 수 없습니다."));
        }
        CareReport r = opt.get();
        byte[] pdf = pdfBuilder.build(r, user.getName());
        String fileName = pdfBuilder.suggestedFileName(r);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded)
                .body(pdf);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "로그인이 필요합니다."));
    }
}
