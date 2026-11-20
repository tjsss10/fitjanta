package com.fitjanta.repository;

import com.fitjanta.model.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    boolean existsByTitle(String title);
}
