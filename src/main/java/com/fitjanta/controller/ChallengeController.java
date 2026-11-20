package com.fitjanta.controller;

import com.fitjanta.model.Challenge;
import com.fitjanta.model.ChallengeCompletion;
import com.fitjanta.model.User;
import com.fitjanta.repository.ChallengeCompletionRepository;
import com.fitjanta.repository.ChallengeRepository;
import com.fitjanta.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final ChallengeCompletionRepository completionRepository;

    public ChallengeController(ChallengeRepository challengeRepository, UserRepository userRepository,
                               ChallengeCompletionRepository completionRepository) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.completionRepository = completionRepository;
    }

    // Get all challenges
    @GetMapping
    public List<Challenge> getAllChallenges() {
        return challengeRepository.findAll();
    }

    // IDs of challenges this user already completed
    @GetMapping("/completed/{userId}")
    public List<Long> completed(@PathVariable Long userId) {
        return completionRepository.findByUserId(userId).stream().map(ChallengeCompletion::getChallengeId).toList();
    }

    // Save a challenge
    @PostMapping
    public Challenge saveChallenge(@RequestBody Challenge challenge) {
        return challengeRepository.save(challenge);
    }

    // Complete a challenge (only once per user)
    @PostMapping("/{challengeId}/complete/{userId}")
    public ResponseEntity<?> completeChallenge(@PathVariable Long challengeId, @PathVariable Long userId) {

        Challenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Challenge not found."));
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please log in again."));
        }
        if (completionRepository.existsByUserIdAndChallengeId(userId, challengeId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "You already completed this challenge."));
        }

        int oldLevel = Math.max(1, user.getLevel());
        user.setXp(user.getXp() + challenge.getXp());
        user.setLevel((user.getXp() / 100) + 1);
        userRepository.save(user);

        ChallengeCompletion completion = new ChallengeCompletion();
        completion.setUserId(userId);
        completion.setChallengeId(challengeId);
        completion.setXpEarned(challenge.getXp());
        completionRepository.save(completion);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("xpEarned", challenge.getXp());
        result.put("levelUp", user.getLevel() > oldLevel);
        return ResponseEntity.ok(result);
    }
}
