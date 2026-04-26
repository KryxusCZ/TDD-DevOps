package cz.upce.roombooking.controller;

import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.dto.BookingRequest;
import cz.upce.roombooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository roomRepository;

    // GET /rooms — seznam vsech mistnosti
    @GetMapping
    public String listRooms(Model model) {
        List<Room> rooms = roomRepository.findAll();
        model.addAttribute("rooms", rooms);
        return "rooms/list";  // templates/rooms/list.html
    }

    // GET /rooms/{id}/book — formular pro rezervaci konkretni mistnosti
    @GetMapping("/{id}/book")
    public String showBookingForm(@PathVariable Long id, Model model) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));

        model.addAttribute("room", room);
        model.addAttribute("bookingRequest", new BookingRequest());
        return "rooms/book";  // templates/rooms/book.html
    }
}
