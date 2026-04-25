package cz.upce.roombooking.service;

import cz.upce.roombooking.domain.Booking;
import cz.upce.roombooking.domain.BookingStatus;
import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.domain.User;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;

    public Booking createBooking(User user, Room room, LocalDateTime start, LocalDateTime end) {
        // načteme všechny NEzrušené rezervace pro danou místnost
        List<Booking> existing = bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED);

        // pravidlo 1: kolize — dva intervaly se překrývají pokud A začne před koncem B a zároveň B začne před koncem A
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
