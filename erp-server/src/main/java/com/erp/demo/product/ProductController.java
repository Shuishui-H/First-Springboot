package com.erp.demo.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import com.erp.demo.warehouse.WarehouseService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final WarehouseService warehouseService;

    public ProductController(ProductService productService, WarehouseService warehouseService) {
        this.productService = productService;
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:product:list')")
    public List<Product> findAll() {
        return productService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:product:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody ProductRequest request) {
        Product product = productService.create(request);
        warehouseService.syncProductStockToMainWarehouse(product);
        return product;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:product:manage')")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        Product product = productService.update(id, request);
        warehouseService.syncProductStockToMainWarehouse(product);
        return product;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:product:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
