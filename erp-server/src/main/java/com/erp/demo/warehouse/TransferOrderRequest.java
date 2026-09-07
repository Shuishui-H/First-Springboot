package com.erp.demo.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TransferOrderRequest(
        @NotNull(message = "调出仓不能为空") Long fromWarehouseId,
        @NotNull(message = "调入仓不能为空") Long toWarehouseId,
        LocalDate transferDate,
        String remark,
        @NotEmpty(message = "至少需要一条调拨明细") List<@Valid Item> items
) {
    public record Item(@NotNull(message = "商品不能为空") Long productId,
                       @NotNull(message = "调拨数量不能为空") @Min(value = 1, message = "调拨数量必须大于 0") Integer quantity,
                       String remark) { }
}
