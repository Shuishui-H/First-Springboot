package com.erp.demo.warehouse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 按一个仓库执行的库存盘点单。 */
public record StocktakeOrder(Long id, String stocktakeNo, Long warehouseId, String warehouseName,
                             LocalDate stocktakeDate, String status, String remark, List<StocktakeOrderItem> items,
                             String createdBy, LocalDateTime createdAt, String confirmedBy,
                             LocalDateTime confirmedAt, long version) {
}
