package com.fitjanta.repository;

import com.fitjanta.model.FoodLogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface FoodLogItemRepository extends JpaRepository<FoodLogItem, Long> {

    List<FoodLogItem> findByFoodLogIdIn(Collection<Long> foodLogIds);

    @Transactional
    void deleteByFoodLogId(Long foodLogId);
}
