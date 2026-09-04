package com.erp.demo.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseReceiptRequest(
        @NotNull(message = "采购订单不能为空") Long purchaseOrderId,
        @NotNull(message = "入库仓库不能为空") Long warehouseId,
        @NotNull(message = "入库日期不能为空") LocalDate stockInDate,
        String warehouseName,
        String remark,
        @NotEmpty(message = "入库明细不能为空") @Valid List<Item> items
) {
    public record Item(
            @NotNull(message = "采购订单明细不能为空") Long purchaseOrderItemId,
            @NotNull(message = "实收数量不能为空") @Min(value = 1, message = "实收数量必须大于 0") Integer receivedQuantity
    ) {
    }
}
