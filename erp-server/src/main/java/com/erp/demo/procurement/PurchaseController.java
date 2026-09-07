package com.erp.demo.procurement;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:order:list')")
    public List<PurchaseOrder> findAll(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status) {
        return purchaseService.findOrders(keyword, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:order:list')")
    public PurchaseOrder findById(@PathVariable Long id) {
        return purchaseService.findOrder(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchase:order:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder create(@Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:order:create')")
    public PurchaseOrder update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:order:create')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        purchaseService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase:order:create')")
    public PurchaseOrder submit(@PathVariable Long id) {
        return purchaseService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase:order:approve')")
    public PurchaseOrder approve(@PathVariable Long id, @RequestBody(required = false) PurchaseActionRequest request) {
        return purchaseService.approve(id, request == null ? null : request.comment());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase:order:approve')")
    public PurchaseOrder reject(@PathVariable Long id, @RequestBody PurchaseActionRequest request) {
        return purchaseService.reject(id, request == null ? null : request.comment());
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('purchase:order:create')")
    public PurchaseOrder voidOrder(@PathVariable Long id, @RequestBody(required = false) PurchaseActionRequest request) {
        return purchaseService.voidOrder(id, request == null ? null : request.comment());
    }

    @GetMapping("/receivable")
    @PreAuthorize("hasAuthority('purchase:receipt:list')")
    public List<PurchaseOrder> receivable() {
        return purchaseService.findReceivableOrders();
    }
}
