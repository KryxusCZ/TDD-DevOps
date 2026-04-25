package cz.upce.roombooking.repository;

import cz.upce.roombooking.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Spring vygeneruje SQL: SELECT * FROM users WHERE username = ?
    // Pouziva Spring Security pri prihlaseni
    Optional<User> findByUsername(String username);
}
