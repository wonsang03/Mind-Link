package com.mindlink.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking_time(VARCHAR2) 의 LocalTime 직렬화/역직렬화가 NLS 에 의존하지 않고
 * 일관된 "HH:mm" 문자열로 라운드트립되는지 검증한다. (ORA-17132 회귀 방지)
 */
class LocalTimeAttributeConverterTest {

    private final LocalTimeAttributeConverter converter = new LocalTimeAttributeConverter();

    @Test
    void LocalTime_을_HHmm_문자열로_저장한다() {
        assertThat(converter.convertToDatabaseColumn(LocalTime.of(14, 30))).isEqualTo("14:30");
        assertThat(converter.convertToDatabaseColumn(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(converter.convertToDatabaseColumn(LocalTime.MIDNIGHT)).isEqualTo("00:00");
    }

    @Test
    void HHmm_문자열을_LocalTime_으로_읽는다() {
        assertThat(converter.convertToEntityAttribute("14:30")).isEqualTo(LocalTime.of(14, 30));
        assertThat(converter.convertToEntityAttribute("09:05")).isEqualTo(LocalTime.of(9, 5));
    }

    @Test
    void 초가_포함된_기존_데이터도_읽을_수_있다() {
        assertThat(converter.convertToEntityAttribute("14:30:00")).isEqualTo(LocalTime.of(14, 30));
        assertThat(converter.convertToEntityAttribute(" 9:05 ")).isEqualTo(LocalTime.of(9, 5));
    }

    @Test
    void 저장하고_다시_읽으면_원래_시각과_같다() {
        LocalTime original = LocalTime.of(16, 45);
        String stored = converter.convertToDatabaseColumn(original);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(original);
    }

    @Test
    void null_과_공백은_null_로_처리한다() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }
}
