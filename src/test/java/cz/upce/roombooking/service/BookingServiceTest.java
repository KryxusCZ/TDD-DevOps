package cz.upce.roombooking.service;

import cz.upce.roombooking.domain.*;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.exception.BookingNotFoundException;
import cz.upce.roombooking.exception.BookingValidationException;
import cz.upce.roombooking.exception.UnauthorizedCancellationException;
import cz.upce.roombooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private BookingService bookingService;

    private Room room;
    private User user;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 9, 0);

    @BeforeEach
    void setUp() {
        room = Room.builder().id(1L).name("A101").capacity(10).build();
        user = User.builder().id(1L).username("jan").email("jan@test.cz")
                .password("pass").role(UserRole.USER).build();

        // lenient() — clock stub nemusí být použit v každém testu
        // (některé testy hodí výjimku dříve než kód dojde k LocalDateTime.now(clock))
        lenient().when(clock.instant()).thenReturn(NOW.atZone(ZoneId.systemDefault()).toInstant());
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    // ---------------------------------------------------------------
    // Pravidlo 1: Kolize rezervací
    // ---------------------------------------------------------------

    @Test
    void createBooking_shouldThrow_whenTimeOverlaps() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 11, 0);

        Booking existing = Booking.builder()
                .room(room).user(user)
                .startTime(start).endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> bookingService.createBooking(user, room, start, end))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void createBooking_shouldSucceed_whenNoTimeOverlap() {
        Booking existing = Booking.builder()
                .room(room).user(user)
                .startTime(LocalDateTime.of(2026, 5, 1, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 9, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of(existing));

        LocalDateTime newStart = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime newEnd   = LocalDateTime.of(2026, 5, 1, 11, 0);

        assertDoesNotThrow(() -> bookingService.createBooking(user, room, newStart, newEnd));
    }

    // ---------------------------------------------------------------
    // Pravidlo 2: Nelze rezervovat v minulosti
    // ---------------------------------------------------------------

    @Test
    void createBooking_shouldThrow_whenStartTimeIsInPast() {
        LocalDateTime pastStart = LocalDateTime.of(2026, 5, 1, 8, 0);
        LocalDateTime pastEnd   = LocalDateTime.of(2026, 5, 1, 9, 0);

        assertThatThrownBy(() -> bookingService.createBooking(user, room, pastStart, pastEnd))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("past");
    }

    @Test
    void createBooking_shouldSucceed_whenStartTimeIsInFuture() {
        LocalDateTime futureStart = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime futureEnd   = LocalDateTime.of(2026, 5, 1, 11, 0);

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> bookingService.createBooking(user, room, futureStart, futureEnd));
    }

    // ---------------------------------------------------------------
    // Pravidlo 3: Délka rezervace musí být 30 min – 4 hodiny
    // ---------------------------------------------------------------

    @Test
    void createBooking_shouldThrow_whenDurationTooShort() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 10, 15);

        assertThatThrownBy(() -> bookingService.createBooking(user, room, start, end))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void createBooking_shouldThrow_whenDurationTooLong() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 15, 0);

        assertThatThrownBy(() -> bookingService.createBooking(user, room, start, end))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void createBooking_shouldSucceed_whenDurationIsExactlyMinimum() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 10, 30);

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> bookingService.createBooking(user, room, start, end));
    }

    // ---------------------------------------------------------------
    // Pravidlo 4: Nelze zrušit méně než 2 hodiny před začátkem
    // ---------------------------------------------------------------

    @Test
    void cancelBooking_shouldThrow_whenLessThan2HoursBeforeStart() {
        // NOW = 9:00, rezervace začíná 10:00 — jen 1 hodina, příliš pozdě
        Booking booking = Booking.builder()
                .id(1L).user(user).room(room)
                .startTime(LocalDateTime.of(2026, 5, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 11, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(user, 1L))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("cancel");
    }

    @Test
    void cancelBooking_shouldSucceed_whenMoreThan2HoursBeforeStart() {
        // NOW = 9:00, rezervace začíná 12:00 — 3 hodiny, OK
        Booking booking = Booking.builder()
                .id(1L).user(user).room(room)
                .startTime(LocalDateTime.of(2026, 5, 1, 12, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 13, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertDoesNotThrow(() -> bookingService.cancelBooking(user, 1L));
    }

    // ---------------------------------------------------------------
    // Pravidlo 5: Admin zruší komukoliv, user jen svou vlastní
    // ---------------------------------------------------------------

    @Test
    void cancelBooking_shouldThrow_whenUserCancelsOtherUsersBooking() {
        // otherUser vlastní rezervaci, user (ne admin) se ji pokouší zrušit
        User otherUser = User.builder().id(2L).username("pavel").email("pavel@test.cz")
                .password("pass").role(UserRole.USER).build();

        Booking booking = Booking.builder()
                .id(1L).user(otherUser).room(room)
                .startTime(LocalDateTime.of(2026, 5, 1, 12, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 13, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(user, 1L))
                .isInstanceOf(UnauthorizedCancellationException.class);
    }

    @Test
    void cancelBooking_shouldSucceed_whenUserCancelsOwnBooking() {
        // user ruší svou vlastní rezervaci — OK
        Booking booking = Booking.builder()
                .id(1L).user(user).room(room)
                .startTime(LocalDateTime.of(2026, 5, 1, 12, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 13, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertDoesNotThrow(() -> bookingService.cancelBooking(user, 1L));
    }

    @Test
    void cancelBooking_shouldSucceed_whenAdminCancelsAnyBooking() {
        // admin ruší rezervaci jiného uživatele — musí projít
        User admin = User.builder().id(2L).username("admin").email("admin@test.cz")
                .password("pass").role(UserRole.ADMIN).build();

        User otherUser = User.builder().id(3L).username("pavel").email("pavel@test.cz")
                .password("pass").role(UserRole.USER).build();

        Booking booking = Booking.builder()
                .id(1L).user(otherUser).room(room)
                .startTime(LocalDateTime.of(2026, 5, 1, 12, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 13, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertDoesNotThrow(() -> bookingService.cancelBooking(admin, 1L));
    }

    @Test
    void cancelBooking_shouldThrow_whenBookingNotFound() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(user, 99L))
                .isInstanceOf(BookingNotFoundException.class);
    }
}
