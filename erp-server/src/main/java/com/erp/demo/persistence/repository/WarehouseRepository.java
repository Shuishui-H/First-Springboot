package com.erp.demo.persistence.repository;

import com.erp.demo.persistence.entity.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {
    Optional<WarehouseEntity> findByCodeIgnoreCase(String code);
}
