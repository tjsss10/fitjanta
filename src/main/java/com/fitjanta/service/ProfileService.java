package com.fitjanta.service;

import com.fitjanta.model.Activity;
import com.fitjanta.model.User;
import com.fitjanta.repository.ActivityRepository;
import com.fitjanta.repository.PostRepository;
import com.fitjanta.repository.TerritoryRepository;
import com.fitjanta.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds the public profile of a user: what OTHER users may see.
 * Email, phone, address and password are never included.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final PostRepository postRepository;
    private final TerritoryRepository territoryRepository;

    public ProfileService(UserRepository userRepository, ActivityRepository activityRepository,
                          PostRepository postRepository, TerritoryRepository territoryRepository) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.postRepository = postRepository;
        this.territoryRepository = territoryRepository;
    }

    public record PublicProfile(Long id, String name, String city, String bio, String avatarUrl,
                                int level, int xp, int xpIntoLevel, int streak, int rank,
                                long postsCount, int workoutsCount, int territoriesCount,
                                double totalDistanceKm, int totalCaloriesBurned, int totalActiveMinutes,
                                String favouriteActivity, LocalDateTime joinedAt) {
    }

    /** Rank 1 = most XP. */
    public int rankOf(User user) {
        List<User> ranked = userRepository.findAllByOrderByXpDesc();
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getId().equals(user.getId())) return i + 1;
        }
        return ranked.size();
    }

    public PublicProfile build(User u, int rank) {
        List<Activity> acts = activityRepository.findByUserId(u.getId());
        String favourite = acts.stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getType() == null ? "Workout" : a.getType(),
                        java.util.stream.Collectors.counting()))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(null);

        return new PublicProfile(
                u.getId(), u.getName(), u.getCity(), u.getBio(), u.getAvatarUrl(),
                Math.max(1, u.getLevel()), u.getXp(), u.getXp() % 100, currentStreak(u), rank,
                postRepository.countByUserId(u.getId()), acts.size(),
                territoryRepository.findByUserId(u.getId()).size(),
                Math.round(acts.stream().mapToDouble(Activity::getDistance).sum() * 10) / 10.0,
                acts.stream().mapToInt(Activity::getCalories).sum(),
                acts.stream().mapToInt(Activity::getDuration).sum(),
                favourite, u.getCreatedAt());
    }

    public PublicProfile build(User u) {
        return build(u, rankOf(u));
    }

    /** Streak counts only if the last activity was today or yesterday. */
    public int currentStreak(User u) {
        String last = u.getLastActivityDate();
        if (last == null) return u.getStreak();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        return (last.equals(today) || last.equals(yesterday)) ? u.getStreak() : 0;
    }
}
