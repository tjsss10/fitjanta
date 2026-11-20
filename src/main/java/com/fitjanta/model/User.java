package com.fitjanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;

    // Stored as a BCrypt hash. Never sent back to the browser.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String address;
    private String city;
    private String pincode;
    private String country;

    @Column(length = 300)
    private String bio;

    // Daily calorie target used by the food tracker
    private Integer calorieGoal;

    // Fitness progress
    private int xp;
    private int level;
    private int streak;

    // Last day (yyyy-MM-dd) the user completed an activity
    private String lastActivityDate;

    private LocalDateTime createdAt;

    // Profile photo, e.g. /uploads/avatars/<uuid>.jpg
    private String avatarPath;

    // Last location shared on the map (null until the user shares it)
    private Double latitude;
    private Double longitude;
    private LocalDateTime locationUpdatedAt;

    // Whether other users can see this user on the map
    private Boolean shareLocation;

    public User() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (calorieGoal == null) {
            calorieGoal = 2000;
        }
    }

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public int getCalorieGoal() { return calorieGoal == null ? 2000 : calorieGoal; }
    public void setCalorieGoal(Integer calorieGoal) { this.calorieGoal = calorieGoal; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getStreak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }

    public String getLastActivityDate() { return lastActivityDate; }
    public void setLastActivityDate(String lastActivityDate) { this.lastActivityDate = lastActivityDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getAvatarUrl() { return avatarPath; }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }

    public boolean isShareLocation() { return shareLocation == null || shareLocation; }
    public void setShareLocation(Boolean shareLocation) { this.shareLocation = shareLocation; }
}
