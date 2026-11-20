package com.fitjanta.repository;

import com.fitjanta.model.FoodLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {

    List<FoodLog> findByUserIdAndLogDateOrderByIdAsc(Long userId, String logDate);
}
