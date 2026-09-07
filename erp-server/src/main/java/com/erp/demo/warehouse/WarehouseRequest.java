package com.erp.demo.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank(message = "仓库编码不能为空") @Size(max = 32, message = "仓库编码长度不能超过 32") String code,
        @NotBlank(message = "仓库名称不能为空") @Size(max = 80, message = "仓库名称长度不能超过 80") String name,
        @Size(max = 40, message = "负责人长度不能超过 40") String manager,
        String status
) {
}
