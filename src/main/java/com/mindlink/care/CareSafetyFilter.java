package com.mindlink.care;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AI 종합 보고서 보안·안전 필터.
 * - 입력 필터: 자해·자살·타해 표현 탐지 → riskLevel=CRISIS, 위험 원문은 AI 로 넘기지 않음
 * - PII 마스킹: 전화번호, 주민번호, 이메일, 주소 등
 * - 비방·욕설 마스킹: 출력 단계와 게시글 발췌 단계에서 사용
 * - 출력 필터: AI 생성 후 의료 진단·자해 조장·약물 처방 표현을 일반 위로 표현으로 교체
 * - BookRecommendationFeature 의 CRISIS_SUPPORT_MESSAGE_PREFIX 와 동일한 24시간 안내 문구를 노출
 */
@Component
public class CareSafetyFilter {

    public static final String CRISIS_SUPPORT_MESSAGE =
            "지금 겪고 있는 감정이 매우 무겁게 느껴질 수 있어요. 혼자만 버티지 마시고, "
                    + "24시간 자살예방 상담전화 1393, 정신건강 위기 상담 1577-0199, "
                    + "긴급 상황은 119에 연락해 도움을 받으시길 권합니다.\n\n";

    private static final List<String> CRISIS_KEYWORDS = List.of(
            "자살", "죽고 싶", "죽고싶", "죽고 싶어", "죽어버리",
            "끝내고 싶", "끝내고싶", "사라지고 싶", "사라지고싶",
            "자해", "극단적 선택", "극단적선택",
            "살기 싫", "살기싫", "목숨", "베어버리", "베고 싶",
            "죽일거", "죽일 거", "해치고 싶", "해치고싶"
    );

    private static final List<String> ELEVATED_KEYWORDS = List.of(
            "우울", "절망", "허무", "허탈", "외로", "외롭", "공황",
            "무기력", "번아웃", "burnout", "depression",
            "불안", "초조", "긴장", "잠 못", "잠을 못",
            "괴로", "지쳐", "지쳤", "막막", "답답", "힘들"
    );

    private static final List<String> PROFANITY = List.of(
            "씨발", "시발", "ㅅㅂ", "병신", "ㅂㅅ", "좆", "지랄", "개새끼",
            "fuck", "shit", "asshole"
    );

    private static final Pattern PHONE = Pattern.compile(
            "0\\d{1,2}[-\\s]?\\d{3,4}[-\\s]?\\d{4}");
    private static final Pattern RRN = Pattern.compile(
            "\\b\\d{6}[-\\s]?[1-4]\\d{6}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ADDRESS_NUMBER = Pattern.compile(
            "\\d+(번지|호|동|가|로|길)");

    private static final List<String> DIAGNOSIS_PATTERNS = List.of(
            "당신은 우울증입니다", "당신은 우울증이에요", "당신은 우울증",
            "당신은 불안장애입니다", "당신은 불안장애",
            "당신은 양극성", "당신은 조현병",
            "확정 진단", "진단할 수 있습니다", "진단을 내립니다"
    );

    private static final List<String> MEDICATION_PATTERNS = List.of(
            "약을 복용", "약물 복용", "처방받으세요", "처방을 받으세요",
            "항우울제", "벤조다이아제핀", "리튬", "복약을"
    );

    /**
     * 입력에서 자해·위기 신호를 감지한다.
     * - 사용자 직접 입력(mood, hardship, currentThought) + 본인 커뮤니티 게시물 발췌도 함께 검사
     */
    public CareReport.RiskLevel detectRiskLevel(String... texts) {
        if (texts == null) return CareReport.RiskLevel.NORMAL;
        String combined = String.join(" ", Arrays.stream(texts)
                .filter(s -> s != null && !s.isBlank())
                .toList())
                .toLowerCase(Locale.ROOT);
        if (combined.isBlank()) return CareReport.RiskLevel.NORMAL;
        for (String k : CRISIS_KEYWORDS) {
            if (combined.contains(k)) return CareReport.RiskLevel.CRISIS;
        }
        for (String k : ELEVATED_KEYWORDS) {
            if (combined.contains(k)) return CareReport.RiskLevel.ELEVATED;
        }
        return CareReport.RiskLevel.NORMAL;
    }

    /** 본문에 자해·위기 표현이 들어있으면 true (AI 에 원문을 넘길지 결정할 때 사용) */
    public boolean containsCrisisExpression(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.toLowerCase(Locale.ROOT);
        for (String k : CRISIS_KEYWORDS) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    /**
     * 사용자 입력을 AI 에 넣기 전에 안전하게 가공한다.
     * - 자해·위기 표현이 있으면 원문을 통째로 [위험 표현 생략] 으로 교체
     * - PII 마스킹은 항상 수행
     * - 너무 긴 입력은 잘라낸다
     */
    public String sanitizeUserInput(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return "";
        String t = raw.replaceAll("[\\r\\n]+", " ").trim();
        if (containsCrisisExpression(t)) {
            return "[위험 표현 포함 — 본문 생략, 상담 안내 우선]";
        }
        t = maskPii(t);
        t = maskProfanity(t);
        if (t.length() > maxLen) t = t.substring(0, maxLen);
        return t;
    }

    /**
     * 본인 게시글 발췌를 AI 에 넣기 전 정제한다.
     * - 타인 실명·연락처 마스킹
     * - 위기 표현 포함 글은 발췌를 통째로 생략
     */
    public String sanitizeExcerpt(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return "";
        if (containsCrisisExpression(raw)) {
            return "[감정적으로 무거운 내용 — 본문 생략]";
        }
        String t = raw.replaceAll("<[^>]*>", " ")
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        t = maskPii(t);
        t = maskProfanity(t);
        if (t.length() > maxLen) t = t.substring(0, maxLen) + "…";
        return t;
    }

    public String maskPii(String text) {
        if (text == null || text.isBlank()) return text;
        String t = RRN.matcher(text).replaceAll("[주민번호]");
        t = PHONE.matcher(t).replaceAll("[연락처]");
        t = EMAIL.matcher(t).replaceAll("[이메일]");
        t = ADDRESS_NUMBER.matcher(t).replaceAll("[주소]");
        return t;
    }

    public String maskProfanity(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text;
        for (String bad : PROFANITY) {
            if (t.toLowerCase(Locale.ROOT).contains(bad.toLowerCase(Locale.ROOT))) {
                t = t.replaceAll("(?i)" + Pattern.quote(bad), "***");
            }
        }
        return t;
    }

    /**
     * AI 가 생성한 편지를 사후 검증한다.
     * - 자해·자살 방법 안내성 표현 발견 → 전체 폐기 신호 (sanitizedLetter 가 null)
     * - 의료 확정 진단·약물 처방 표현 → 부드러운 표현으로 교체
     * - 위기 사용자라면 본문 맨 앞에 CRISIS_SUPPORT_MESSAGE 가 들어가도록 보장
     */
    public LetterReview reviewGeneratedLetter(String letter, CareReport.RiskLevel risk) {
        if (letter == null || letter.isBlank()) {
            return new LetterReview(null, true, "empty");
        }
        String t = letter.trim();

        // 위험: 자해 방법·자살 권유성 표현
        if (containsHarmfulOutput(t)) {
            return new LetterReview(null, true, "harmful");
        }

        // 의료 확정 진단 완화
        for (String p : DIAGNOSIS_PATTERNS) {
            if (t.contains(p)) {
                t = t.replace(p, "지금 마음이 많이 무거우신 것 같아요");
            }
        }
        // 약물 처방 권유 → 자기돌봄 표현
        for (String p : MEDICATION_PATTERNS) {
            if (t.contains(p)) {
                t = t.replace(p, "전문가와 상담하기");
            }
        }

        // 위기 사용자에게 핫라인 안내가 누락된 경우 앞에 부착
        if (risk == CareReport.RiskLevel.CRISIS
                && !t.contains("1393") && !t.contains("1577-0199")) {
            t = CRISIS_SUPPORT_MESSAGE + t;
        }
        return new LetterReview(t, false, "ok");
    }

    private static boolean containsHarmfulOutput(String letter) {
        String lower = letter.toLowerCase(Locale.ROOT);
        String[] harmful = {
                "자살하세요", "자살하는 방법", "자해하세요", "스스로 끝내세요",
                "약을 한 번에", "약을 많이", "투신", "목을 매",
                "죽는 게 낫다", "죽어버리세요"
        };
        for (String h : harmful) {
            if (lower.contains(h.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** AI 출력 검토 결과. sanitizedLetter == null 이면 호출자는 fallback 으로 대체해야 한다. */
    public record LetterReview(String sanitizedLetter, boolean rejected, String code) {}
}
