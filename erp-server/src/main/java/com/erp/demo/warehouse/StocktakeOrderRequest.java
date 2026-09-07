package com.erp.demo.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record StocktakeOrderRequest(
        @NotNull(message = "盘点仓库不能为空") Long warehouseId,
        LocalDate stocktakeDate,
        String remark,
        @NotEmpty(message = "至少需要一条盘点明细") List<@Valid Item> items
) {
    public record Item(@NotNull(message = "商品不能为空") Long productId,
                       @NotNull(message = "实盘数量不能为空") @Min(value = 0, message = "实盘数量不能小于 0") Integer countedQuantity,
                       @NotBlank(message = "请填写盘点原因") String reason) { }
}
