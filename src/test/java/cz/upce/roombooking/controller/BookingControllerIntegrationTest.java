package cz.upce.roombooking.controller;

import cz.upce.roombooking.domain.*;
import cz.upce.roombooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integracni testy BookingController — testuje celou HTTP vrstvu vcetne
 * Spring Security, validace a business logiky s realnou H2 databazi.
 *
 * MockMvc je sestaveno rucne pres webAppContextSetup() — nevyzaduje
 * @AutoConfigureMockMvc ani balicky spring-boot-test-autoconfigure.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingControllerIntegrationTest {

    // WebApplicationContext potrebujeme pro rucni sestaveni MockMvc
    @Autowired private WebApplicationContext context;

    @Autowired private RoomRepository    roomRepository;
    @Autowired private UserRepository    userRepository;
    @Autowired private BookingRepository bookingRepository;

    // @MockitoBean = Spring Boot 4 nahrada za @MockBean
    // Nahrazuje Clock bean v celem Spring kontextu — BookingService pouzije tento mock
    @MockitoBean private Clock clock;

    private MockMvc mockMvc;

    private static final LocalDateTime MOCK_NOW = LocalDateTime.of(2030, 6, 1, 9, 0);

    private Room room;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Sestavujeme MockMvc rucne — ekvivalent @AutoConfigureMockMvc
        // springSecurity() aktivuje Spring Security filtry (bez toho by @WithMockUser nefungovalo)
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Clock stub — BookingService.checkNotInPast() vola LocalDateTime.now(clock)
        when(clock.instant())
                .thenReturn(MOCK_NOW.atZone(ZoneId.systemDefault()).toInstant());
        when(clock.getZone())
                .thenReturn(ZoneId.systemDefault());

        room = roomRepository.save(Room.builder()
                .name("A101").capacity(10).description("Testovaci mistnost").build());

        // Uzivatel musi existovat v DB — controller vola userRepository.findByUsername()
        testUser = userRepository.save(User.builder()
                .username("testuser")
                .email("testuser@test.cz")
                .password("dummy")
                .role(UserRole.USER)
                .build());
    }

    // -----------------------------------------------------------------------
    // GET /rooms/{id}/book
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "testuser")
    void getBookingForm_returns200WithRoomAndBookings() throws Exception {
        mockMvc.perform(get("/rooms/{id}/book", room.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms/book"))
                .andExpect(model().attributeExists("room"))
                .andExpect(model().attributeExists("existingBookings"))
                .andExpect(model().attributeExists("bookingRequest"));
    }

    // -----------------------------------------------------------------------
    // POST /rooms/{id}/book — uspesna rezervace
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "testuser")
    void postBooking_withValidData_redirectsToBookings() throws Exception {
        mockMvc.perform(post("/rooms/{id}/book", room.getId())
                        .param("startTime", "2030-06-01T10:00")
                        .param("endTime",   "2030-06-01T12:00")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));
    }

    // -----------------------------------------------------------------------
    // POST /rooms/{id}/book — kolize (Pravidlo 3)
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "testuser")
    void postBooking_whenTimeConflicts_showsErrorOnForm() throws Exception {
        // Arrange — existujici rezervace 10:00-12:00
        bookingRepository.save(Booking.builder()
                .user(testUser).room(room)
                .startTime(LocalDateTime.of(2030, 6, 1, 10, 0))
                .endTime(LocalDateTime.of(2030, 6, 1, 12, 0))
                .status(BookingStatus.CONFIRMED)
                .build());

        // pokusime se rezervovat prekryvajici se cas 11:00-13:00
        mockMvc.perform(post("/rooms/{id}/book", room.getId())
                        .param("startTime", "2030-06-01T11:00")
                        .param("endTime",   "2030-06-01T13:00")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms/book"))
                .andExpect(model().attributeExists("error"));
    }

    // -----------------------------------------------------------------------
    // POST /rooms/{id}/book — chybejici endTime (Bean Validation)
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "testuser")
    void postBooking_whenEndTimeMissing_showsForm() throws Exception {
        mockMvc.perform(post("/rooms/{id}/book", room.getId())
                        .param("startTime", "2030-06-01T10:00")
                        // endTime chybi — @NotNull selze
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms/book"));
    }

    // -----------------------------------------------------------------------
    // POST /bookings/{id}/cancel
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "testuser")
    void cancelBooking_redirectsToBookings() throws Exception {
        // Arrange — rezervace zacina za 3h od MOCK_NOW => splnuje pravidlo min. 2h predem
        Booking booking = bookingRepository.save(Booking.builder()
                .user(testUser).room(room)
                .startTime(MOCK_NOW.plusHours(3))
                .endTime(MOCK_NOW.plusHours(5))
                .status(BookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(post("/bookings/{id}/cancel", booking.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));
    }
}
