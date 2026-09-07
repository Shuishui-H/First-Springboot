package com.erp.demo.warehouse;

/** 仓库档案实体。 */
public record Warehouse(Long id, String code, String name, String manager, String status) {
}
