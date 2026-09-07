package com.erp.demo.persistence.repository;

import com.erp.demo.persistence.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {
    Optional<SupplierEntity> findByCodeIgnoreCase(String code);
}
