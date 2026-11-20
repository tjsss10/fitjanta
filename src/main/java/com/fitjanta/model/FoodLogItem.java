package com.fitjanta.model;

import jakarta.persistence.*;

/**
 * A single food recognised inside a meal, e.g. "2 piece Roti - 240 kcal".
 */
@Entity
@Table(name = "food_log_items")
public class FoodLogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long foodLogId;

    private String foodName;
    private double quantity;
    private String unit;
    private int grams;

    private int calories;
    private double protein;
    private double carbs;
    private double fat;
    private double fiber;

    public Long getId() { return id; }

    public Long getFoodLogId() { return foodLogId; }
    public void setFoodLogId(Long foodLogId) { this.foodLogId = foodLogId; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public int getGrams() { return grams; }
    public void setGrams(int grams) { this.grams = grams; }

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
}
