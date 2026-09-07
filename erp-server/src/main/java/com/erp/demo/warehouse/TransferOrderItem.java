package com.erp.demo.warehouse;

/** 调拨单商品明细。 */
public record TransferOrderItem(Long productId, String productSku, String productName, String unit, Integer quantity,
                                String remark) {
}
