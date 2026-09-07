package com.erp.demo.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("mysql")
@RequestMapping("/api/system")
public class UserAdminController {
    private final UserAdminService service;
    public UserAdminController(UserAdminService service) { this.service = service; }

    @GetMapping("/users") @PreAuthorize("hasAuthority('system:user:list')")
    public List<UserAdminService.UserSummary> users(@RequestParam(required = false) String keyword, @RequestParam(required = false) String roleCode, @RequestParam(required = false) Integer status) { return service.users(keyword, roleCode, status); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('system:user:add')")
    public UserAdminService.UserSummary create(@Valid @RequestBody UserAdminRequest request) { return service.create(request); }
    @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:edit')")
    public UserAdminService.UserSummary update(@PathVariable Long id, @Valid @RequestBody UserAdminRequest request) { return service.update(id, request); }
    @PutMapping("/users/{id}/status") @PreAuthorize("hasAuthority('system:user:status')")
    public UserAdminService.UserSummary status(@PathVariable Long id, @RequestBody StatusRequest request) { return service.changeStatus(id, request.status()); }
    @PostMapping("/users/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('system:user:password')")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) { service.resetPassword(id, request.password()); }

    @GetMapping("/roles") @PreAuthorize("hasAuthority('system:role:list')")
    public List<UserAdminService.RoleSummary> roles() { return service.roles(); }
    @PutMapping("/roles/{id}/status") @PreAuthorize("hasAuthority('system:role:status')")
    public UserAdminService.RoleSummary roleStatus(@PathVariable Long id, @RequestBody StatusRequest request) { return service.changeRoleStatus(id, request.status()); }
    @GetMapping("/permissions") @PreAuthorize("hasAuthority('system:role:list')")
    public List<UserAdminService.PermissionSummary> permissions() { return service.permissions(); }
    @PutMapping("/roles/{id}/permissions") @PreAuthorize("hasAuthority('system:role:config')")
    public UserAdminService.RoleSummary permissions(@PathVariable Long id, @RequestBody PermissionRequest request) { return service.updatePermissions(id, request.permissionIds()); }

    public record StatusRequest(@jakarta.validation.constraints.NotNull Integer status) {}
    public record PermissionRequest(List<Long> permissionIds) {}
    public record PasswordResetRequest(@NotBlank @Size(min = 8, max = 100) String password) {}
}
