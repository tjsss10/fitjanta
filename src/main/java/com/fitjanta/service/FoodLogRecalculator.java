package com.fitjanta.service;

import com.fitjanta.model.FoodLog;
import com.fitjanta.model.FoodLogItem;
import com.fitjanta.model.User;
import com.fitjanta.repository.FoodLogItemRepository;
import com.fitjanta.repository.FoodLogRepository;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.NutritionAiService.Analysis;
import com.fitjanta.service.NutritionAiService.ItemResult;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * When the nutrition engine is improved, meals already saved in MySQL are
 * re-analysed from their original description so the food diary always
 * matches the current (more accurate) calculation.
 */
@Component
public class FoodLogRecalculator implements CommandLineRunner {

    private final NutritionAiService ai;
    private final FoodLogRepository logRepository;
    private final FoodLogItemRepository itemRepository;
    private final UserRepository userRepository;

    public FoodLogRecalculator(NutritionAiService ai, FoodLogRepository logRepository,
                               FoodLogItemRepository itemRepository, UserRepository userRepository) {
        this.ai = ai;
        this.logRepository = logRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        int fixed = 0;
        for (FoodLog log : logRepository.findAll()) {
            if (log.getDescription() == null || log.getDescription().isBlank()) continue;

            int goal = userRepository.findById(log.getUserId()).map(User::getCalorieGoal).orElse(2000);
            Analysis a = ai.analyze(log.getDescription(), goal);
            if (a.items().isEmpty() || a.calories() == log.getCalories()) continue;

            System.out.printf("FitJanta AI: recalculated meal #%d \"%s\": %d -> %d kcal%n",
                    log.getId(), log.getDescription(), log.getCalories(), a.calories());

            log.setCalories(a.calories());
            log.setProtein(a.protein());
            log.setCarbs(a.carbs());
            log.setFat(a.fat());
            log.setFiber(a.fiber());
            log.setHealthScore(a.healthScore());
            log.setAiFeedback(String.join(" | ", a.tips()));
            logRepository.save(log);

            itemRepository.deleteByFoodLogId(log.getId());
            List<ItemResult> items = a.items();
            for (ItemResult it : items) {
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
                itemRepository.save(item);
            }
            fixed++;
        }
        if (fixed > 0) {
            System.out.println("FitJanta AI: " + fixed + " saved meal(s) updated to the latest nutrition engine.");
        }
    }
}
