package cz.upce.roombooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // BCrypt je standard pro hashovani hesel
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // verejne dostupne — login stranka a staticke soubory
                .requestMatchers("/login", "/css/**", "/js/**").permitAll()
                // H2 konzole dostupna pouze v dev profilu
                .requestMatchers("/h2-console/**").permitAll()
                // vse ostatni vyzaduje prihlaseni
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/rooms")    // po prihlaseni presmeruj na seznam mistnosti
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // H2 konzole pouziva iframes
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            // CSRF vypnuto pro H2 konzoli — v produkci je CSRF ochrana aktivni
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**")
            );

        return http.build();
    }
}
