package cz.upce.roombooking.repository;

import cz.upce.roombooking.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    // JpaRepository uz obsahuje: findAll(), findById(), save(), delete()
    // pro mistnosti zadne extra metody nepotrebujeme
}
