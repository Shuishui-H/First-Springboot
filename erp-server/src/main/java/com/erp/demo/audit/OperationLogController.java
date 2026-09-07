package com.erp.demo.audit;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("mysql")
@RequestMapping("/api/system/operation-logs")
public class OperationLogController {
    private final OperationLogService service;
    public OperationLogController(OperationLogService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('system:operation-log:view')")
    public List<OperationLogService.OperationLog> find(@RequestParam(required = false) String module,
                                                        @RequestParam(required = false) String action,
                                                        @RequestParam(required = false) String businessNo,
                                                        @RequestParam(required = false) Integer limit) {
        return service.find(module, action, businessNo, limit);
    }
}
