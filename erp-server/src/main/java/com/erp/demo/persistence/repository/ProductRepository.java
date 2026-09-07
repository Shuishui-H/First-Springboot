package com.erp.demo.persistence.repository;

import com.erp.demo.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findAllByOrderByIdAsc();
    Optional<ProductEntity> findBySkuIgnoreCase(String sku);
}
