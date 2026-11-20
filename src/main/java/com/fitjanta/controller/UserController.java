package com.fitjanta.controller;

import com.fitjanta.model.Activity;
import com.fitjanta.model.FoodLog;
import com.fitjanta.model.User;
import com.fitjanta.repository.ActivityRepository;
import com.fitjanta.repository.FoodLogRepository;
import com.fitjanta.repository.PostRepository;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.GeoService;
import com.fitjanta.service.ImageStorageService;
import com.fitjanta.service.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final FoodLogRepository foodLogRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService images;
    private final ProfileService profiles;

    public UserController(UserRepository userRepository, ActivityRepository activityRepository,
                          FoodLogRepository foodLogRepository, PostRepository postRepository,
                          PasswordEncoder passwordEncoder, ImageStorageService images, ProfileService profiles) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.foodLogRepository = foodLogRepository;
        this.postRepository = postRepository;
        this.passwordEncoder = passwordEncoder;
        this.images = images;
        this.profiles = profiles;
    }

    public record LocationUpdate(Double latitude, Double longitude, Boolean share) {
    }

    // ------------------------------------------------------------------
    // Profile photo
    // ------------------------------------------------------------------

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@PathVariable Long id, @RequestParam("image") MultipartFile image)
            throws IOException {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found."));

        String path;
        try {
            path = images.save(image, "avatars");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        User user = found.get();
        images.delete(user.getAvatarPath());   // remove the old photo
        user.setAvatarPath(path);
        return ResponseEntity.ok(userRepository.save(user));
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<?> removeAvatar(@PathVariable Long id) {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        User user = found.get();
        images.delete(user.getAvatarPath());
        user.setAvatarPath(null);
        return ResponseEntity.ok(userRepository.save(user));
    }

    // ------------------------------------------------------------------
    // Public profile (what other users see, no email/phone/address)
    // ------------------------------------------------------------------

    @GetMapping("/{id}/public")
    public ResponseEntity<?> publicProfile(@PathVariable Long id) {
        return userRepository.findById(id)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(profiles.build(u)))
                .orElse(ResponseEntity.status(404).body(Map.of("message", "User not found.")));
    }

    // ------------------------------------------------------------------
    // Location for the live map
    // ------------------------------------------------------------------

    @PutMapping("/{id}/location")
    public ResponseEntity<?> updateLocation(@PathVariable Long id, @RequestBody LocationUpdate update) {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        User user = found.get();

        if (update.latitude() != null || update.longitude() != null) {
            if (!GeoService.validCoordinates(update.latitude(), update.longitude())) {
                return error("That location doesn't look right.");
            }
            user.setLatitude(Math.round(update.latitude() * 1e6) / 1e6);
            user.setLongitude(Math.round(update.longitude() * 1e6) / 1e6);
            user.setLocationUpdatedAt(LocalDateTime.now());
        }
        if (update.share() != null) {
            user.setShareLocation(update.share());
        }
        return ResponseEntity.ok(userRepository.save(user));
    }

    // ------------------------------------------------------------------
    // Last 7 days: calories eaten vs burned (home screen chart)
    // ------------------------------------------------------------------

    @GetMapping("/{id}/weekly")
    public List<Map<String, Object>> weekly(@PathVariable Long id) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            String day = d.toString();
            List<FoodLog> meals = foodLogRepository.findByUserIdAndLogDateOrderByIdAsc(id, day);
            List<Activity> acts = activityRepository.findByUserIdAndDate(id, day);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day);
            m.put("label", d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            m.put("eaten", meals.stream().mapToInt(FoodLog::getCalories).sum());
            m.put("burned", acts.stream().mapToInt(Activity::getCalories).sum());
            m.put("activeMinutes", acts.stream().mapToInt(Activity::getDuration).sum());
            m.put("workouts", acts.size());
            days.add(m);
        }
        return days;
    }

    public record LoginRequest(String email, String password) {
    }

    public record ProfileUpdate(String name, String bio, String city, String phone, Integer calorieGoal) {
    }

    // Register new user
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {

        if (isBlank(user.getName()) || isBlank(user.getEmail()) || isBlank(user.getPassword())) {
            return error("Name, email and password are required.");
        }
        if (!user.getEmail().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return error("Please enter a valid email address.");
        }
        if (user.getPassword().length() < 6) {
            return error("Password must be at least 6 characters.");
        }
        if (userRepository.existsByEmailIgnoreCase(user.getEmail().trim())) {
            return error("An account with this email already exists. Please log in.");
        }

        user.setName(user.getName().trim());
        user.setEmail(user.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // New users start with 0 XP, level 1 and no streak
        user.setXp(0);
        user.setLevel(1);
        user.setStreak(0);
        if (user.getCalorieGoal() <= 0) {
            user.setCalorieGoal(2000);
        }

        return ResponseEntity.ok(userRepository.save(user));
    }

    // Login
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest login) {

        if (login == null || isBlank(login.email()) || isBlank(login.password())) {
            return error("Enter your email and password.");
        }

        Optional<User> found = userRepository.findByEmailIgnoreCase(login.email().trim());
        if (found.isEmpty()) {
            return error("No account found with this email.");
        }

        User user = found.get();
        String stored = user.getPassword() == null ? "" : user.getPassword();

        boolean ok;
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            ok = passwordEncoder.matches(login.password(), stored);
        } else {
            // Account created before hashing was added: compare once, then upgrade to a hash
            ok = stored.equals(login.password());
            if (ok) {
                user.setPassword(passwordEncoder.encode(login.password()));
                userRepository.save(user);
            }
        }

        if (!ok) {
            return error("Incorrect password. Please try again.");
        }
        return ResponseEntity.ok(user);
    }

    // Get user by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("message", "User not found.")));
    }

    // Update profile
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id, @RequestBody ProfileUpdate update) {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }
        User user = found.get();

        if (update.name() != null && !update.name().isBlank()) user.setName(update.name().trim());
        if (update.bio() != null) user.setBio(update.bio().trim().length() > 300 ? update.bio().trim().substring(0, 300) : update.bio().trim());
        if (update.city() != null) user.setCity(update.city().trim());
        if (update.phone() != null) user.setPhone(update.phone().trim());
        if (update.calorieGoal() != null) {
            if (update.calorieGoal() < 1000 || update.calorieGoal() > 5000) {
                return error("Daily calorie goal should be between 1000 and 5000 kcal.");
            }
            user.setCalorieGoal(update.calorieGoal());
        }
        return ResponseEntity.ok(userRepository.save(user));
    }

    // Dashboard summary for the home screen
    @GetMapping("/{id}/summary")
    public ResponseEntity<?> summary(@PathVariable Long id) {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }
        User user = found.get();
        String today = LocalDate.now().toString();

        List<FoodLog> meals = foodLogRepository.findByUserIdAndLogDateOrderByIdAsc(id, today);
        List<Activity> workouts = activityRepository.findByUserIdAndDate(id, today);

        // Streak is broken if the last activity was before yesterday
        int streak = user.getStreak();
        String last = user.getLastActivityDate();
        if (last != null && !last.equals(today) && !last.equals(LocalDate.now().minusDays(1).toString())) {
            streak = 0;
        }

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("user", user);
        s.put("level", Math.max(1, user.getLevel()));
        s.put("xp", user.getXp());
        s.put("xpIntoLevel", user.getXp() % 100);
        s.put("xpForNextLevel", 100);
        s.put("streak", streak);
        s.put("calorieGoal", user.getCalorieGoal());
        s.put("caloriesEaten", meals.stream().mapToInt(FoodLog::getCalories).sum());
        s.put("proteinEaten", Math.round(meals.stream().mapToDouble(FoodLog::getProtein).sum()));
        s.put("mealsLogged", meals.size());
        s.put("caloriesBurned", workouts.stream().mapToInt(Activity::getCalories).sum());
        s.put("workoutsToday", workouts.size());
        s.put("activeMinutes", workouts.stream().mapToInt(Activity::getDuration).sum());
        s.put("distanceToday", Math.round(workouts.stream().mapToDouble(Activity::getDistance).sum() * 10) / 10.0);
        s.put("postsCount", postRepository.countByUserId(id));
        s.put("totalWorkouts", activityRepository.findByUserId(id).size());
        s.put("rank", profiles.rankOf(user));
        s.put("profile", profiles.build(user));
        return ResponseEntity.ok(s);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
