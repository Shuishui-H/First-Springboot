package com.erp.demo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "wh_inventory_balance", uniqueConstraints = @UniqueConstraint(name = "uk_inventory_warehouse_product", columnNames = {"warehouse_id", "product_id"}))
public class InventoryBalanceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "warehouse_id", nullable = false) private Long warehouseId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal quantity;
    @Column(name = "locked_quantity", nullable = false, precision = 18, scale = 4) private BigDecimal lockedQuantity;
    @Column(name = "available_quantity", nullable = false, precision = 18, scale = 4) private BigDecimal availableQuantity;
    @Version @Column(nullable = false) private Long version;

    protected InventoryBalanceEntity() { }
    public InventoryBalanceEntity(Long warehouseId, Long productId, BigDecimal quantity, BigDecimal lockedQuantity) {
        this.warehouseId = warehouseId; this.productId = productId; this.quantity = quantity; this.lockedQuantity = lockedQuantity; this.availableQuantity = quantity.subtract(lockedQuantity);
    }
    public Long getId() { return id; } public Long getWarehouseId() { return warehouseId; } public Long getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; } public BigDecimal getLockedQuantity() { return lockedQuantity; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; } public Long getVersion() { return version; }
    public void applyChange(BigDecimal delta) { this.quantity = this.quantity.add(delta); this.availableQuantity = this.quantity.subtract(this.lockedQuantity); }
}
