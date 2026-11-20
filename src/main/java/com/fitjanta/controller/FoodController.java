package com.fitjanta.controller;

import com.fitjanta.model.FoodLog;
import com.fitjanta.model.FoodLogItem;
import com.fitjanta.model.User;
import com.fitjanta.repository.FoodLogItemRepository;
import com.fitjanta.repository.FoodLogRepository;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.NutritionAiService;
import com.fitjanta.service.NutritionAiService.Analysis;
import com.fitjanta.service.NutritionAiService.ItemResult;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/food")
public class FoodController {

    private static final List<String> MEALS = List.of("Breakfast", "Lunch", "Snack", "Dinner");

    private final NutritionAiService ai;
    private final FoodLogRepository logRepository;
    private final FoodLogItemRepository itemRepository;
    private final UserRepository userRepository;

    public FoodController(NutritionAiService ai, FoodLogRepository logRepository,
                          FoodLogItemRepository itemRepository, UserRepository userRepository) {
        this.ai = ai;
        this.logRepository = logRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    public record FoodRequest(Long userId, String text, String mealType) {
    }

    // Analyse a meal without saving (live preview in the app)
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody FoodRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Describe what you ate first."));
        }
        return ResponseEntity.ok(ai.analyze(request.text(), goalFor(request.userId())));
    }

    // Analyse and save a meal to MySQL (food_logs + food_log_items)
    @PostMapping("/logs")
    @Transactional
    public ResponseEntity<?> saveMeal(@RequestBody FoodRequest request) {
        if (request.userId() == null || userRepository.findById(request.userId()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please log in again."));
        }
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Describe what you ate first."));
        }

        Analysis analysis = ai.analyze(request.text(), goalFor(request.userId()));
        if (analysis.items().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "I couldn't recognise any food. Try e.g. \"2 roti, dal and salad\"."));
        }

        FoodLog log = new FoodLog();
        log.setUserId(request.userId());
        log.setMealType(MEALS.contains(request.mealType()) ? request.mealType() : mealForNow());
        log.setDescription(request.text().trim());
        log.setCalories(analysis.calories());
        log.setProtein(analysis.protein());
        log.setCarbs(analysis.carbs());
        log.setFat(analysis.fat());
        log.setFiber(analysis.fiber());
        log.setHealthScore(analysis.healthScore());
        log.setAiFeedback(String.join(" | ", analysis.tips()));
        log.setLogDate(LocalDate.now().toString());
        log = logRepository.save(log);

        List<FoodLogItem> saved = new ArrayList<>();
        for (ItemResult it : analysis.items()) {
            FoodLogItem item = new FoodLogItem();
            item.setFoodLogId(log.getId());
            item.setFoodName(it.foodName());
            item.setQuantity(it.quantity());
            item.setUnit(it.unit());
            item.setGrams(it.grams());
            item.setCalories(it.calories());
            item.setProtein(it.protein());
            item.setCarbs(it.carbs());
            item.setFat(it.fat());
            item.setFiber(it.fiber());
            saved.add(itemRepository.save(item));
        }

        return ResponseEntity.ok(view(log, saved));
    }

    // Full food diary for one day with totals
    @GetMapping("/day/{userId}")
    public Map<String, Object> day(@PathVariable Long userId,
                                   @RequestParam(required = false) String date) {
        String day = (date == null || date.isBlank()) ? LocalDate.now().toString() : date;
        List<FoodLog> logs = logRepository.findByUserIdAndLogDateOrderByIdAsc(userId, day);

        Map<Long, List<FoodLogItem>> itemsByLog = logs.isEmpty() ? Map.of() :
                itemRepository.findByFoodLogIdIn(logs.stream().map(FoodLog::getId).toList())
                        .stream().collect(Collectors.groupingBy(FoodLogItem::getFoodLogId));

        int goal = goalFor(userId);
        int calories = logs.stream().mapToInt(FoodLog::getCalories).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day);
        result.put("goal", goal);
        result.put("calories", calories);
        result.put("remaining", goal - calories);
        result.put("protein", round1(logs.stream().mapToDouble(FoodLog::getProtein).sum()));
        result.put("carbs", round1(logs.stream().mapToDouble(FoodLog::getCarbs).sum()));
        result.put("fat", round1(logs.stream().mapToDouble(FoodLog::getFat).sum()));
        result.put("fiber", round1(logs.stream().mapToDouble(FoodLog::getFiber).sum()));
        // Daily macro targets: 20% protein, 50% carbs, 30% fat
        result.put("proteinGoal", Math.round(goal * 0.20 / 4));
        result.put("carbsGoal", Math.round(goal * 0.50 / 4));
        result.put("fatGoal", Math.round(goal * 0.30 / 9));
        result.put("meals", logs.stream().map(l -> view(l, itemsByLog.getOrDefault(l.getId(), List.of()))).toList());
        return result;
    }

    @DeleteMapping("/logs/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam Long userId) {
        Optional<FoodLog> log = logRepository.findById(id);
        if (log.isEmpty() || !Objects.equals(log.get().getUserId(), userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "Meal not found."));
        }
        itemRepository.deleteByFoodLogId(id);
        logRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("engine", "FitJanta AI Nutrition", "foodsInDatabase", ai.databaseSize());
    }

    // ------------------------------------------------------------------

    private Map<String, Object> view(FoodLog log, List<FoodLogItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("mealType", log.getMealType());
        m.put("description", log.getDescription());
        m.put("calories", log.getCalories());
        m.put("protein", log.getProtein());
        m.put("carbs", log.getCarbs());
        m.put("fat", log.getFat());
        m.put("fiber", log.getFiber());
        m.put("healthScore", log.getHealthScore());
        m.put("tips", log.getAiFeedback() == null ? List.of() : List.of(log.getAiFeedback().split(" \\| ")));
        m.put("createdAt", log.getCreatedAt());
        m.put("items", items);
        return m;
    }

    private int goalFor(Long userId) {
        if (userId == null) return 2000;
        return userRepository.findById(userId).map(User::getCalorieGoal).orElse(2000);
    }

    private static String mealForNow() {
        int h = LocalTime.now().getHour();
        if (h < 11) return "Breakfast";
        if (h < 16) return "Lunch";
        if (h < 19) return "Snack";
        return "Dinner";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
