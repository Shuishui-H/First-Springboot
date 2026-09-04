package com.erp.demo.procurement;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-receipts")
public class PurchaseReceiptController {

    private final PurchaseService purchaseService;

    public PurchaseReceiptController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    public List<PurchaseReceipt> findAll() {
        return purchaseService.findReceipts();
    }

    @GetMapping("/{id}")
    public PurchaseReceipt findById(@PathVariable Long id) {
        return purchaseService.findReceipt(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseReceipt create(@Valid @RequestBody PurchaseReceiptRequest request) {
        return purchaseService.createReceipt(request);
    }

    @PostMapping("/{id}/confirm")
    public PurchaseReceipt confirm(@PathVariable Long id) {
        return purchaseService.confirmReceipt(id);
    }
}
