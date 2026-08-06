package com.example.jwtauth.dto;

public class Room {

    private Long id;

    public Room() {
    }

    public Room(Long id, String assignedTo) {
        this.id = id;
        this.assignedTo = assignedTo;
    }

    public Long getId() {
        return id;
    }

    public Room setId(Long id) {
        this.id = id;
        return this;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public Room setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
        return this;
    }

    private String assignedTo;
}
