package com.erp.demo.warehouse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 仓库间调拨单。确认后仅改变分仓库存，不改变商品全仓总库存。 */
public record TransferOrder(Long id, String transferNo, Long fromWarehouseId, String fromWarehouseName,
                            Long toWarehouseId, String toWarehouseName, LocalDate transferDate, String status,
                            String remark, List<TransferOrderItem> items, String createdBy, LocalDateTime createdAt,
                            String confirmedBy, LocalDateTime confirmedAt, long version) {
}
