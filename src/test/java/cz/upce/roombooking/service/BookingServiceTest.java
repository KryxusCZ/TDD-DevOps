package cz.upce.roombooking.service;

import cz.upce.roombooking.domain.*;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingService bookingService;

    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        room = Room.builder().id(1L).name("A101").capacity(10).build();
        user = User.builder().id(1L).username("jan").email("jan@test.cz")
                .password("pass").role(UserRole.USER).build();
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
        // Arrange — existující rezervace 8:00–9:00
        Booking existing = Booking.builder()
                .room(room).user(user)
                .startTime(LocalDateTime.of(2026, 5, 1, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 1, 9, 0))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED))
                .thenReturn(List.of(existing));

        // nová rezervace 10:00–11:00 — žádná kolize
        LocalDateTime newStart = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime newEnd   = LocalDateTime.of(2026, 5, 1, 11, 0);

        // Act & Assert — žádná výjimka
        assertDoesNotThrow(() -> bookingService.createBooking(user, room, newStart, newEnd));
    }
}
