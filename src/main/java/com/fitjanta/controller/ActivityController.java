package com.fitjanta.controller;

import com.fitjanta.model.Activity;
import com.fitjanta.model.User;
import com.fitjanta.repository.ActivityRepository;
import com.fitjanta.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public ActivityController(ActivityRepository activityRepository, UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    // Save activity, estimate calories burned, update XP, level and streak
    @PostMapping
    public ResponseEntity<?> saveActivity(@RequestBody Activity activity) {

        if (activity.getUserId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please log in again."));
        }
        Optional<User> found = userRepository.findById(activity.getUserId());
        if (found.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please log in again."));
        }
        if (activity.getDuration() <= 0 && activity.getDistance() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a duration or distance."));
        }

        String today = LocalDate.now().toString();
        activity.setDate(today);
        if (activity.getCalories() <= 0) {
            activity.setCalories(estimateCalories(activity));
        }
        Activity saved = activityRepository.save(activity);

        User user = found.get();
        int earnedXp = calculateXp(activity);
        int oldLevel = Math.max(1, user.getLevel());

        user.setXp(user.getXp() + earnedXp);
        user.setLevel((user.getXp() / 100) + 1);

        // Streak: +1 if last activity was yesterday, unchanged if today, otherwise restart at 1
        String last = user.getLastActivityDate();
        String yesterday = LocalDate.now().minusDays(1).toString();
        if (last == null || (!last.equals(today) && !last.equals(yesterday))) {
            user.setStreak(1);
        } else if (last.equals(yesterday)) {
            user.setStreak(user.getStreak() + 1);
        } else if (user.getStreak() == 0) {
            user.setStreak(1);
        }
        user.setLastActivityDate(today);
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activity", saved);
        result.put("xpEarned", earnedXp);
        result.put("levelUp", user.getLevel() > oldLevel);
        result.put("user", user);
        return ResponseEntity.ok(result);
    }

    // Activities for a user, newest first
    @GetMapping("/user/{userId}")
    public List<Activity> getUserActivities(@PathVariable Long userId) {
        return activityRepository.findByUserIdOrderByIdDesc(userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam Long userId) {
        Optional<Activity> a = activityRepository.findById(id);
        if (a.isEmpty() || !Objects.equals(a.get().getUserId(), userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "Activity not found."));
        }
        activityRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ------------------------------------------------------------------

    private int calculateXp(Activity activity) {
        String type = activity.getType() == null ? "" : activity.getType();
        int xp = switch (type.toLowerCase()) {
            case "walking" -> (int) (activity.getDistance() * 10);
            case "running" -> (int) (activity.getDistance() * 15);
            case "cycling" -> (int) (activity.getDistance() * 8);
            case "swimming", "workout", "gym" -> activity.getDuration() * 2;
            case "yoga" -> (int) (activity.getDuration() * 1.5);
            default -> 10;
        };
        if (xp == 0) xp = Math.max(10, activity.getDuration());
        return xp;
    }

    /** Approximate calories burned for a 65 kg person. */
    private int estimateCalories(Activity activity) {
        String type = activity.getType() == null ? "" : activity.getType().toLowerCase();
        double km = activity.getDistance();
        int min = activity.getDuration();
        double kcal = switch (type) {
            case "walking" -> km > 0 ? km * 55 : min * 4.5;
            case "running" -> km > 0 ? km * 68 : min * 11;
            case "cycling" -> km > 0 ? km * 30 : min * 7.5;
            case "swimming" -> min * 9;
            case "workout", "gym" -> min * 7;
            case "yoga" -> min * 3.5;
            default -> min * 5;
        };
        return (int) Math.round(kcal);
    }
}
