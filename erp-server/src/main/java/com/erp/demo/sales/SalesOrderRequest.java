package com.erp.demo.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderRequest(
        @NotNull(message = "客户不能为空") Long customerId,
        @NotNull(message = "仓库不能为空") Long warehouseId,
        @NotNull(message = "订单日期不能为空") LocalDate orderDate,
        LocalDate requiredShipDate,
        String remark,
        @NotEmpty(message = "销售明细不能为空") @Valid List<Item> items
) {
    public record Item(
            @NotNull(message = "商品不能为空") Long productId,
            @NotNull(message = "销售数量不能为空") @Min(value = 1, message = "销售数量必须大于 0") Integer orderedQuantity,
            @NotNull(message = "销售单价不能为空") @DecimalMin(value = "0.00", message = "销售单价不能小于 0") BigDecimal unitPrice
    ) {}
}
