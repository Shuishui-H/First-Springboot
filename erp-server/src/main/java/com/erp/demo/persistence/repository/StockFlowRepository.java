package com.erp.demo.persistence.repository;

import com.erp.demo.persistence.entity.StockFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockFlowRepository extends JpaRepository<StockFlowEntity, Long> {
}
