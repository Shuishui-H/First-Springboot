package com.erp.demo.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesReturnRequest(@NotNull(message = "原出库单不能为空") Long sourceStockOutId,
                                 @NotNull(message = "退货日期不能为空") LocalDate returnDate,
                                 @NotBlank(message = "退货原因不能为空") String reason,
                                 String remark,
                                 @NotEmpty(message = "退货明细不能为空") @Valid List<Item> items) {
    public record Item(@NotNull(message = "出库明细不能为空") Long sourceStockOutItemId,
                       @NotNull(message = "退货数量不能为空") @Min(value = 1, message = "退货数量必须大于 0") Integer returnedQuantity) {}
}
