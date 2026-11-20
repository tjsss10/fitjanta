package com.fitjanta.repository;

import com.fitjanta.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByUserId(Long userId);

    List<Activity> findByUserIdOrderByIdDesc(Long userId);

    List<Activity> findByUserIdAndDate(Long userId, String date);
}
