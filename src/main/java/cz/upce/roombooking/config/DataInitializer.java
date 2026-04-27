package cz.upce.roombooking.config;

import cz.upce.roombooking.domain.Room;
import cz.upce.roombooking.domain.User;
import cz.upce.roombooking.domain.UserRole;
import cz.upce.roombooking.repository.RoomRepository;
import cz.upce.roombooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Spusti se v "dev" i "prod" profilu — vytvori demo data pokud je DB prazdna
@Component
@Profile({"dev", "prod"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // vytvorime uzivatele jen pokud DB je prazdna
        if (userRepository.count() == 0) {
            createUsers();
            createRooms();
        }
    }

    private void createUsers() {
        User admin = User.builder()
                .username("admin")
                .email("admin@roombooking.cz")
                .password(passwordEncoder.encode("admin123"))
                .role(UserRole.ADMIN)
                .build();

        User user = User.builder()
                .username("jan")
                .email("jan@roombooking.cz")
                .password(passwordEncoder.encode("jan123"))
                .role(UserRole.USER)
                .build();

        userRepository.save(admin);
        userRepository.save(user);
    }

    private void createRooms() {
        roomRepository.save(Room.builder()
                .name("A101").capacity(10)
                .description("Mala zasedaci mistnost, projektor, tabule")
                .build());

        roomRepository.save(Room.builder()
                .name("B205").capacity(20)
                .description("Velka konferencni mistnost, plazma TV")
                .build());

        roomRepository.save(Room.builder()
                .name("C301").capacity(6)
                .description("Tiche pracovni misto, 6 mist")
                .build());
    }
}
