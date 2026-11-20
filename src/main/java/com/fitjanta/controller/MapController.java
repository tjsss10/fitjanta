package com.fitjanta.controller;

import com.fitjanta.model.Territory;
import com.fitjanta.model.User;
import com.fitjanta.repository.TerritoryRepository;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.GeoService;
import com.fitjanta.service.ProfileService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything the live map needs in one call: athletes (with their shared or
 * approximate location and distance from you) and all territories.
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final UserRepository userRepository;
    private final TerritoryRepository territoryRepository;
    private final GeoService geo;
    private final ProfileService profiles;

    public MapController(UserRepository userRepository, TerritoryRepository territoryRepository,
                         GeoService geo, ProfileService profiles) {
        this.userRepository = userRepository;
        this.territoryRepository = territoryRepository;
        this.geo = geo;
        this.profiles = profiles;
    }

    public record MapAthlete(Long id, String name, String city, String avatarUrl, int level, int xp, int streak,
                             double latitude, double longitude, boolean approximate,
                             LocalDateTime locationUpdatedAt, Double distanceKm, boolean me) {
    }

    public record MapTerritory(Long id, String name, double latitude, double longitude, double radius, int xp,
                               String status, Long ownerId, String ownerName, String ownerAvatar,
                               LocalDateTime claimedAt, Double distanceKm, boolean mine) {
    }

    @GetMapping("/{userId}")
    public Map<String, Object> map(@PathVariable Long userId) {
        List<User> users = userRepository.findAllByOrderByXpDesc();
        User me = users.stream().filter(u -> u.getId().equals(userId)).findFirst().orElse(null);

        double[] myPos = me == null ? null : position(me);

        List<MapAthlete> athletes = new ArrayList<>();
        for (User u : users) {
            boolean isMe = u.getId().equals(userId);
            if (!isMe && !u.isShareLocation()) continue;      // respect privacy setting
            double[] pos = position(u);
            if (pos == null) continue;
            boolean approx = !GeoService.validCoordinates(u.getLatitude(), u.getLongitude());
            Double dist = (myPos == null || isMe) ? null
                    : Math.round(geo.distanceKm(myPos[0], myPos[1], pos[0], pos[1]) * 10) / 10.0;
            athletes.add(new MapAthlete(u.getId(), u.getName(), u.getCity(), u.getAvatarUrl(),
                    Math.max(1, u.getLevel()), u.getXp(), profiles.currentStreak(u),
                    pos[0], pos[1], approx, u.getLocationUpdatedAt(), dist, isMe));
        }
        athletes.sort(Comparator.comparing((MapAthlete a) -> !a.me())
                .thenComparing(a -> a.distanceKm() == null ? Double.MAX_VALUE : a.distanceKm()));

        Map<Long, User> byId = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<MapTerritory> territories = new ArrayList<>();
        for (Territory t : territoryRepository.findAll()) {
            User owner = byId.get(t.getUserId());
            Double dist = myPos == null ? null
                    : Math.round(geo.distanceKm(myPos[0], myPos[1], t.getLatitude(), t.getLongitude()) * 10) / 10.0;
            territories.add(new MapTerritory(t.getId(), t.getName(), t.getLatitude(), t.getLongitude(), t.getRadius(),
                    t.getXp(), t.getStatus(), t.getUserId(),
                    owner == null ? "Unclaimed" : owner.getName(), owner == null ? null : owner.getAvatarUrl(),
                    t.getClaimedAt(), dist, Objects.equals(t.getUserId(), userId)));
        }
        territories.sort(Comparator.comparing(t -> t.distanceKm() == null ? Double.MAX_VALUE : t.distanceKm()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("me", me == null ? null : Map.of(
                "hasExactLocation", GeoService.validCoordinates(me.getLatitude(), me.getLongitude()),
                "shareLocation", me.isShareLocation(),
                "level", Math.max(1, me.getLevel()),
                "claimRadiusKm", Math.min(2.0, Math.round((0.5 + 0.1 * Math.max(1, me.getLevel())) * 10) / 10.0),
                "territoriesOwned", territories.stream().filter(MapTerritory::mine).count()));
        result.put("athletes", athletes);
        result.put("territories", territories);
        return result;
    }

    /** Exact shared location, or the user's city centre as an approximation. */
    private double[] position(User u) {
        if (GeoService.validCoordinates(u.getLatitude(), u.getLongitude())) {
            return new double[]{u.getLatitude(), u.getLongitude()};
        }
        return geo.approximateLocation(u.getCity(), u.getId());
    }
}
