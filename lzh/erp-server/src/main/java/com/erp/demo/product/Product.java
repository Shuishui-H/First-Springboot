package com.erp.demo.product;

import java.math.BigDecimal;

public record Product(
        Long id,
        String sku,
        String name,
        String category,
        String unit,
        BigDecimal price,
        Integer stock,
        Integer safetyStock,
        String status
) {
}
