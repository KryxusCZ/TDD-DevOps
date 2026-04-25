package cz.upce.roombooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // Registrujeme Clock jako Spring Bean — v produkci vrací reálný systémový čas,
    // v testech ho nahrazujeme mockem s pevným časem
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
