package com.mindlink.care;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** AI 종합 보고서(위로 편지) 위저드 — 컨트롤러·서비스 사이 DTO 모음. */
public final class CareReportDtos {

    private CareReportDtos() {}

    /**
     * POST /api/care-reports/generate 요청 본문 — 위저드 한 번에 제출.
     * - mood / recentHardship / concern / smallComfort / hopeForward: 필수 (자유 입력)
     * - oneLineMessage: 선택
     * - assessments: 선택 — 완료한 검사만 (stress/depression/anxiety)
     *   예) { "stress": [0,2,3,1,...], "depression": [...], "anxiety": [...] }
     */
    public record GenerateRequest(
            String mood,
            String recentHardship,
            String concern,
            String smallComfort,
            String hopeForward,
            String oneLineMessage,
            Map<String, List<Integer>> assessments
    ) {
        public GenerateRequest() {
            this(null, null, null, null, null, null, null);
        }
    }

    /** GET /api/care-reports — 내 목록의 한 줄 */
    public record SummaryResponse(
            Long id,
            LocalDateTime createdAt,
            String excerpt,
            String riskLevel,
            List<String> themes
    ) {
        public SummaryResponse {
            if (themes == null) themes = List.of();
        }
    }

    /** GET /api/care-reports/{id} — 편지 상세 */
    public record DetailResponse(
            Long id,
            LocalDateTime createdAt,
            String letterBody,
            String riskLevel,
            List<String> themes
    ) {
        public DetailResponse {
            if (themes == null) themes = List.of();
        }
    }

    /** POST /api/care-reports/generate 의 응답 — 신규 생성된 보고서 */
    public record GenerateResponse(
            Long id,
            LocalDateTime createdAt,
            String letterBody,
            String riskLevel,
            List<String> themes,
            boolean usedFallback
    ) {
        public GenerateResponse {
            if (themes == null) themes = List.of();
        }
    }
}
