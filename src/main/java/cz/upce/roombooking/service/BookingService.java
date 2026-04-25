package cz.upce.roombooking.service;

import cz.upce.roombooking.domain.Booking;
import cz.upce.roombooking.domain.BookingStatus;
import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.domain.User;
import cz.upce.roombooking.domain.UserRole;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.exception.BookingNotFoundException;
import cz.upce.roombooking.exception.BookingValidationException;
import cz.upce.roombooking.exception.UnauthorizedCancellationException;
import cz.upce.roombooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    // ---------------------------------------------------------------
    // Veřejné metody
    // ---------------------------------------------------------------

    public Booking createBooking(User user, Room room, LocalDateTime start, LocalDateTime end) {
        // Pravidlo 1 → 2 → 3, pak uložení
        checkNotInPast(start);
        checkDuration(start, end);
        checkNoConflict(room, start, end);

        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        return bookingRepository.save(booking);
    }

    public void cancelBooking(User requestingUser, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        // Pravidlo 4 → 5, pak uložení
        checkCancellationAuthorization(requestingUser, booking);
        checkCancellationTime(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    // ---------------------------------------------------------------
    // Privátní metody — každá = jedno business pravidlo
    // ---------------------------------------------------------------

    // Pravidlo 1: rezervaci nelze vytvořit zpětně v minulosti
    private void checkNotInPast(LocalDateTime start) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!start.isAfter(now)) {
            throw new BookingValidationException("Booking start time must not be in the past");
        }
    }

    // Pravidlo 2: délka rezervace musí být 30 minut až 4 hodiny
    private void checkDuration(LocalDateTime start, LocalDateTime end) {
        Duration duration = Duration.between(start, end);
        if (duration.toMinutes() < 30) {
            throw new BookingValidationException("Booking duration must be at least 30 minutes");
        }
        if (duration.toHours() > 4) {
            throw new BookingValidationException("Booking duration must not exceed 4 hours");
        }
    }

    // Pravidlo 3: místnost nesmí být ve stejný čas obsazená
    private void checkNoConflict(Room room, LocalDateTime start, LocalDateTime end) {
        List<Booking> existing = bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED);
        boolean hasConflict = existing.stream()
                .anyMatch(b -> start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));
        if (hasConflict) {
            throw new BookingConflictException("Booking conflict: room is already booked for this time slot");
        }
    }

    // Pravidlo 4: user smí zrušit jen svou rezervaci, admin komukoliv
    private void checkCancellationAuthorization(User requestingUser, Booking booking) {
        boolean isAdmin = requestingUser.getRole() == UserRole.ADMIN;
        boolean isOwner = booking.getUser().getId().equals(requestingUser.getId());
        if (!isAdmin && !isOwner) {
            throw new UnauthorizedCancellationException("You can only cancel your own bookings");
        }
    }

    // Pravidlo 5: zrušení není možné méně než 2 hodiny před začátkem
    private void checkCancellationTime(Booking booking) {
        LocalDateTime now = LocalDateTime.now(clock);
        Duration timeUntilStart = Duration.between(now, booking.getStartTime());
        if (timeUntilStart.toHours() < 2) {
            throw new BookingValidationException("Cannot cancel booking less than 2 hours before start time");
        }
    }
}
