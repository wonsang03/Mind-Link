package com.mindlink.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class LogMaskingConverterTest {

    private final LogMaskingConverter c = new LogMaskingConverter();

    private String mask(String in) throws Exception {
        // protected transform 호출
        Method m = CompositeConverter.class.getDeclaredMethod("transform", Object.class, String.class);
        m.setAccessible(true);
        // ILoggingEvent 미사용 (transform 내부에서 in 만 사용)
        return (String) m.invoke(c, (ILoggingEvent) null, in);
    }

    @Test
    void masksEmail() throws Exception {
        assertEquals("abc***@example.com", mask("abcdefg@example.com"));
        assertEquals("foo***@mindlink.kr 회원", mask("foobar@mindlink.kr 회원"));
    }

    @Test
    void masksKoreanPhone() throws Exception {
        assertEquals("연락처 010-****-****", mask("연락처 010-1234-5678"));
        assertEquals("phone=010-****-****", mask("phone=01012345678"));
    }

    @Test
    void masksRrn() throws Exception {
        assertEquals("주민 900101-1******", mask("주민 900101-1234567"));
    }

    @Test
    void masksBearerToken() throws Exception {
        // Authorization 헤더 키워드와 Bearer 토큰이 둘 다 마스킹된다 (이중 방어)
        assertEquals("Authorization: *** ***",
                mask("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
        // Bearer 단독도 마스킹된다
        assertEquals("token: Bearer ***",
                mask("token: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
    }

    @Test
    void masksOpenAiKey() throws Exception {
        assertTrue(mask("key=sk-abcdefghijklmnopqrstuvwxyz12345").contains("sk-***"));
    }

    @Test
    void masksGeminiKey() throws Exception {
        assertTrue(mask("AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q7").contains("AIza***"));
    }

    @Test
    void masksPasswordField() throws Exception {
        assertEquals("password=*** logged in", mask("password=secret123 logged in"));
        assertEquals("{\"password\":\"***\"}", mask("{\"password\":\"hunter2\"}"));
    }

    @Test
    void masksJSessionId() throws Exception {
        assertEquals("Cookie: JSESSIONID=***",
                mask("Cookie: JSESSIONID=ABCD1234EFGH5678"));
    }

    @Test
    void masksCreditCard() throws Exception {
        assertEquals("card 1234-****-****-7890", mask("card 1234-5678-9012-7890"));
    }

    @Test
    void leavesNormalTextAlone() throws Exception {
        assertEquals("일반 로그 메시지입니다.", mask("일반 로그 메시지입니다."));
    }
}
