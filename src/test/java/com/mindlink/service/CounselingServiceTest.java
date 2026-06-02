package com.mindlink.service;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import com.mindlink.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 예약 생성 검증 규칙과 취소 권한(본인만) 로직을 검증한다.
 * 저장소는 경계(boundary)이므로 최소한으로만 mock 하고, 비즈니스 규칙 자체는 실제 코드로 실행한다.
 */
class CounselingServiceTest {

    private BookingRepository bookingRepository;
    private CounselingService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        // naverClient 는 예약 생성/취소 로직에서 사용되지 않으므로 null 로 둔다.
        service = new CounselingService(null, bookingRepository);
    }

    private User userWithId(long id) {
        User u = new User("홍길동", "hong@example.com", "pw");
        ReflectionTestUtils.setField(u, "id", id); // 영속화 없이 식별자 부여
        return u;
    }

    @Test
    void 미래_날짜로_예약하면_REQUESTED_상태로_저장된다() {
        User user = userWithId(1L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(14, 30);
        Booking saved = service.createBooking(
                "행복심리상담센터", "심리상담", "02-123-4567", "서울시 강남구",
                user, "홍길동", "010-1234-5678", "hong@example.com",
                date, time, "개인상담", "첫 방문입니다");

        assertThat(saved.getCenterName()).isEqualTo("행복심리상담센터");
        assertThat(saved.getDate()).isEqualTo(date);
        assertThat(saved.getTime()).isEqualTo(time);
        assertThat(saved.getMemo()).isEqualTo("첫 방문입니다");
        assertThat(saved.getStatus()).isEqualTo(Booking.Status.REQUESTED);
        assertThat(saved.getUser()).isSameAs(user);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void 과거_날짜로_예약하면_예외가_발생하고_저장되지_않는다() {
        User user = userWithId(1L);
        LocalDate past = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service.createBooking(
                "행복심리상담센터", null, null, null,
                user, "홍길동", "010-1234-5678", "hong@example.com",
                past, LocalTime.NOON, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("오늘 이후");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void 상담소_정보가_비어있으면_예외가_발생한다() {
        assertThatThrownBy(() -> service.createBooking(
                "   ", null, null, null,
                userWithId(1L), "홍길동", "010-1234-5678", "hong@example.com",
                LocalDate.now().plusDays(1), LocalTime.NOON, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상담 센터");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void 본인_예약은_취소되어_CANCELLED_상태가_된다() {
        User owner = userWithId(1L);
        Booking booking = bookingOf(owner);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        service.cancelBooking(10L, owner);

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
    }

    @Test
    void 타인의_예약은_취소할_수_없고_상태가_유지된다() {
        User owner = userWithId(1L);
        User stranger = userWithId(2L);
        Booking booking = bookingOf(owner);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(10L, stranger))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("본인");

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.REQUESTED);
    }

    @Test
    void 미배정_예약은_상담사가_수락하면_본인_담당으로_배정된다() {
        User counselor = userWithId(7L);
        Booking booking = bookingOf(userWithId(1L)); // counselor 미배정
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        Booking result = service.acceptBooking(10L, counselor);

        assertThat(result.getCounselor()).isSameAs(counselor);
        assertThat(result.isAssigned()).isTrue();
    }

    @Test
    void 이미_다른_상담사가_담당중인_예약은_수락할_수_없다() {
        User other = userWithId(8L);
        User me = userWithId(7L);
        Booking booking = bookingOf(userWithId(1L));
        booking.assignCounselor(other);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.acceptBooking(10L, me))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다른 상담사");
        assertThat(booking.getCounselor()).isSameAs(other); // 가로채기되지 않음
    }

    @Test
    void 본인_담당_예약의_상태는_변경할_수_있다() {
        User counselor = userWithId(7L);
        Booking booking = bookingOf(userWithId(1L));
        booking.assignCounselor(counselor);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        service.changeStatusByCounselor(10L, counselor, Booking.Status.CONFIRMED);

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CONFIRMED);
    }

    @Test
    void 담당이_아닌_상담사는_예약_상태를_변경할_수_없다() {
        User assigned = userWithId(7L);
        User stranger = userWithId(8L);
        Booking booking = bookingOf(userWithId(1L));
        booking.assignCounselor(assigned);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.changeStatusByCounselor(10L, stranger, Booking.Status.CONFIRMED))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("본인이 담당");
        assertThat(booking.getStatus()).isEqualTo(Booking.Status.REQUESTED);
    }

    private Booking bookingOf(User owner) {
        return new Booking("행복심리상담센터", null, null, null,
                owner, "홍길동", "010-1234-5678", "hong@example.com",
                LocalDate.now().plusDays(1), LocalTime.NOON, "개인상담", null);
    }
}
