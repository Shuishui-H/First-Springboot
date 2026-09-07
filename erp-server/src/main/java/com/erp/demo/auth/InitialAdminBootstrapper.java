package com.erp.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("mysql")
public class InitialAdminBootstrapper {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean authEnabled;
    private final String username;
    private final String password;

    public InitialAdminBootstrapper(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                                    @Value("${erp.auth.enabled:false}") boolean authEnabled,
                                    @Value("${erp.auth.initial-admin-username:admin}") String username,
                                    @Value("${erp.auth.initial-admin-password:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authEnabled = authEnabled;
        this.username = username;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createInitialAdminWhenNeeded() {
        if (!authEnabled || jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class) > 0) return;
        if (password == null || password.isBlank()) throw new IllegalStateException("已启用登录保护，但尚未设置 ERP_INITIAL_ADMIN_PASSWORD");
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, real_name, role_id, status, created_by, updated_by)
                VALUES (?, ?, '系统管理员', 1, 1, 0, 0)
                """, username.trim(), passwordEncoder.encode(password));
    }
}
