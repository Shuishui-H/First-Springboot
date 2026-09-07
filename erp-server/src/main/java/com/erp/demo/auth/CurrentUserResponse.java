package com.erp.demo.auth;

import java.util.List;

public record CurrentUserResponse(Long id, String username, String realName, String roleCode, String roleName,
                                  List<String> permissions, boolean mustChangePassword) {
}
