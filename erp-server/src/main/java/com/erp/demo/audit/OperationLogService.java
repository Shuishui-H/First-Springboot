package com.erp.demo.audit;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("mysql")
public class OperationLogService {
    private final JdbcTemplate jdbcTemplate;

    public OperationLogService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void log(String module, String action, String businessType, Long businessId, String businessNo, String status) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String operator = authentication == null || "anonymousUser".equals(authentication.getName()) ? "系统任务" : authentication.getName();
        Long operatorId = "系统任务".equals(operator) ? null : jdbcTemplate.query("SELECT id FROM sys_user WHERE username = ?", 
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null, operator);
        jdbcTemplate.update("""
                INSERT INTO sys_operation_log (module, action, business_type, business_id, business_no, after_summary, operator_id, operator_name)
                VALUES (?, ?, ?, ?, ?, JSON_OBJECT('status', ?), ?, ?)
                """, module, action, businessType, businessId, businessNo, status, operatorId, operator);
    }

    public List<OperationLog> find(String module, String action, String businessNo, Integer limit) {
        int size = limit == null ? 100 : limit;
        if (size < 1 || size > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 500 之间");
        StringBuilder sql = new StringBuilder("SELECT id, module, action, business_type, business_id, business_no, operator_id, operator_name, created_at FROM sys_operation_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (module != null && !module.isBlank()) { sql.append(" AND module = ?"); args.add(module.trim()); }
        if (action != null && !action.isBlank()) { sql.append(" AND action = ?"); args.add(action.trim()); }
        if (businessNo != null && !businessNo.isBlank()) { sql.append(" AND business_no LIKE ?"); args.add("%" + businessNo.trim() + "%"); }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?"); args.add(size);
        return jdbcTemplate.query(sql.toString(), (rs, row) -> new OperationLog(rs.getLong("id"), rs.getString("module"), rs.getString("action"),
                rs.getString("business_type"), rs.getObject("business_id", Long.class), rs.getString("business_no"),
                rs.getObject("operator_id", Long.class), rs.getString("operator_name"), rs.getTimestamp("created_at").toLocalDateTime()), args.toArray());
    }

    public record OperationLog(Long id, String module, String action, String businessType, Long businessId,
                               String businessNo, Long operatorId, String operatorName, LocalDateTime createdAt) {}
}
