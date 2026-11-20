package com.fitjanta.repository;

import com.fitjanta.model.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaderboardRepository
        extends JpaRepository<Leaderboard, Long> {
}