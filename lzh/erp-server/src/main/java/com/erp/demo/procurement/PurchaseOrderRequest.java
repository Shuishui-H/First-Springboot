package com.erp.demo.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderRequest(
        @NotNull(message = "供应商不能为空") Long supplierId,
        @NotNull(message = "订单日期不能为空") LocalDate orderDate,
        LocalDate expectedArrivalDate,
        String remark,
        @NotEmpty(message = "采购明细不能为空") @Valid List<Item> items
) {
    public record Item(
            @NotNull(message = "商品不能为空") Long productId,
            @NotNull(message = "采购数量不能为空") @Min(value = 1, message = "采购数量必须大于 0") Integer orderedQuantity,
            @NotNull(message = "采购单价不能为空") @DecimalMin(value = "0.00", message = "采购单价不能小于 0") BigDecimal unitPrice
    ) {
    }
}
