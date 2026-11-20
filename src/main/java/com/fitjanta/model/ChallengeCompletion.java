package com.fitjanta.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Records that a user completed a challenge, so it can only be claimed once.
 */
@Entity
@Table(name = "challenge_completions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "challenge_id"}))
public class ChallengeCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long challengeId;
    private int xpEarned;
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getChallengeId() { return challengeId; }
    public void setChallengeId(Long challengeId) { this.challengeId = challengeId; }

    public int getXpEarned() { return xpEarned; }
    public void setXpEarned(int xpEarned) { this.xpEarned = xpEarned; }

    public LocalDateTime getCompletedAt() { return completedAt; }
}
