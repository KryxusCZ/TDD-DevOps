package cz.upce.roombooking.controller;

import cz.upce.roombooking.domain.Booking;
import cz.upce.roombooking.domain.BookingStatus;
import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.domain.User;
import cz.upce.roombooking.domain.UserRole;
import cz.upce.roombooking.dto.BookingRequest;
import cz.upce.roombooking.exception.BookingConflictException;
import cz.upce.roombooking.exception.BookingValidationException;
import cz.upce.roombooking.exception.UnauthorizedCancellationException;
import cz.upce.roombooking.repository.BookingRepository;
import cz.upce.roombooking.repository.RoomRepository;
import cz.upce.roombooking.repository.UserRepository;
import cz.upce.roombooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    // GET /bookings — admin vidi vsechny rezervace, uzivatel jen sve
    @GetMapping("/bookings")
    public String listMyBookings(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = getLoggedUser(userDetails);
        List<Booking> bookings = user.getRole() == UserRole.ADMIN
                ? bookingRepository.findAll()
                : bookingRepository.findByUser(user);
        model.addAttribute("bookings", bookings);
        return "bookings/list";
    }

    // POST /rooms/{id}/book — zpracovani formulare pro vytvoreni rezervace
    @PostMapping("/rooms/{id}/book")
    public String createBooking(@PathVariable Long id,
                                @Valid @ModelAttribute BookingRequest request,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal UserDetails userDetails,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));

        // validacni chyby z @Valid anotaci na BookingRequest
        if (bindingResult.hasErrors()) {
            model.addAttribute("room", room);
            model.addAttribute("existingBookings",
                    bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED));
            return "rooms/book";
        }

        try {
            User user = getLoggedUser(userDetails);
            bookingService.createBooking(user, room, request.getStartTime(), request.getEndTime());
            redirectAttributes.addFlashAttribute("success", "Rezervace uspesne vytvorena!");
            return "redirect:/bookings";

        } catch (BookingValidationException | BookingConflictException e) {
            // business pravidlo porouseno — vratime formular s chybovou zpravou a aktualnimi rezervacemi
            model.addAttribute("room", room);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("existingBookings",
                    bookingRepository.findByRoomAndStatusNot(room, BookingStatus.CANCELLED));
            return "rooms/book";
        }
    }

    // POST /bookings/{id}/cancel — zruseni rezervace
    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = getLoggedUser(userDetails);
            bookingService.cancelBooking(user, id);
            redirectAttributes.addFlashAttribute("success", "Rezervace uspesne zrusena.");

        } catch (BookingValidationException | UnauthorizedCancellationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/bookings";
    }

    // pomocna metoda — ziska prihlaseneho uzivatele z DB podle username ze Spring Security
    private User getLoggedUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Logged user not found in DB"));
    }
}
