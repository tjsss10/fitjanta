package com.fitjanta.controller;

import com.fitjanta.model.Territory;
import com.fitjanta.model.User;
import com.fitjanta.repository.TerritoryRepository;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.GeoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/territories")
public class TerritoryController {

    private static final int MAX_TERRITORIES = 5;
    private static final int CLAIM_XP = 25;

    private final TerritoryRepository territoryRepository;
    private final UserRepository userRepository;
    private final GeoService geo;

    public TerritoryController(TerritoryRepository territoryRepository, UserRepository userRepository, GeoService geo) {
        this.territoryRepository = territoryRepository;
        this.userRepository = userRepository;
        this.geo = geo;
    }

    public record ClaimRequest(Long userId, String name, Double latitude, Double longitude) {
    }

    // Get all territories
    @GetMapping
    public List<Territory> getAllTerritories() {
        return territoryRepository.findAll();
    }

    // Get territory belonging to a specific user
    @GetMapping("/user/{userId}")
    public List<Territory> getUserTerritories(@PathVariable Long userId) {
        return territoryRepository.findByUserId(userId);
    }

    /**
     * Claim a territory around a point. Rules:
     *  - radius grows with level (0.5 km + 0.1 km per level, max 2 km)
     *  - the centre can't be inside someone else's territory
     *  - up to 5 territories per user, +25 XP per claim
     */
    @PostMapping("/claim")
    public ResponseEntity<?> claim(@RequestBody ClaimRequest req) {
        if (req.userId() == null) return error("Please log in again.");
        Optional<User> found = userRepository.findById(req.userId());
        if (found.isEmpty()) return error("Please log in again.");
        User user = found.get();

        Double lat = req.latitude() != null ? req.latitude() : user.getLatitude();
        Double lon = req.longitude() != null ? req.longitude() : user.getLongitude();
        if (!GeoService.validCoordinates(lat, lon)) {
            return error("Set your location on the map first.");
        }

        List<Territory> mine = territoryRepository.findByUserId(user.getId());
        if (mine.size() >= MAX_TERRITORIES) {
            return error("You already own " + MAX_TERRITORIES + " territories. Release one to claim a new area.");
        }

        for (Territory t : territoryRepository.findAll()) {
            double d = geo.distanceKm(lat, lon, t.getLatitude(), t.getLongitude());
            if (d < t.getRadius()) {
                if (Objects.equals(t.getUserId(), user.getId())) {
                    return error("This spot is already inside your territory \"" + t.getName() + "\".");
                }
                String owner = userRepository.findById(t.getUserId()).map(User::getName).orElse("another athlete");
                return error("This area belongs to " + owner + " (" + t.getName() + "). Move outside it to claim.");
            }
        }

        int level = Math.max(1, user.getLevel());
        double radius = Math.min(2.0, Math.round((0.5 + 0.1 * level) * 10) / 10.0);

        String name = req.name() == null || req.name().isBlank()
                ? (user.getCity() == null || user.getCity().isBlank() ? "Zone" : capitalise(user.getCity()))
                  + " Zone " + (mine.size() + 1)
                : req.name().trim();
        if (name.length() > 40) name = name.substring(0, 40);

        Territory t = new Territory();
        t.setName(name);
        t.setLatitude(Math.round(lat * 1e6) / 1e6);
        t.setLongitude(Math.round(lon * 1e6) / 1e6);
        t.setRadius(radius);
        t.setXp(CLAIM_XP);
        t.setStatus("ACTIVE");
        t.setUserId(user.getId());
        t = territoryRepository.save(t);

        int oldLevel = level;
        user.setXp(user.getXp() + CLAIM_XP);
        user.setLevel(user.getXp() / 100 + 1);
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("territory", t);
        result.put("xpEarned", CLAIM_XP);
        result.put("levelUp", user.getLevel() > oldLevel);
        result.put("user", user);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> release(@PathVariable Long id, @RequestParam Long userId) {
        Optional<Territory> t = territoryRepository.findById(id);
        if (t.isEmpty() || !Objects.equals(t.get().getUserId(), userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "Territory not found."));
        }
        territoryRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("released", id));
    }

    private static String capitalise(String s) {
        s = s.trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
