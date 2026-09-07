package com.erp.demo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserAdminRequest(
        @NotBlank(message = "用户名不能为空") @Size(max = 50, message = "用户名不能超过 50 个字符") String username,
        @Size(min = 8, max = 100, message = "密码长度应为 8 到 100 位") String password,
        @Size(max = 50, message = "姓名不能超过 50 个字符") String realName,
        @NotBlank(message = "角色不能为空") String roleCode
) {}
