package com.erp.demo.procurement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SupplierService {

    private final JdbcTemplate jdbcTemplate;

    private final AtomicLong nextId = new AtomicLong(1003);
    private final List<Supplier> suppliers = new ArrayList<>(List.of(
            new Supplier(1001L, "SUP-0001", "上海优采办公用品有限公司", "王玲", "13800000001", "启用"),
            new Supplier(1002L, "SUP-0002", "深圳智联办公设备有限公司", "陈涛", "13800000002", "启用")
    ));

    public SupplierService() { this.jdbcTemplate = null; }

    @Autowired
    public SupplierService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) { this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable(); }

    public synchronized List<Supplier> findAll(String keyword, String status) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim();
            return jdbcTemplate.query("""
                    SELECT id, code, name, contact, phone, status FROM md_supplier
                     WHERE (? = '' OR code LIKE CONCAT('%', ?, '%') OR name LIKE CONCAT('%', ?, '%'))
                       AND (? IS NULL OR ? = '' OR ? = '全部状态' OR status = ?)
                     ORDER BY id
                    """, (rs, row) -> new Supplier(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("contact"), rs.getString("phone"), rs.getInt("status") == 1 ? "启用" : "停用"),
                    query, query, query, status, status, status, "启用".equals(status) ? 1 : 0);
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return suppliers.stream()
                .filter(item -> query.isBlank() || item.code().toLowerCase().contains(query)
                        || item.name().toLowerCase().contains(query))
                .filter(item -> status == null || status.isBlank() || status.equals("全部状态")
                        || item.status().equals(status))
                .sorted(Comparator.comparing(Supplier::id))
                .toList();
    }

    public synchronized Supplier findById(Long id) {
        if (relationalDataAvailable()) return findAll(null, null).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "供应商不存在"));
        return suppliers.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "供应商不存在"));
    }

    public synchronized Supplier create(SupplierRequest request) {
        if (relationalDataAvailable()) {
            assertCodeAvailableRelational(request.code(), null);
            jdbcTemplate.update("INSERT INTO md_supplier (code, name, contact, phone, status, created_by, updated_by) VALUES (?, ?, ?, ?, ?, 0, 0)",
                    request.code().trim(), request.name().trim(), blankToNull(request.contact()), blankToNull(request.phone()), enabled(request.status()));
            return findAll(null, null).stream().filter(item -> item.code().equalsIgnoreCase(request.code().trim())).findFirst().orElseThrow();
        }
        assertCodeAvailable(request.code(), null);
        Supplier supplier = new Supplier(nextId.getAndIncrement(), request.code().trim(), request.name().trim(),
                blankToNull(request.contact()), blankToNull(request.phone()), request.status());
        suppliers.add(supplier);
        return supplier;
    }

    public synchronized Supplier update(Long id, SupplierRequest request) {
        if (relationalDataAvailable()) {
            findById(id); assertCodeAvailableRelational(request.code(), id);
            jdbcTemplate.update("UPDATE md_supplier SET code = ?, name = ?, contact = ?, phone = ?, status = ?, updated_by = 0 WHERE id = ?",
                    request.code().trim(), request.name().trim(), blankToNull(request.contact()), blankToNull(request.phone()), enabled(request.status()), id);
            return findById(id);
        }
        Supplier current = findById(id);
        assertCodeAvailable(request.code(), id);
        Supplier updated = new Supplier(current.id(), request.code().trim(), request.name().trim(),
                blankToNull(request.contact()), blankToNull(request.phone()), request.status());
        suppliers.set(suppliers.indexOf(current), updated);
        return updated;
    }

    private void assertCodeAvailable(String code, Long currentId) {
        if (suppliers.stream().anyMatch(item -> item.code().equalsIgnoreCase(code.trim())
                && !item.id().equals(currentId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "供应商编码已存在");
        }
    }

    private void assertCodeAvailableRelational(String code, Long currentId) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_supplier WHERE LOWER(code) = LOWER(?) AND (? IS NULL OR id <> ?)", Integer.class, code.trim(), currentId, currentId) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "供应商编码已存在");
    }
    private int enabled(String status) { return "启用".equals(status) ? 1 : 0; }
    private boolean relationalDataAvailable() { return jdbcTemplate != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_supplier", Integer.class) > 0; }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public synchronized State exportState() { return new State(List.copyOf(suppliers), nextId.get()); }
    public synchronized void restoreState(State state) { suppliers.clear(); suppliers.addAll(state.suppliers()); nextId.set(state.nextId()); }
    public record State(List<Supplier> suppliers, long nextId) {}
}
