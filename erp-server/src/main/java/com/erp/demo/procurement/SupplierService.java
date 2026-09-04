package com.erp.demo.procurement;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SupplierService {

    private final AtomicLong nextId = new AtomicLong(1003);
    private final List<Supplier> suppliers = new ArrayList<>(List.of(
            new Supplier(1001L, "SUP-0001", "上海优采办公用品有限公司", "王玲", "13800000001", "启用"),
            new Supplier(1002L, "SUP-0002", "深圳智联办公设备有限公司", "陈涛", "13800000002", "启用")
    ));

    public synchronized List<Supplier> findAll(String keyword, String status) {
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
        return suppliers.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "供应商不存在"));
    }

    public synchronized Supplier create(SupplierRequest request) {
        assertCodeAvailable(request.code(), null);
        Supplier supplier = new Supplier(nextId.getAndIncrement(), request.code().trim(), request.name().trim(),
                blankToNull(request.contact()), blankToNull(request.phone()), request.status());
        suppliers.add(supplier);
        return supplier;
    }

    public synchronized Supplier update(Long id, SupplierRequest request) {
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
