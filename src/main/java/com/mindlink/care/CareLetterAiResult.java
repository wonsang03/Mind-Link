package com.mindlink.care;

import java.util.List;
import java.util.Optional;

/** AI 종합 보고서 편지 생성 공통 결과 (OpenAI / Gemini) */
public final class CareLetterAiResult {

    private CareLetterAiResult() {}

    public enum FailureKind {
        NONE,
        DISABLED,
        EMPTY_INPUT,
        QUOTA_EXCEEDED,
        HTTP_ERROR,
        PARSE_ERROR,
        TOO_SHORT,
        EXCEPTION
    }

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

    public record LetterDraft(String letterBody, List<String> themes) {}
}
