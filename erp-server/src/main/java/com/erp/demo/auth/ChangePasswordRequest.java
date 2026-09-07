package com.erp.demo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前登录用户修改自己的密码。 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空") String currentPassword,
        @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 100, message = "新密码长度应为 8 到 100 位") String newPassword) {
}
