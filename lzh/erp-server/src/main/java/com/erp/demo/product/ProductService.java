package com.erp.demo.product;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private final AtomicLong nextId = new AtomicLong(4);
    private final List<Product> products = new ArrayList<>(List.of(
            new Product(1L, "SP-1001", "A4 复印纸", "办公耗材", "包", new BigDecimal("26.80"), 120, 40, "启用"),
            new Product(2L, "SP-1002", "黑色签字笔", "办公耗材", "支", new BigDecimal("2.50"), 18, 30, "启用"),
            new Product(3L, "SP-2001", "无线鼠标", "办公设备", "个", new BigDecimal("79.00"), 36, 10, "启用")
    ));

    public synchronized List<Product> findAll() {
        return products.stream()
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    public synchronized Product findById(Long id) {
        return findExistingById(id);
    }

    public synchronized Product increaseStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库数量必须大于 0");
        }
        Product existing = findExistingById(id);
        Product updated = new Product(existing.id(), existing.sku(), existing.name(), existing.category(),
                existing.unit(), existing.price(), existing.stock() + quantity, existing.safetyStock(), existing.status());
        products.set(products.indexOf(existing), updated);
        return updated;
    }

    public synchronized Product create(ProductRequest request) {
        assertSkuAvailable(request.sku(), null);
        Product product = toProduct(nextId.getAndIncrement(), request);
        products.add(product);
        return product;
    }

    public synchronized Product update(Long id, ProductRequest request) {
        Product existing = findById(id);
        assertSkuAvailable(request.sku(), id);
        Product updated = toProduct(existing.id(), request);
        products.set(products.indexOf(existing), updated);
        return updated;
    }

    public synchronized void delete(Long id) {
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

    private Product toProduct(Long id, ProductRequest request) {
        return new Product(id, request.sku(), request.name(), request.category(), request.unit(),
                request.price(), request.stock(), request.safetyStock(), request.status());
    }
}
