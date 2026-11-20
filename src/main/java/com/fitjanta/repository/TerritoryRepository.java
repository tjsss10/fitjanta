package com.fitjanta.repository;

import com.fitjanta.model.Territory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TerritoryRepository
        extends JpaRepository<Territory, Long> {

    List<Territory> findByUserId(Long userId);

}