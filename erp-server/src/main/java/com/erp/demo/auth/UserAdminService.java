package com.erp.demo.auth;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Profile("mysql")
public class UserAdminService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummary> users(String keyword, String roleCode, Integer status) {
        String query = keyword == null ? "" : keyword.trim();
        return jdbcTemplate.query("""
                SELECT u.id, u.username, u.real_name, r.role_code, r.role_name, u.status, u.created_at, u.must_change_password
                  FROM sys_user u JOIN sys_role r ON r.id = u.role_id
                 WHERE (? = '' OR u.username LIKE CONCAT('%', ?, '%') OR COALESCE(u.real_name, '') LIKE CONCAT('%', ?, '%'))
                   AND (? IS NULL OR r.role_code = ?) AND (? IS NULL OR u.status = ?)
                 ORDER BY u.id
                """, (rs, row) -> new UserSummary(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"),
                rs.getString("role_code"), rs.getString("role_name"), rs.getInt("status"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getBoolean("must_change_password")),
                query, query, query, roleCode, roleCode, status, status);
    }

    @Transactional
    public UserSummary create(UserAdminRequest request) {
        if (request.password() == null || request.password().isBlank()) throw badRequest("新用户必须设置初始密码");
        long roleId = roleId(request.roleCode());
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO sys_user (username, password_hash, real_name, role_id, status, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 1, ?, ?)
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, request.username().trim());
                statement.setString(2, passwordEncoder.encode(request.password()));
                statement.setString(3, blank(request.realName()));
                statement.setLong(4, roleId);
                statement.setLong(5, actorId());
                statement.setLong(6, actorId());
                return statement;
            }, keyHolder);
            long id = keyHolder.getKey().longValue();
            log("USER_CREATE", id, request.username().trim());
            return users(request.username().trim(), null, null).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        } catch (DataAccessException exception) {
            throw conflict("用户名已存在或用户数据不合法");
        }
    }

    @Transactional
    public UserSummary update(Long id, UserAdminRequest request) {
        UserSummary current = findUser(id);
        long roleId = roleId(request.roleCode());
        try {
            jdbcTemplate.update("UPDATE sys_user SET real_name = ?, role_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    blank(request.realName()), roleId, actorId(), id);
            log("USER_UPDATE", id, current.username());
            return findUser(id);
        } catch (DataAccessException exception) {
            throw conflict("用户信息更新失败");
        }
    }

    @Transactional
    public UserSummary changeStatus(Long id, int status) {
        if (status != 0 && status != 1) throw badRequest("用户状态不合法");
        UserSummary current = findUser(id);
        if (current.username().equals(currentUsername()) && status == 0) throw badRequest("不能停用当前登录账号");
        jdbcTemplate.update("UPDATE sys_user SET status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, actorId(), id);
        log("USER_STATUS", id, current.username() + " -> " + status);
        return findUser(id);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        if (password == null || password.length() < 8 || password.length() > 100) throw badRequest("重置密码长度应为 8 到 100 位");
        UserSummary current = findUser(id);
        jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, must_change_password = 1, failed_login_count = 0, locked_until = NULL, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                passwordEncoder.encode(password), actorId(), id);
        log("USER_PASSWORD_RESET", id, current.username());
    }

    public List<RoleSummary> roles() {
        return jdbcTemplate.query("SELECT id, role_code, role_name, status, remark FROM sys_role ORDER BY id", (rs, row) ->
                new RoleSummary(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name"), rs.getInt("status"), rs.getString("remark"), permissionIds(rs.getLong("id"))));
    }

    @Transactional
    public RoleSummary changeRoleStatus(Long id, int status) {
        RoleSummary role = findRole(id);
        if ("admin".equals(role.roleCode()) && status == 0) throw badRequest("管理员角色不能停用");
        jdbcTemplate.update("UPDATE sys_role SET status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, actorId(), id);
        log("ROLE_STATUS", id, role.roleCode() + " -> " + status);
        return findRole(id);
    }

    @Transactional
    public RoleSummary updatePermissions(Long id, List<Long> permissionIds) {
        RoleSummary role = findRole(id);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", id);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO sys_role_permission (role_id, permission_id, created_by) VALUES (?, ?, ?)",
                    permissionIds.stream().distinct().map(permissionId -> new Object[]{id, permissionId, actorId()}).toList());
        }
        log("ROLE_PERMISSION_UPDATE", id, role.roleCode());
        return findRole(id);
    }

    public List<PermissionSummary> permissions() {
        return jdbcTemplate.query("SELECT id, parent_id, name, permission_code, permission_type, path, sort_no, status FROM sys_permission ORDER BY parent_id, sort_no, id",
                (rs, row) -> new PermissionSummary(rs.getLong("id"), rs.getLong("parent_id"), rs.getString("name"), rs.getString("permission_code"),
                        rs.getString("permission_type"), rs.getString("path"), rs.getInt("sort_no"), rs.getInt("status")));
    }

    private UserSummary findUser(Long id) { return users(null, null, null).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> notFound("用户不存在")); }
    private RoleSummary findRole(Long id) { return roles().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> notFound("角色不存在")); }
    private long roleId(String roleCode) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM sys_role WHERE role_code = ? AND status = 1", Long.class, roleCode);
        if (ids.isEmpty()) throw badRequest("角色不存在或已停用");
        return ids.get(0);
    }
    private List<Long> permissionIds(long roleId) { return jdbcTemplate.queryForList("SELECT permission_id FROM sys_role_permission WHERE role_id = ? ORDER BY permission_id", Long.class, roleId); }
    private Long actorId() { String username = currentUsername(); return username == null ? 0L : jdbcTemplate.query("SELECT id FROM sys_user WHERE username = ?", rs -> rs.next() ? rs.getLong(1) : 0L, username); }
    private String currentUsername() { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); return auth == null || "anonymousUser".equals(auth.getName()) ? null : auth.getName(); }
    private void log(String action, long id, String summary) { jdbcTemplate.update("INSERT INTO sys_operation_log (module, action, business_type, business_id, after_summary, operator_id, operator_name) VALUES ('SYSTEM', ?, 'USER_ROLE', ?, JSON_OBJECT('summary', ?), ?, ?)", action, id, summary, actorId(), currentUsername()); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    public record UserSummary(Long id, String username, String realName, String roleCode, String roleName, int status, java.time.LocalDateTime createdAt, boolean mustChangePassword) {}
    public record RoleSummary(Long id, String roleCode, String roleName, int status, String remark, List<Long> permissionIds) {}
    public record PermissionSummary(Long id, Long parentId, String name, String permissionCode, String permissionType, String path, int sortNo, int status) {}
}
