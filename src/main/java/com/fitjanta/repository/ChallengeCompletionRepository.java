package com.fitjanta.repository;

import com.fitjanta.model.ChallengeCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeCompletionRepository extends JpaRepository<ChallengeCompletion, Long> {

    List<ChallengeCompletion> findByUserId(Long userId);

    boolean existsByUserIdAndChallengeId(Long userId, Long challengeId);
}
