package com.erp.demo.persistence.repository;

import com.erp.demo.persistence.entity.InventoryBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalanceEntity, Long> {
    Optional<InventoryBalanceEntity> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    @Query("select coalesce(sum(item.quantity), 0) from InventoryBalanceEntity item where item.productId = :productId")
    BigDecimal sumQuantityByProductId(Long productId);
}
