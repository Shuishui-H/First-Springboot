package com.erp.demo.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/sales-returns")
public class SalesReturnController {
    private final SalesService salesService;
    public SalesReturnController(SalesService salesService) { this.salesService = salesService; }
    @GetMapping @PreAuthorize("hasAuthority('sales:return:list')") public List<SalesReturnOrder> findAll(@RequestParam(required = false) String keyword) { return salesService.findReturnOrders(keyword); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:return:list')") public SalesReturnOrder findById(@PathVariable Long id) { return salesService.findReturn(id); }
    @PostMapping @PreAuthorize("hasAuthority('sales:return:create')") @ResponseStatus(HttpStatus.CREATED) public SalesReturnOrder create(@Valid @RequestBody SalesReturnRequest request) { return salesService.createReturn(request); }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('sales:return:create')") public SalesReturnOrder submit(@PathVariable Long id) { return salesService.submitReturn(id); }
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('sales:return:approve')") public SalesReturnOrder approve(@PathVariable Long id, @RequestBody(required = false) SalesActionRequest request) { return salesService.approveReturn(id, request == null ? null : request.comment()); }
    @PostMapping("/{id}/reject") @PreAuthorize("hasAuthority('sales:return:approve')") public SalesReturnOrder reject(@PathVariable Long id, @RequestBody(required = false) SalesActionRequest request) { return salesService.rejectReturn(id, request == null ? null : request.comment()); }
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('sales:return:confirm')") public SalesReturnOrder confirm(@PathVariable Long id) { return salesService.confirmReturn(id); }
    @PostMapping("/{id}/void") @PreAuthorize("hasAuthority('sales:return:create')") public SalesReturnOrder voidReturn(@PathVariable Long id, @RequestBody(required = false) SalesActionRequest request) { return salesService.voidReturn(id, request == null ? null : request.comment()); }
}
