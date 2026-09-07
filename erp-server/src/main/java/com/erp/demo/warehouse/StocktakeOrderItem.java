package com.erp.demo.warehouse;

/** 盘点明细：差异由后端根据账面和实盘数计算。 */
public record StocktakeOrderItem(Long productId, String productSku, String productName, String unit,
                                 Integer bookQuantity, Integer countedQuantity, Integer differenceQuantity,
                                 String reason, long balanceVersion) {
}
