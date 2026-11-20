package com.fitjanta;

import com.fitjanta.model.Challenge;
import com.fitjanta.model.Leaderboard;
import com.fitjanta.model.Territory;
import com.fitjanta.repository.ChallengeRepository;
import com.fitjanta.repository.LeaderboardRepository;
import com.fitjanta.repository.TerritoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FitjantaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitjantaApplication.class, args);
    }

    /**
     * Adds sample data. Each item is only inserted if it is missing,
     * so restarting the app never creates duplicate rows in MySQL.
     */
    @Bean
    CommandLineRunner addData(
            TerritoryRepository territoryRepository,
            ChallengeRepository challengeRepository,
            LeaderboardRepository leaderboardRepository) {

        return args -> {

            // =========================
            // TERRITORY
            // =========================
            if (territoryRepository.count() == 0) {
                Territory territory = new Territory();
                territory.setName("Pune Territory");
                territory.setLatitude(18.5204);
                territory.setLongitude(73.8567);
                territory.setRadius(2);
                territory.setXp(100);
                territory.setStatus("ACTIVE");
                territory.setUserId(1L);   // Territory belongs to user ID 1
                territoryRepository.save(territory);
            }

            // Territories follow the claim rules: max 2 km radius.
            // (Older sample territories were 5 km and blocked a whole city.)
            territoryRepository.findAll().stream()
                    .filter(t -> t.getRadius() > 2.0)
                    .forEach(t -> {
                        t.setRadius(2.0);
                        territoryRepository.save(t);
                    });

            // =========================
            // CHALLENGES
            // =========================
            addChallenge(challengeRepository, "Walk 5 KM", "Complete a 5 kilometer walking session.", "Walking", 5, 50);
            addChallenge(challengeRepository, "Run 10 KM", "Complete 10 kilometers of running.", "Running", 10, 100);
            addChallenge(challengeRepository, "Workout 30 Minutes", "Complete a 30 minute workout session.", "Workout", 30, 75);
            addChallenge(challengeRepository, "Cycle 15 KM", "Ride 15 kilometers on your bicycle.", "Cycling", 15, 90);
            addChallenge(challengeRepository, "Yoga Flow 20 Minutes", "Finish a calm 20 minute yoga session.", "Yoga", 20, 40);
            addChallenge(challengeRepository, "10,000 Steps Day", "Hit 10,000 steps in a single day.", "Walking", 10000, 80);
            addChallenge(challengeRepository, "Swim 30 Minutes", "Swim continuously for 30 minutes.", "Swimming", 30, 85);
            addChallenge(challengeRepository, "Healthy Plate", "Log a meal with a FitJanta AI health score of 8 or more.", "Nutrition", 8, 60);

            // =========================
            // LEADERBOARD (legacy sample table)
            // =========================
            if (leaderboardRepository.count() == 0) {
                addPlayer(leaderboardRepository, "Tejas", 500, 5, 7);
                addPlayer(leaderboardRepository, "Rahul", 350, 3, 5);
                addPlayer(leaderboardRepository, "Priya", 250, 2, 3);
            }
        };
    }

    private static void addChallenge(ChallengeRepository repo, String title, String description,
                                     String type, int target, int xp) {
        if (repo.existsByTitle(title)) {
            return;
        }
        Challenge c = new Challenge();
        c.setTitle(title);
        c.setDescription(description);
        c.setActivityType(type);
        c.setTarget(target);
        c.setXp(xp);
        c.setStatus("ACTIVE");
        repo.save(c);
    }

    private static void addPlayer(LeaderboardRepository repo, String name, int xp, int level, int streak) {
        Leaderboard p = new Leaderboard();
        p.setName(name);
        p.setXp(xp);
        p.setLevel(level);
        p.setStreak(streak);
        repo.save(p);
    }
}
