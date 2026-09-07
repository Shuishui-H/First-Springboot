package com.erp.demo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "md_product")
public class ProductEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String sku;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, length = 20) private String unit;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal price;
    @Column(name = "safety_stock", nullable = false, precision = 18, scale = 4) private BigDecimal safetyStock;
    @Column(nullable = false) private Byte status;

    protected ProductEntity() { }
    public ProductEntity(String sku, String name, String category, String unit, BigDecimal price, BigDecimal safetyStock, Byte status) {
        this.sku = sku; this.name = name; this.category = category; this.unit = unit; this.price = price; this.safetyStock = safetyStock; this.status = status;
    }
    public Long getId() { return id; } public String getSku() { return sku; } public String getName() { return name; }
    public String getCategory() { return category; } public String getUnit() { return unit; } public BigDecimal getPrice() { return price; }
    public BigDecimal getSafetyStock() { return safetyStock; } public Byte getStatus() { return status; }
    public void update(String sku, String name, String category, String unit, BigDecimal price, BigDecimal safetyStock, Byte status) {
        this.sku = sku; this.name = name; this.category = category; this.unit = unit; this.price = price; this.safetyStock = safetyStock; this.status = status;
    }
}
