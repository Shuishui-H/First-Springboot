package com.erp.demo.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/stock-out-orders")
public class StockOutController {
    private final SalesService salesService;
    public StockOutController(SalesService salesService) { this.salesService = salesService; }
    @GetMapping @PreAuthorize("hasAuthority('sales:stockout:list')") public List<StockOutOrder> findAll(@RequestParam(required = false) String keyword) { return salesService.findStockOutOrders(keyword); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:stockout:list')") public StockOutOrder findById(@PathVariable Long id) { return salesService.findStockOut(id); }
    @PostMapping @PreAuthorize("hasAuthority('sales:stockout:confirm')") @ResponseStatus(HttpStatus.CREATED) public StockOutOrder create(@Valid @RequestBody StockOutRequest request) { return salesService.createStockOut(request); }
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('sales:stockout:confirm')") public StockOutOrder confirm(@PathVariable Long id) { return salesService.confirmStockOut(id); }
    @GetMapping("/returnable") @PreAuthorize("hasAuthority('sales:return:list')") public List<StockOutOrder> returnable() { return salesService.findReturnableStockOuts(); }
}
