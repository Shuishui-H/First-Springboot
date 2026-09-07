package com.erp.demo.sales;

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
public class CustomerService {
    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong nextId = new AtomicLong(6003);
    private final List<Customer> customers = new ArrayList<>(List.of(
            new Customer(6001L, "CUS-1001", "上海星河办公有限公司", "周敏", "13800001001", "启用"),
            new Customer(6002L, "CUS-1002", "杭州云启科技有限公司", "陈浩", "13900001002", "启用")
    ));

    public CustomerService() { this.jdbcTemplate = null; }

    @Autowired
    public CustomerService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) { this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable(); }

    public synchronized List<Customer> findAll(String keyword, String status) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim();
            return jdbcTemplate.query("""
                    SELECT id, code, name, contact, phone, status FROM md_customer
                     WHERE (? = '' OR code LIKE CONCAT('%', ?, '%') OR name LIKE CONCAT('%', ?, '%') OR COALESCE(contact, '') LIKE CONCAT('%', ?, '%'))
                       AND (? IS NULL OR ? = '' OR ? = '全部状态' OR status = ?)
                     ORDER BY id
                    """, (rs, row) -> new Customer(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("contact"), rs.getString("phone"), rs.getInt("status") == 1 ? "启用" : "停用"),
                    query, query, query, query, status, status, status, "启用".equals(status) ? 1 : 0);
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return customers.stream()
                .filter(item -> query.isBlank() || item.code().toLowerCase().contains(query)
                        || item.name().toLowerCase().contains(query) || String.valueOf(item.contact()).contains(query))
                .filter(item -> status == null || status.isBlank() || "全部状态".equals(status) || item.status().equals(status))
                .sorted(Comparator.comparing(Customer::id))
                .toList();
    }

    public synchronized Customer findById(Long id) {
        if (relationalDataAvailable()) return findAll(null, null).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在"));
        return customers.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在"));
    }

    public synchronized Customer create(CustomerRequest request) {
        if (relationalDataAvailable()) {
            assertCodeRelational(request.code(), null);
            jdbcTemplate.update("INSERT INTO md_customer (code, name, contact, phone, status, created_by, updated_by) VALUES (?, ?, ?, ?, ?, 0, 0)",
                    request.code().trim(), request.name().trim(), request.contact(), request.phone(), enabled(request.status()));
            return findAll(null, null).stream().filter(item -> item.code().equalsIgnoreCase(request.code().trim())).findFirst().orElseThrow();
        }
        assertCode(request.code(), null);
        Customer customer = toCustomer(nextId.getAndIncrement(), request);
        customers.add(customer);
        return customer;
    }

    public synchronized Customer update(Long id, CustomerRequest request) {
        if (relationalDataAvailable()) {
            findById(id); assertCodeRelational(request.code(), id);
            jdbcTemplate.update("UPDATE md_customer SET code = ?, name = ?, contact = ?, phone = ?, status = ?, updated_by = 0 WHERE id = ?",
                    request.code().trim(), request.name().trim(), request.contact(), request.phone(), enabled(request.status()), id);
            return findById(id);
        }
        Customer current = findById(id);
        assertCode(request.code(), id);
        Customer updated = toCustomer(current.id(), request);
        customers.set(customers.indexOf(current), updated);
        return updated;
    }

    private Customer toCustomer(Long id, CustomerRequest request) {
        return new Customer(id, request.code().trim(), request.name().trim(), request.contact(), request.phone(),
                request.status() == null || request.status().isBlank() ? "启用" : request.status());
    }

    private void assertCode(String code, Long currentId) {
        if (customers.stream().anyMatch(item -> item.code().equalsIgnoreCase(code.trim())
                && !item.id().equals(currentId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "客户编码已存在");
        }
    }

    private void assertCodeRelational(String code, Long currentId) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_customer WHERE LOWER(code) = LOWER(?) AND (? IS NULL OR id <> ?)", Integer.class, code.trim(), currentId, currentId) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "客户编码已存在");
    }
    private int enabled(String status) { return "启用".equals(status) ? 1 : 0; }
    private boolean relationalDataAvailable() { return jdbcTemplate != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_customer", Integer.class) > 0; }

    public synchronized State exportState() { return new State(List.copyOf(customers), nextId.get()); }
    public synchronized void restoreState(State state) { customers.clear(); customers.addAll(state.customers()); nextId.set(state.nextId()); }
    public record State(List<Customer> customers, long nextId) {}
}
