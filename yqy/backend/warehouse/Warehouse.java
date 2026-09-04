package com.erp.demo.warehouse;

/**
 * 仓库档案实体（字段参考 PRD.md：编码、名称、负责人、状态）
 */
public record Warehouse(
        Long id,
        String code,
        String name,
        String manager,
        String status
) {
}
