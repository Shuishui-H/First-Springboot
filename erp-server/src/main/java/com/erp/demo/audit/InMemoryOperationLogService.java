package com.erp.demo.audit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** V1/V2 内存演示不连接数据库时的审计适配器。 */
@Service
@Profile("inmemory")
public class InMemoryOperationLogService extends OperationLogService {
    public InMemoryOperationLogService() { super(null); }
    @Override
    public void log(String module, String action, String businessType, Long businessId, String businessNo, String status) { }
}
