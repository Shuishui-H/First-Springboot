package com.erp.demo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "wh_stock_flow")
public class StockFlowEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "flow_no", nullable = false, unique = true, length = 50) private String flowNo;
    @Column(name = "warehouse_id", nullable = false) private Long warehouseId;
    @Column(name = "warehouse_name", nullable = false, length = 80) private String warehouseName;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(nullable = false, length = 32) private String sku;
    @Column(name = "product_name", nullable = false, length = 80) private String productName;
    @Column(nullable = false, length = 20) private String unit;
    @Column(name = "business_type", nullable = false, length = 30) private String businessType;
    @Column(name = "change_quantity", nullable = false, precision = 18, scale = 4) private BigDecimal changeQuantity;
    @Column(name = "before_quantity", nullable = false, precision = 18, scale = 4) private BigDecimal beforeQuantity;
    @Column(name = "after_quantity", nullable = false, precision = 18, scale = 4) private BigDecimal afterQuantity;
    @Column(name = "source_type", nullable = false, length = 30) private String sourceType;
    @Column(name = "source_id", nullable = false) private Long sourceId;
    @Column(name = "source_no", nullable = false, length = 50) private String sourceNo;
    @Column(length = 255) private String remark;

    protected StockFlowEntity() { }
}
