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
    private final boolean resetDemoAccounts;

    public InitialAdminBootstrapper(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                                    @Value("${erp.auth.enabled:false}") boolean authEnabled,
                                    @Value("${erp.auth.initial-admin-username:admin}") String username,
                                    @Value("${erp.auth.initial-admin-password:}") String password,
                                    @Value("${erp.auth.demo-accounts-reset:true}") boolean resetDemoAccounts) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authEnabled = authEnabled;
        this.username = username;
        this.password = password;
        this.resetDemoAccounts = resetDemoAccounts;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createInitialAdminWhenNeeded() {
        if (!authEnabled) return;
        if (password == null || password.isBlank()) throw new IllegalStateException("已启用登录保护，但尚未设置 ERP_INITIAL_ADMIN_PASSWORD");
        ensureAccount(username.trim(), password, "系统管理员", "admin");
        ensureAccount("purchase", "123456", "采购员", "purchaser");
        ensureAccount("saler", "123456", "销售员", "seller");
        ensureAccount("warehouse", "123456", "仓库管理员", "warehouse");
        ensureAccount("finance", "123456", "经营管理者", "manager");
    }

    private void ensureAccount(String account, String rawPassword, String realName, String roleCode) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, account) > 0) {
            if (resetDemoAccounts) {
                jdbcTemplate.update("""
                        UPDATE sys_user u JOIN sys_role r ON r.role_code = ?
                           SET u.password_hash = ?, u.real_name = ?, u.role_id = r.id, u.status = 1,
                               u.must_change_password = 0, u.failed_login_count = 0, u.locked_until = NULL,
                               u.updated_at = CURRENT_TIMESTAMP
                         WHERE u.username = ?
                        """, roleCode, passwordEncoder.encode(rawPassword), realName, account);
            }
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, real_name, role_id, status, created_by, updated_by)
                SELECT ?, ?, ?, id, 1, 0, 0 FROM sys_role WHERE role_code = ?
                """, account, passwordEncoder.encode(rawPassword), realName, roleCode);
    }
}
