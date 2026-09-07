package com.erp.demo.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@RestController
@Profile("mysql")
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpSession session) { return authService.login(request, session); }

    @GetMapping("/me")
    public CurrentUserResponse currentUser() { return authService.currentUser(); }

    @PostMapping("/logout")
    public void logout(HttpSession session) { authService.logout(session); }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) { authService.changePassword(request); }

    @GetMapping("/status")
    public Map<String, Boolean> status(@org.springframework.beans.factory.annotation.Value("${erp.auth.enabled:false}") boolean enabled) {
        return Map.of("enabled", enabled);
    }
}
