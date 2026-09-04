package com.erp.demo.procurement;

public record Supplier(
        Long id,
        String code,
        String name,
        String contact,
        String phone,
        String status
) {
}
