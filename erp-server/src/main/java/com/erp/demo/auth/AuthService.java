package com.erp.demo.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("mysql")
public class AuthService {
    private static final String SECURITY_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";
    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean authEnabled;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                       @Value("${erp.auth.enabled:false}") boolean authEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authEnabled = authEnabled;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public CurrentUserResponse login(LoginRequest request, HttpSession session) {
        if (!authEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录功能尚未启用，请先完成初始管理员配置");
        UserRow row = findUser(request.username().trim());
        if (row == null || row.status() != 1 || row.lockedUntil() != null && row.lockedUntil().isAfter(LocalDateTime.now())
                || !passwordEncoder.matches(request.password(), row.passwordHash())) {
            if (row != null && row.status() == 1 && (row.lockedUntil() == null || !row.lockedUntil().isAfter(LocalDateTime.now()))) {
                recordFailedLogin(row);
            }
            log(row == null ? null : row.id(), request.username().trim(), "LOGIN_FAILURE", null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        List<String> permissions = permissions(row.id());
        UserDetails principal = User.withUsername(row.username()).password("").authorities(permissions.stream().map(SimpleGrantedAuthority::new).toList()).build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = new SecurityContextImpl(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(SECURITY_CONTEXT_KEY, context);
        jdbcTemplate.update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP, failed_login_count = 0 WHERE id = ?", row.id());
        log(row.id(), row.username(), "LOGIN_SUCCESS", null);
        return new CurrentUserResponse(row.id(), row.username(), row.realName(), row.roleCode(), row.roleName(), permissions, row.mustChangePassword());
    }

    public CurrentUserResponse currentUser() {
        if (!authEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录功能尚未启用");
        String username = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || "anonymousUser".equals(username)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "尚未登录");
        UserRow row = findUser(username);
        if (row == null || row.status() != 1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        return new CurrentUserResponse(row.id(), row.username(), row.realName(), row.roleCode(), row.roleName(), permissions(row.id()), row.mustChangePassword());
    }

    @Transactional
    public void logout(HttpSession session) {
        String username = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getName();
        if (username != null && !"anonymousUser".equals(username)) {
            UserRow row = findUser(username);
            log(row == null ? null : row.id(), username, "LOGOUT", null);
        }
        session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void changePassword(ChangePasswordRequest request) {
        CurrentUserResponse currentUser = currentUser();
        UserRow row = findUser(currentUser.username());
        if (row == null || !passwordEncoder.matches(request.currentPassword(), row.passwordHash())) {
            log(currentUser.id(), currentUser.username(), "PASSWORD_CHANGE_FAILURE", null);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), row.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        jdbcTemplate.update("""
                UPDATE sys_user
                   SET password_hash = ?, must_change_password = 0, failed_login_count = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, passwordEncoder.encode(request.newPassword()), row.id());
        log(row.id(), row.username(), "PASSWORD_CHANGE_SUCCESS", null);
    }

    private UserRow findUser(String username) {
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT u.id, u.username, u.password_hash, u.real_name, u.status, u.must_change_password, u.failed_login_count, u.locked_until,
                       r.role_code, r.role_name
                  FROM sys_user u JOIN sys_role r ON r.id = u.role_id
                 WHERE u.username = ? AND r.status = 1
                """, (resultSet, rowNum) -> new UserRow(resultSet.getLong("id"), resultSet.getString("username"),
                resultSet.getString("password_hash"), resultSet.getString("real_name"), resultSet.getInt("status"),
                resultSet.getBoolean("must_change_password"), resultSet.getInt("failed_login_count"), resultSet.getTimestamp("locked_until") == null ? null : resultSet.getTimestamp("locked_until").toLocalDateTime(),
                resultSet.getString("role_code"), resultSet.getString("role_name")), username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> permissions(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT p.permission_code FROM sys_user u
                JOIN sys_role_permission rp ON rp.role_id = u.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE u.id = ? AND p.status = 1 AND p.permission_code IS NOT NULL
                ORDER BY p.permission_code
                """, String.class, userId);
    }

    private void recordFailedLogin(UserRow row) {
        int nextCount = row.failedLoginCount() + 1;
        LocalDateTime lockedUntil = nextCount >= MAX_FAILED_LOGIN_COUNT ? LocalDateTime.now().plusMinutes(15) : null;
        jdbcTemplate.update("""
                UPDATE sys_user
                   SET failed_login_count = ?, locked_until = ?
                 WHERE id = ?
                """, nextCount, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil), row.id());
        if (lockedUntil != null) log(row.id(), row.username(), "LOGIN_LOCKED", null);
    }

    private void log(Long userId, String username, String action, String remark) {
        jdbcTemplate.update("""
                INSERT INTO sys_operation_log (module, action, business_type, business_no, after_summary, operator_id, operator_name)
                VALUES ('AUTH', ?, 'USER', ?, JSON_OBJECT('result', ?), ?, ?)
                """, action, username, action, userId, username);
    }

    private record UserRow(Long id, String username, String passwordHash, String realName, int status,
                           boolean mustChangePassword, int failedLoginCount, LocalDateTime lockedUntil,
                           String roleCode, String roleName) { }
}
