package cz.upce.roombooking.service;

import cz.upce.roombooking.domain.*;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.exception.BookingValidationException;
import cz.upce.roombooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    // Clock je volatilní závislost — mockujeme ho aby testy byly deterministické
    @Mock
    private Clock clock;

    @InjectMocks
    private BookingService bookingService;

    private Room room;
    private User user;

    // "teď" v testech = pevně daný čas — testy nebudou záviset na systémovém čase
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 9, 0);

    @BeforeEach
    void setUp() {
        room = Room.builder().id(1L).name("A101").capacity(10).build();
        user = User.builder().id(1L).username("jan").email("jan@test.cz")
                .password("pass").role(UserRole.USER).build();

        // nastavíme mock clocku aby vždy vracel náš pevný čas
        when(clock.instant()).thenReturn(NOW.atZone(ZoneId.systemDefault()).toInstant());
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    // ---------------------------------------------------------------
    // Pravidlo 1: Kolize rezervací
    // ---------------------------------------------------------------

    @Test
    void createBooking_shouldThrow_whenTimeOverlaps() {
        // Arrange — v místnosti už existuje rezervace 10:00–11:00
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 11, 0);

        Booking existing = Booking.builder()
                .room(room).user(user)
                .startTime(start).endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of(existing));

        // Act & Assert — nová rezervace na stejný čas musí vyhodit výjimku
        assertThatThrownBy(() -> bookingService.createBooking(user, room, start, end))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void createBooking_shouldSucceed_whenNoTimeOverlap() {
        // Arrange — existující rezervace 8:00–9:00, nová 10:00–11:00
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

        // Act & Assert — žádná výjimka
        assertDoesNotThrow(() -> bookingService.createBooking(user, room, newStart, newEnd));
    }

    // ---------------------------------------------------------------
    // Pravidlo 2: Nelze rezervovat v minulosti
    // ---------------------------------------------------------------

    @Test
    void createBooking_shouldThrow_whenStartTimeIsInPast() {
        // Arrange — "teď" je 9:00, pokus o rezervaci na 8:00 (minulost)
        LocalDateTime pastStart = LocalDateTime.of(2026, 5, 1, 8, 0);
        LocalDateTime pastEnd   = LocalDateTime.of(2026, 5, 1, 9, 0);

        // Act & Assert
        assertThatThrownBy(() -> bookingService.createBooking(user, room, pastStart, pastEnd))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("past");
    }

    @Test
    void createBooking_shouldSucceed_whenStartTimeIsInFuture() {
        // Arrange — "teď" je 9:00, rezervace na 10:00–11:00 (budoucnost)
        LocalDateTime futureStart = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime futureEnd   = LocalDateTime.of(2026, 5, 1, 11, 0);

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of());

        // Act & Assert — žádná výjimka
        assertDoesNotThrow(() -> bookingService.createBooking(user, room, futureStart, futureEnd));
    }

    // ---------------------------------------------------------------
    // Pravidlo 3: Délka rezervace musí být 30 min – 4 hodiny
    // ---------------------------------------------------------------

    @Test
    void createBooking_shouldThrow_whenDurationTooShort() {
        // Arrange — rezervace pouze 15 minut (méně než 30 min)
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 10, 15);

        // Act & Assert
        assertThatThrownBy(() -> bookingService.createBooking(user, room, start, end))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void createBooking_shouldThrow_whenDurationTooLong() {
        // Arrange — rezervace 5 hodin (více než 4 hodiny)
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 15, 0);

        // Act & Assert
        assertThatThrownBy(() -> bookingService.createBooking(user, room, start, end))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void createBooking_shouldSucceed_whenDurationIsExactlyMinimum() {
        // Arrange — přesně 30 minut — hraniční případ
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 10, 30);

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of());

        // Act & Assert — přesně na hranici musí projít
        assertDoesNotThrow(() -> bookingService.createBooking(user, room, start, end));
    }
}
