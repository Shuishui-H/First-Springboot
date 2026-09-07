package com.erp.demo.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:customer:list')")
    public List<Customer> findAll(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String status) {
        return customerService.findAll(keyword, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:customer:list')")
    public Customer findById(@PathVariable Long id) { return customerService.findById(id); }

    @PostMapping
    @PreAuthorize("hasAuthority('base:customer:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@Valid @RequestBody CustomerRequest request) { return customerService.create(request); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:customer:manage')")
    public Customer update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }
}
