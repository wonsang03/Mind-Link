package com.mindlink.care;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mindlink.external.OpenAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 종합 보고서 위로 편지 생성 — OpenAI 단일 백엔드.
 * 프롬프트·JSON 응답 파싱·결과 타입을 한 파일에 모은다.
 */
@Service
public class CareLetterService {

    private static final Logger log = LoggerFactory.getLogger(CareLetterService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final OpenAiClient openAi;

    public CareLetterService(OpenAiClient openAi) {
        this.openAi = openAi;
    }

    public boolean isEnabled() {
        return openAi.isConfigured();
    }

    public GenerateResult generateLetter(String snapshotJson, CareReport.RiskLevel riskLevel) {
        if (!isEnabled()) {
            return GenerateResult.failure(FailureKind.DISABLED, "OPENAI_API_KEY 가 .env 에 없습니다.");
        }
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return GenerateResult.failure(FailureKind.EMPTY_INPUT, null);
        }

        String prompt = buildPrompt(snapshotJson, riskLevel);
        OpenAiClient.ChatResult chat = openAi.chat(prompt, 0.55);

        if (!chat.success()) {
            String err = chat.error() != null ? chat.error() : "알 수 없는 오류";
            FailureKind kind = err.contains("429") || err.toLowerCase().contains("quota")
                    ? FailureKind.QUOTA_EXCEEDED
                    : FailureKind.HTTP_ERROR;
            String hint = kind == FailureKind.QUOTA_EXCEEDED
                    ? "OpenAI API 한도 또는 잔액 문제가 있어요. platform.openai.com 에서 확인해 주세요."
                    : "OpenAI API 오류: " + err;
            log.warn("CareLetter failed model={} err={}", openAi.getModel(), err);
            return GenerateResult.failure(kind, hint);
        }

        Optional<LetterDraft> draft = parseResponse(chat.text());
        if (draft.isPresent()) {
            log.info("CareLetter success model={} letterLen={} tokens={}",
                    openAi.getModel(), draft.get().letterBody().length(), chat.totalTokens());
            return GenerateResult.success(draft.get());
        }

        log.warn("CareLetter parse/short model={}", openAi.getModel());
        return GenerateResult.failure(FailureKind.PARSE_ERROR,
                "OpenAI 응답을 JSON 편지 형식으로 해석하지 못했어요.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 프롬프트 + 응답 파싱
    // ─────────────────────────────────────────────────────────────────────

    private static String buildPrompt(String snapshotJson, CareReport.RiskLevel riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("역할: 너는 마음이음(Mind-Link) 서비스의 'AI 정서 케어' 위로 편지 작성자다. ")
          .append("심리 상담사가 정성껏 쓴 손편지 같은 느낌으로, 사용자에게 위로와 자기돌봄을 전한다.\n\n");
        sb.append("입력은 위저드 JSON 스냅샷이다.\n")
          .append("- profile.nickname\n")
          .append("- todayInput (필수·핵심): mood, recentHardship, concern, smallComfort, hopeForward, (선택) oneLineMessage\n")
          .append("- assessments (선택): stress·depression·anxiety — 배열이 비어 있거나 항목이 없으면 자가진단은 언급하지 말고 todayInput 만으로 편지를 쓴다.\n")
          .append("스냅샷에 없는 정보(게시물 수·댓글 수·커뮤니티 활동·도서 추천 이력 등)는 절대 추측·언급하지 말 것.\n\n");
        sb.append("형식:\n")
          .append("- 한국어 장문 편지, 800~1500자 분량 (최소 500자, 최대 1800자)\n")
          .append("- 2인칭은 '당신' 또는 nickname 을 1회만 사용\n")
          .append("- 구조 (단락 사이 빈 줄):\n")
          .append("  (1) 인사 + todayInput 의 구체적 단어를 짚기 (mood, recentHardship, concern, smallComfort, hopeForward 중 여러 개)\n")
          .append("  (2) 공감과 정서 인정 — assessments 가 있을 때만 level 을 '신호'로 부드럽게 언급\n")
          .append("  (3) hopeForward·smallComfort 를 존중하며 실행 가능한 자기돌봄 2~3가지\n")
          .append("  (4) oneLineMessage 가 있으면 그 톤으로 마무리, 없으면 희망·지지로 마무리\n");
        sb.append("- 마크다운·별표·헤더 금지. 순수 텍스트만. 줄바꿈은 \\n.\n");
        sb.append("\n금지:\n")
          .append("- 의료 확정 진단 금지\n")
          .append("- 약물·처방 권유 금지\n")
          .append("- 자해·자살 방법 안내 금지\n")
          .append("- 상투어만 나열 금지 — todayInput 에서 단어 최소 3개를 본문에 녹일 것\n")
          .append("- 게시물·댓글·커뮤니티 통계 언급 금지\n");
        if (riskLevel == CareReport.RiskLevel.CRISIS) {
            sb.append("\n특별 지시: 위기 표현 가능성. 편지 맨 앞에 '24시간 자살예방 1393, 정신건강 위기 1577-0199, 긴급 119' 1단락 후 본문.\n");
        }
        sb.append("\n주제 태그(themes): 핵심 주제 1~3개, 짧은 한국어 명사구.\n");
        sb.append("\n사용자 스냅샷 JSON:\n").append(snapshotJson).append("\n");
        sb.append("\n다음 JSON 형식으로만 응답 (다른 텍스트 없이):\n")
          .append("{\"letterBody\":\"편지 전문\",\"themes\":[\"태그1\",\"태그2\"]}");
        return sb.toString();
    }

    private static Optional<LetterDraft> parseResponse(String rawText) {
        try {
            var node = MAPPER.readTree(extractJson(rawText));
            String letter = node.path("letterBody").asText("").trim();
            if (letter.length() < 200) {
                return Optional.empty();
            }
            List<String> themes = new ArrayList<>();
            ArrayNode arr = node.path("themes").isArray() ? (ArrayNode) node.path("themes") : null;
            if (arr != null) {
                arr.forEach(t -> {
                    String s = t.asText("").trim();
                    if (!s.isBlank() && themes.size() < 5) themes.add(s);
                });
            }
            return Optional.of(new LetterDraft(letter, themes));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String extractJson(String text) {
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        return (s >= 0 && e > s) ? text.substring(s, e + 1) : text;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 결과 타입
    // ─────────────────────────────────────────────────────────────────────

    public enum FailureKind {
        NONE, DISABLED, EMPTY_INPUT, QUOTA_EXCEEDED, HTTP_ERROR, PARSE_ERROR
    }

    public record LetterDraft(String letterBody, List<String> themes) {}

    public record GenerateResult(Optional<LetterDraft> draft, FailureKind failureKind, String userHint) {
        public static GenerateResult success(LetterDraft draft) {
            return new GenerateResult(Optional.of(draft), FailureKind.NONE, null);
        }

        public static GenerateResult failure(FailureKind kind, String userHint) {
            return new GenerateResult(Optional.empty(), kind, userHint);
        }

        public boolean isSuccess() {
            return draft.isPresent();
        }
    }
}
