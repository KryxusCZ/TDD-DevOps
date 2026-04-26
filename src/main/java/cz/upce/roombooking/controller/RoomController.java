package cz.upce.roombooking.controller;

import cz.upce.roombooking.domain.Booking;
import cz.upce.roombooking.domain.BookingStatus;
import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.dto.BookingRequest;
import cz.upce.roombooking.repository.BookingRepository;
import cz.upce.roombooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    // presmerovani z root URL na seznam mistnosti
    @GetMapping("/")
    public String home() {
        return "redirect:/rooms";
    }

    // GET /rooms — seznam vsech mistnosti
    @GetMapping("/rooms")
    public String listRooms(Model model) {
        List<Room> rooms = roomRepository.findAll();
        model.addAttribute("rooms", rooms);
        return "rooms/list";
    }

    // GET /rooms/{id}/book — formular pro rezervaci + seznam existujicich rezervaci
    @GetMapping("/rooms/{id}/book")
    public String showBookingForm(@PathVariable Long id, Model model) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));

        // nacteme existujici (nezrusene) rezervace — uzivatel vidi kdy je mistnost obsazena
        List<Booking> existingBookings = bookingRepository
                .findByRoomAndStatusNot(room, BookingStatus.CANCELLED);

        model.addAttribute("room", room);
        model.addAttribute("bookingRequest", new BookingRequest());
        model.addAttribute("existingBookings", existingBookings);
        return "rooms/book";
    }
}
