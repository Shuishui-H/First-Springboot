package com.erp.demo.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private final JdbcTemplate jdbcTemplate;

    private final AtomicLong nextId = new AtomicLong(4);
    private final List<Product> products = new ArrayList<>(List.of(
            new Product(1L, "SP-1001", "A4 复印纸", "办公耗材", "包", new BigDecimal("26.80"), 120, 40, "启用"),
            new Product(2L, "SP-1002", "黑色签字笔", "办公耗材", "支", new BigDecimal("2.50"), 18, 30, "启用"),
            new Product(3L, "SP-2001", "无线鼠标", "办公设备", "个", new BigDecimal("79.00"), 36, 10, "启用")
    ));

    public ProductService() { this.jdbcTemplate = null; }

    @Autowired
    public ProductService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public synchronized List<Product> findAll() {
        if (relationalDataAvailable()) {
            return jdbcTemplate.query("""
                    SELECT p.id, p.sku, p.name, p.category, p.unit, p.price, p.safety_stock, p.status,
                           COALESCE(SUM(b.quantity), 0) AS stock
                      FROM md_product p LEFT JOIN wh_inventory_balance b ON b.product_id = p.id
                     GROUP BY p.id, p.sku, p.name, p.category, p.unit, p.price, p.safety_stock, p.status
                     ORDER BY p.id
                    """, (rs, row) -> new Product(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    rs.getString("category"), rs.getString("unit"), rs.getBigDecimal("price"), rs.getInt("stock"),
                    rs.getBigDecimal("safety_stock").intValue(), rs.getInt("status") == 1 ? "启用" : "停用"));
        }
        return products.stream()
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    public synchronized Product findById(Long id) {
        if (relationalDataAvailable()) {
            return findAll().stream().filter(product -> product.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        }
        return findExistingById(id);
    }

    public synchronized Product increaseStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库数量必须大于 0");
        }
        if (relationalDataAvailable()) return findById(id);
        Product existing = findExistingById(id);
        Product updated = new Product(existing.id(), existing.sku(), existing.name(), existing.category(),
                existing.unit(), existing.price(), existing.stock() + quantity, existing.safetyStock(), existing.status());
        products.set(products.indexOf(existing), updated);
        return updated;
    }

    public synchronized Product decreaseStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "出库数量必须大于 0");
        }
        if (relationalDataAvailable()) return findById(id);
        Product existing = findExistingById(id);
        if (existing.stock() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品库存不足");
        }
        Product updated = new Product(existing.id(), existing.sku(), existing.name(), existing.category(),
                existing.unit(), existing.price(), existing.stock() - quantity, existing.safetyStock(), existing.status());
        products.set(products.indexOf(existing), updated);
        return updated;
    }

    public synchronized Product create(ProductRequest request) {
        if (relationalDataAvailable()) {
            assertSkuAvailableRelational(request.sku(), null);
            jdbcTemplate.update("INSERT INTO md_product (sku, name, category, unit, price, safety_stock, status, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)",
                    request.sku().trim(), request.name().trim(), request.category().trim(), request.unit().trim(), request.price(), request.safetyStock(), enabled(request.status()));
            return findAll().stream().filter(product -> product.sku().equalsIgnoreCase(request.sku().trim())).findFirst().orElseThrow();
        }
        assertSkuAvailable(request.sku(), null);
        Product product = toProduct(nextId.getAndIncrement(), request);
        products.add(product);
        return product;
    }

    public synchronized Product update(Long id, ProductRequest request) {
        if (relationalDataAvailable()) {
            findById(id);
            assertSkuAvailableRelational(request.sku(), id);
            jdbcTemplate.update("UPDATE md_product SET sku = ?, name = ?, category = ?, unit = ?, price = ?, safety_stock = ?, status = ?, updated_by = 0 WHERE id = ?",
                    request.sku().trim(), request.name().trim(), request.category().trim(), request.unit().trim(), request.price(), request.safetyStock(), enabled(request.status()), id);
            return new Product(id, request.sku().trim(), request.name().trim(), request.category().trim(), request.unit().trim(), request.price(), request.stock(), request.safetyStock(), request.status());
        }
        Product existing = findById(id);
        assertSkuAvailable(request.sku(), id);
        Product updated = toProduct(existing.id(), request);
        products.set(products.indexOf(existing), updated);
        return updated;
    }

    public synchronized void delete(Long id) {
        if (relationalDataAvailable()) {
            findById(id);
            try { jdbcTemplate.update("DELETE FROM md_product WHERE id = ?", id); }
            catch (org.springframework.dao.DataAccessException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "商品已被业务单据引用，不能删除"); }
            return;
        }
        Product product = findById(id);
        products.remove(product);
    }

    private Product findExistingById(Long id) {
        return products.stream()
                .filter(product -> product.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    private void assertSkuAvailable(String sku, Long currentId) {
        boolean exists = products.stream().anyMatch(product -> product.sku().equalsIgnoreCase(sku)
                && !product.id().equals(currentId));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品编码已存在");
        }
    }

    private void assertSkuAvailableRelational(String sku, Long currentId) {
        boolean exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_product WHERE LOWER(sku) = LOWER(?) AND (? IS NULL OR id <> ?)", Integer.class, sku.trim(), currentId, currentId) > 0;
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "商品编码已存在");
    }

    private int enabled(String status) { return "启用".equals(status) ? 1 : 0; }
    private boolean relationalDataAvailable() { return jdbcTemplate != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_product", Integer.class) > 0; }

    private Product toProduct(Long id, ProductRequest request) {
        return new Product(id, request.sku(), request.name(), request.category(), request.unit(),
                request.price(), request.stock(), request.safetyStock(), request.status());
    }

    public synchronized State exportState() { return new State(List.copyOf(products), nextId.get()); }
    public synchronized void restoreState(State state) { products.clear(); products.addAll(state.products()); nextId.set(state.nextId()); }
    public record State(List<Product> products, long nextId) {}
}
