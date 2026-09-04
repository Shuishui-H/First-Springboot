package com.erp.demo.report;

/**
 * 新增：业务报表模块 —— 库存预警单行结果（对应开发文档 RPT-06）。
 * 预警类型说明：
 * - 缺货       ：当前库存 <= 0；
 * - 库存偏低   ：0 < 当前库存 < 安全库存；
 * - 停用商品   ：商品已停用但仍留有库存（提示待清理，可在此叠加三类口径）。
 */
public record InventoryWarningRow(
        Long productId,
        String sku,
        String name,
        String category,
        String unit,
        int currentStock,       // 当前库存
        int safetyStock,        // 安全库存
        int gap,                // 缺口 = max(0, 安全库存 - 当前库存)
        String warningType,     // 预警类型
        String status           // 商品启用状态
) {
}
