package com.mindlink.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * bookings.booking_time 은 VARCHAR2(8) 문자열 컬럼이다.
 *
 * <p>LocalTime 을 JPA 기본 TIME 매핑으로 두면, Oracle 의 암묵적 변환(NLS 의존)이
 * 시각이 아니라 java.sql.Time 의 epoch 날짜(1970-01-01)를 NLS_DATE_FORMAT 으로 찍어
 * "70/01/01" 같은 값을 저장한다. 그 결과 읽을 때 java.sql.Time#valueOf 가 깨진다(ORA-17132).
 *
 * <p>따라서 시각을 "HH:mm" 문자열로 명시적으로 직렬화/역직렬화하여 NLS 의존성을 제거한다.
 * 읽기는 초 단위가 포함된 기존 데이터("H:mm:ss")도 허용한다.
 */
@Converter
public class LocalTimeAttributeConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter WRITE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter READ = DateTimeFormatter.ofPattern("H:mm[:ss]");

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null ? null : attribute.format(WRITE);
    }

    @Override
    public LocalTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        return LocalTime.parse(dbData.trim(), READ);
    }
}
