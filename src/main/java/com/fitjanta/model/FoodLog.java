package com.fitjanta.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One meal saved in the food diary, e.g. "Lunch: 2 roti, dal, salad".
 * The individual foods are stored as rows in food_log_items.
 */
@Entity
@Table(name = "food_logs")
public class FoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    // Breakfast, Lunch, Snack, Dinner
    private String mealType;

    // What the user typed
    @Column(length = 1000)
    private String description;

    private int calories;
    private double protein;
    private double carbs;
    private double fat;
    private double fiber;

    // 1 - 10, calculated by the nutrition AI
    private int healthScore;

    @Column(length = 1500)
    private String aiFeedback;

    // yyyy-MM-dd
    private String logDate;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCalories() { return calories; }
    public void setCalories(int calories) { this.calories = calories; }

    public double getProtein() { return protein; }
    public void setProtein(double protein) { this.protein = protein; }

    public double getCarbs() { return carbs; }
    public void setCarbs(double carbs) { this.carbs = carbs; }

    public double getFat() { return fat; }
    public void setFat(double fat) { this.fat = fat; }

    public double getFiber() { return fiber; }
    public void setFiber(double fiber) { this.fiber = fiber; }

    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int healthScore) { this.healthScore = healthScore; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public String getLogDate() { return logDate; }
    public void setLogDate(String logDate) { this.logDate = logDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
