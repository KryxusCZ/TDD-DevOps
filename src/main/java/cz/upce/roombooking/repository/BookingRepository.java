package cz.upce.roombooking.repository;

import cz.upce.roombooking.domain.Booking;
import cz.upce.roombooking.domain.BookingStatus;
import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // vsechny rezervace dane mistnosti krome zrusenych (pro kontrolu kolize)
    List<Booking> findByRoomAndStatusNot(Room room, BookingStatus status);

    // vsechny rezervace prihlaseneho uzivatele (pro jeho prehled)
    List<Booking> findByUser(User user);
}