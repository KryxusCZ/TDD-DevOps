package cz.upce.roombooking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

// DTO = Data Transfer Object
// Prijima data z HTML formulare — oddeluje vstup od domainove entity
@Data
public class BookingRequest {

    @NotNull(message = "Zacatek rezervace je povinny")
    @Future(message = "Rezervace musi byt v budoucnosti")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @NotNull(message = "Konec rezervace je povinny")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;
}
