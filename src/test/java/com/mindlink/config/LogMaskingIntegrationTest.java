package com.mindlink.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * logback-spring.xml 의 conversionRule 등록과 패턴 적용까지 포함해
 * 실제 logger 호출 결과가 마스킹되는지 검증한다.
 */
class LogMaskingIntegrationTest {

    @Test
    void realLoggerOutputIsMasked() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        // 패턴 컨버터 등록 (테스트에서는 logback-spring.xml 없이 동작하므로 수동)
        ctx.putObject("PATTERN_RULE_REGISTRY", new java.util.HashMap<>());
        PatternLayout.defaultConverterMap.put("mask", LogMaskingConverter.class.getName());

        PatternLayout layout = new PatternLayout();
        layout.setContext(ctx);
        layout.setPattern("%mask(%m)");
        layout.start();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        LayoutWrappingEncoder<ch.qos.logback.classic.spi.ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setLayout(layout);
        encoder.setContext(ctx);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setOutputStream(buf);
        appender.setEncoder(encoder);
        appender.start();

        Logger logger = (Logger) LoggerFactory.getLogger("test.logger");
        logger.detachAndStopAllAppenders();
        logger.addAppender((Appender) appender);
        logger.setAdditive(false);

        // 민감정보 다 섞은 한 줄
        logger.info("login user=foobar@mindlink.kr phone=010-1234-5678 password=hunter2 token=sk-abcdefghijklmnopqrstuvwxyz123");

        String out = buf.toString(StandardCharsets.UTF_8);
        // 모두 마스킹되어 있어야 함
        assertFalse(out.contains("foobar@"));
        assertTrue(out.contains("foo***@mindlink.kr"));
        assertFalse(out.contains("010-1234-5678"));
        assertTrue(out.contains("010-****-****"));
        assertFalse(out.contains("hunter2"));
        assertTrue(out.contains("password=***"));
        assertFalse(out.contains("sk-abcdefghijklmnopqrstuvwxyz123"));
        assertTrue(out.contains("sk-***"));
    }
}
