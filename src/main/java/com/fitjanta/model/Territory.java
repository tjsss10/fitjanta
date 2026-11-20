package com.fitjanta.model;

import jakarta.persistence.*;

@Entity
@Table(name = "territories")
public class Territory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private double latitude;
    private double longitude;
    private double radius;
    private int xp;
    private String status;

    // ID of the user who owns this territory
    private Long userId;

    // When the territory was claimed
    private java.time.LocalDateTime claimedAt;

    public Territory() {
    }

    @PrePersist
    void onCreate() {
        if (claimedAt == null) {
            claimedAt = java.time.LocalDateTime.now();
        }
    }

    public java.time.LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}