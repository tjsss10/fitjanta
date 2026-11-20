package com.fitjanta.controller;

import com.fitjanta.model.User;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.ProfileService;
import com.fitjanta.service.ProfileService.PublicProfile;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final UserRepository userRepository;
    private final ProfileService profiles;

    public LeaderboardController(UserRepository userRepository, ProfileService profiles) {
        this.userRepository = userRepository;
        this.profiles = profiles;
    }

    // All users ranked by XP (highest first). Public info only.
    @GetMapping
    public List<PublicProfile> getLeaderboard() {
        List<User> ranked = userRepository.findAllByOrderByXpDesc();
        List<PublicProfile> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            result.add(profiles.build(ranked.get(i), i + 1));
        }
        return result;
    }
}
