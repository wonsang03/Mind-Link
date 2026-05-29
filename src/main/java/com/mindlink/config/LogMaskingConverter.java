package com.mindlink.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * 로그 출력 단계에서 민감정보를 마스킹한다.
 * logback-spring.xml 의 패턴에서 %mask(%m%n%wEx) 형태로 사용한다.
 */
public class LogMaskingConverter extends CompositeConverter<ILoggingEvent> {

    // 이메일: 앞 3자만 노출, 도메인 유지 — abcdef@example.com -> abc***@example.com
    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+-]{1,3})[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    // 한국 휴대전화: 010-1234-5678 또는 01012345678 -> 010-****-****
    private static final Pattern PHONE_KR = Pattern.compile(
            "(01[016789])[-\\s]?\\d{3,4}[-\\s]?\\d{4}");

    // 주민등록번호: 123456-1234567 -> 123456-1******
    private static final Pattern RRN = Pattern.compile(
            "(\\d{6})[-\\s]?([1-4])\\d{6}");

    // Authorization: Bearer xxx
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9_\\-.=]{8,}");

    // OpenAI 키 (sk-...) / Gemini 키 (AIza...)
    private static final Pattern OPENAI_KEY = Pattern.compile("sk-[A-Za-z0-9_\\-]{20,}");
    private static final Pattern GEMINI_KEY = Pattern.compile("AIza[A-Za-z0-9_\\-]{30,}");

    // Naver / 일반 API 헤더 형태: X-Naver-Client-Secret: xxx
    private static final Pattern HEADER_SECRET = Pattern.compile(
            "(?i)(X-Naver-Client-Secret|Api[-_]?Key|Authorization)(\\s*[:=]\\s*)[^\\s,;\"']+");

    // 신용카드 16자리
    private static final Pattern CARD = Pattern.compile(
            "\\b(\\d{4})[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?(\\d{4})\\b");

    // password / passwd / pwd = xxx, "password":"xxx"
    private static final Pattern PASSWORD_FIELD = Pattern.compile(
            "(?i)(\"?(?:password|passwd|pwd)\"?\\s*[=:]\\s*\"?)[^\"\\s,;&}]+");

    // JSESSIONID
    private static final Pattern JSESSIONID = Pattern.compile(
            "(?i)(JSESSIONID\\s*=\\s*)[A-Za-z0-9]+");

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (in == null || in.isEmpty()) return in;
        String out = in;
        out = EMAIL.matcher(out).replaceAll("$1***@$2");
        out = PHONE_KR.matcher(out).replaceAll("$1-****-****");
        out = RRN.matcher(out).replaceAll("$1-$2******");
        out = BEARER.matcher(out).replaceAll("$1***");
        out = OPENAI_KEY.matcher(out).replaceAll("sk-***");
        out = GEMINI_KEY.matcher(out).replaceAll("AIza***");
        out = HEADER_SECRET.matcher(out).replaceAll("$1$2***");
        out = CARD.matcher(out).replaceAll("$1-****-****-$2");
        out = PASSWORD_FIELD.matcher(out).replaceAll("$1***");
        out = JSESSIONID.matcher(out).replaceAll("$1***");
        return out;
    }
}
