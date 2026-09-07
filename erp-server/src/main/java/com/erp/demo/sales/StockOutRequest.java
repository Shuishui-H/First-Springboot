package com.erp.demo.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record StockOutRequest(@NotNull(message = "销售订单不能为空") Long salesOrderId,
                              @NotNull(message = "出库日期不能为空") LocalDate stockOutDate,
                              String remark,
                              @NotEmpty(message = "出库明细不能为空") @Valid List<Item> items) {
    public record Item(@NotNull(message = "销售明细不能为空") Long salesOrderItemId,
                       @NotNull(message = "出库数量不能为空") @Min(value = 1, message = "出库数量必须大于 0") Integer shippedQuantity) {}
}
