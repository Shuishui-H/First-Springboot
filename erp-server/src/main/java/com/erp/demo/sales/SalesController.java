package com.erp.demo.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesController {
    private final SalesService salesService;
    public SalesController(SalesService salesService) { this.salesService = salesService; }
    @GetMapping @PreAuthorize("hasAuthority('sales:order:list')") public List<SalesOrder> findAll(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status) { return salesService.findOrders(keyword, status); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:order:list')") public SalesOrder findById(@PathVariable Long id) { return salesService.findOrder(id); }
    @PostMapping @PreAuthorize("hasAuthority('sales:order:create')") @ResponseStatus(HttpStatus.CREATED) public SalesOrder create(@Valid @RequestBody SalesOrderRequest request) { return salesService.create(request); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('sales:order:create')") public SalesOrder update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) { return salesService.update(id, request); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('sales:order:create')") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { salesService.delete(id); }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('sales:order:create')") public SalesOrder submit(@PathVariable Long id) { return salesService.submit(id); }
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('sales:order:approve')") public SalesOrder approve(@PathVariable Long id, @RequestBody(required = false) SalesActionRequest request) { return salesService.approve(id, request == null ? null : request.comment()); }
    @PostMapping("/{id}/reject") @PreAuthorize("hasAuthority('sales:order:approve')") public SalesOrder reject(@PathVariable Long id, @RequestBody(required = false) SalesActionRequest request) { return salesService.reject(id, request == null ? null : request.comment()); }
    @PostMapping("/{id}/void") @PreAuthorize("hasAuthority('sales:order:create')") public SalesOrder voidOrder(@PathVariable Long id, @RequestBody(required = false) SalesActionRequest request) { return salesService.voidOrder(id, request == null ? null : request.comment()); }
    @GetMapping("/shippable") @PreAuthorize("hasAuthority('sales:stockout:list')") public List<SalesOrder> shippable() { return salesService.findShippableOrders(); }
}
