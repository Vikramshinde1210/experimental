package com.example.jwtauth.controller;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jwtauth.dto.Room;

import jakarta.annotation.security.PermitAll;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_ADD')")
    public String addRoom() {
        return "Room added";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','GUEST')")
    @PostAuthorize("returnObject.assignedTo == authentication.name")
    public Room getRoomById(@PathVariable Long id) {
        return new Room(id, "Vikram");
    }

    @GetMapping
    @PermitAll
    public String getRooms() {
        return "All rooms";
    }
}
