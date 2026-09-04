package com.erp.demo.procurement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank(message = "供应商编码不能为空")
        @Size(max = 32, message = "供应商编码不能超过 32 个字符")
        String code,
        @NotBlank(message = "供应商名称不能为空")
        @Size(max = 120, message = "供应商名称不能超过 120 个字符")
        String name,
        @Size(max = 64, message = "联系人不能超过 64 个字符")
        String contact,
        @Size(max = 32, message = "联系电话不能超过 32 个字符")
        String phone,
        @NotBlank(message = "供应商状态不能为空")
        String status
) {
}
