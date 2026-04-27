package cz.upce.roombooking.repository;

import cz.upce.roombooking.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracni testy JPA repository vrstvy.
 *
 * @SpringBootTest    — nacte cely Spring kontext s H2 databazi (test profil)
 * @ActiveProfiles    — pouzije application-test.properties (H2, zadna PostgreSQL)
 * @Transactional     — kazdy test se automaticky rollbackne => izolace mezi testy
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingRepositoryIntegrationTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomRepository    roomRepository;
    @Autowired private UserRepository    userRepository;

    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        room = roomRepository.save(Room.builder()
                .name("A101").capacity(10).description("Testovaci mistnost").build());
        user = userRepository.save(User.builder()
                .username("testuser").email("test@test.cz")
                .password("pass").role(UserRole.USER).build());
    }

    // --- findByRoomAndStatusNot -------------------------------------------------

    /**
     * Pravidlo 3 (kolize) vola findByRoomAndStatusNot(room, CANCELLED).
     * Overujeme ze zrusene rezervace nejsou zahrnuty do vysledku.
     */
    @Test
    void findByRoomAndStatusNot_excludesCancelledBookings() {
        // Arrange
        LocalDateTime base = LocalDateTime.of(2030, 6, 1, 10, 0);
        bookingRepository.save(Booking.builder().user(user).room(room)
                .startTime(base).endTime(base.plusHours(1))
                .status(BookingStatus.CONFIRMED).build());
        bookingRepository.save(Booking.builder().user(user).room(room)
                .startTime(base.plusHours(2)).endTime(base.plusHours(3))
                .status(BookingStatus.PENDING).build());
        bookingRepository.save(Booking.builder().user(user).room(room)
                .startTime(base.plusHours(4)).endTime(base.plusHours(5))
                .status(BookingStatus.CANCELLED).build());  // tato se nesmi vratit

        // Act
        List<Booking> result = bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(b -> b.getStatus() == BookingStatus.CANCELLED);
    }

    @Test
    void findByRoomAndStatusNot_whenAllCancelled_returnsEmpty() {
        // Arrange
        LocalDateTime base = LocalDateTime.of(2030, 6, 1, 10, 0);
        bookingRepository.save(Booking.builder().user(user).room(room)
                .startTime(base).endTime(base.plusHours(1))
                .status(BookingStatus.CANCELLED).build());

        // Act
        List<Booking> result = bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED);

        // Assert
        assertThat(result).isEmpty();
    }

    // --- findByUser -------------------------------------------------------------

    /**
     * Uzivatel vidi pouze sve rezervace, ne rezervace ostatnich.
     */
    @Test
    void findByUser_returnsOnlyThatUsersBookings() {
        // Arrange
        User other = userRepository.save(User.builder()
                .username("other").email("other@test.cz")
                .password("pass").role(UserRole.USER).build());

        LocalDateTime base = LocalDateTime.of(2030, 6, 1, 10, 0);
        bookingRepository.save(Booking.builder().user(user).room(room)
                .startTime(base).endTime(base.plusHours(1))
                .status(BookingStatus.CONFIRMED).build());
        bookingRepository.save(Booking.builder().user(other).room(room)
                .startTime(base.plusHours(2)).endTime(base.plusHours(3))
                .status(BookingStatus.CONFIRMED).build());  // jiny uzivatel

        // Act
        List<Booking> result = bookingRepository.findByUser(user);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getUsername()).isEqualTo("testuser");
    }

    @Test
    void findByUser_whenNoBookings_returnsEmpty() {
        // Act
        List<Booking> result = bookingRepository.findByUser(user);

        // Assert
        assertThat(result).isEmpty();
    }
}
