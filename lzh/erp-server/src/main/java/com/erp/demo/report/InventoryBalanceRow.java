package com.erp.demo.report;

import java.math.BigDecimal;

/**
 * 新增：业务报表模块 —— 库存余额报表单行结果（对应开发文档 RPT-04）。
 */
public record InventoryBalanceRow(
        Long productId,
        String sku,
        String name,
        String category,
        String unit,
        int openingStock,       // 期初库存（内存版无期初快照，暂以 0 占位；待后续接入库存流水表后回填）
        int stockIn,            // 累计入库数量（已确认采购入库单口径）
        int stockOut,           // 累计出库数量（依赖销售/领用模块，当前为 0）
        int currentStock,       // 当前库存
        int availableStock,     // 可用库存（当前版本未做预留，= 当前库存）
        int safetyStock,        // 安全库存
        BigDecimal stockValue,  // 货值（销售价 × 当前库存）
        String status           // 商品启用状态
) {
}
