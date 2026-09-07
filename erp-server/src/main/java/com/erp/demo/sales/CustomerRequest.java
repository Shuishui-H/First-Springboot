package com.erp.demo.sales;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "客户编码不能为空") String code,
        @NotBlank(message = "客户名称不能为空") String name,
        String contact,
        String phone,
        String status
) {
}
