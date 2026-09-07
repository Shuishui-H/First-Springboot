package com.erp.demo.warehouse;

import com.erp.demo.audit.InMemoryOperationLogService;
import com.erp.demo.product.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarehouseServiceTest {
    @Test
    void transferConfirmationIsIdempotent() {
        WarehouseService service = new WarehouseService(new ProductService(), new InMemoryOperationLogService());
        TransferOrder order = service.createTransfer(new TransferOrderRequest(1L, 2L, null, null,
                List.of(new TransferOrderRequest.Item(2L, 2, null))));

        assertEquals("已确认", service.confirmTransfer(order.id()).status());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.confirmTransfer(order.id()));
        assertEquals(409, exception.getStatusCode().value());
        assertEquals(16, service.findBalances(1L, 2L, null).get(0).quantity());
    }

    @Test
    void stocktakeRejectsChangedBalanceVersion() {
        WarehouseService service = new WarehouseService(new ProductService(), new InMemoryOperationLogService());
        StocktakeOrder order = service.createStocktake(new StocktakeOrderRequest(1L, null, null,
                List.of(new StocktakeOrderRequest.Item(2L, 17, "复核差异"))));
        service.createMovement(new WarehouseController.StockMovementRequest(1L, 2L, "手工入库", 1, "并发变更"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.confirmStocktake(order.id()));
        assertEquals(409, exception.getStatusCode().value());
    }
}
