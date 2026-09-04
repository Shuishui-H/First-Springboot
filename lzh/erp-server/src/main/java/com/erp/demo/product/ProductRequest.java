package com.erp.demo.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "商品编码不能为空")
        @Size(max = 32, message = "商品编码不能超过 32 个字符")
        String sku,
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 80, message = "商品名称不能超过 80 个字符")
        String name,
        @NotBlank(message = "商品分类不能为空")
        @Size(max = 40, message = "商品分类不能超过 40 个字符")
        String category,
        @NotBlank(message = "单位不能为空")
        @Size(max = 10, message = "单位不能超过 10 个字符")
        String unit,
        @NotNull(message = "销售单价不能为空")
        @DecimalMin(value = "0.00", message = "销售单价不能小于 0")
        BigDecimal price,
        @NotNull(message = "当前库存不能为空")
        @Min(value = 0, message = "当前库存不能小于 0")
        @Max(value = 999999, message = "当前库存过大")
        Integer stock,
        @NotNull(message = "安全库存不能为空")
        @Min(value = 0, message = "安全库存不能小于 0")
        Integer safetyStock,
        @NotBlank(message = "状态不能为空")
        String status
) {
}
