package cz.upce.roombooking.service;

import cz.upce.roombooking.domain.Booking;
import cz.upce.roombooking.domain.BookingStatus;
import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.domain.User;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.exception.BookingValidationException;
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
    private final Clock clock;  // injektovaný — umožňuje mockování v testech

    public Booking createBooking(User user, Room room, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now(clock);

        // pravidlo 2: nelze rezervovat v minulosti
        if (!start.isAfter(now)) {
            throw new BookingValidationException("Booking start time must not be in the past");
        }

        // pravidlo 3: délka rezervace musí být 30 min – 4 hodiny
        Duration duration = Duration.between(start, end);
        if (duration.toMinutes() < 30) {
            throw new BookingValidationException("Booking duration must be at least 30 minutes");
        }
        if (duration.toHours() > 4) {
            throw new BookingValidationException("Booking duration must not exceed 4 hours");
        }

        // pravidlo 1: kolize — dva intervaly se překrývají pokud A začne před koncem B a B začne před koncem A
        List<Booking> existing = bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED);
        boolean hasConflict = existing.stream()
                .anyMatch(b -> start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));

        if (hasConflict) {
            throw new BookingConflictException("Booking conflict: room is already booked for this time slot");
        }

        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        return bookingRepository.save(booking);
    }
}
