package com.erp.demo.report;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;
    public ReportController(ReportService reportService) { this.reportService = reportService; }
    @GetMapping("/dashboard") @PreAuthorize("hasAuthority('report:view')") public DashboardReport dashboard() { return reportService.dashboard(); }
    @GetMapping("/purchase-analysis") @PreAuthorize("hasAuthority('report:view')") public PurchaseAnalysis purchaseAnalysis() { return reportService.purchaseAnalysis(); }
    @GetMapping("/inventory-balance") @PreAuthorize("hasAuthority('inventory:balance:view')") public List<InventoryBalanceRow> inventoryBalance() { return reportService.inventoryBalance(); }
    @GetMapping("/low-stock") @PreAuthorize("hasAuthority('report:view')") public List<InventoryWarningRow> lowStock() { return reportService.lowStock(); }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAuthority('report:view')")
    public ResponseEntity<byte[]> export(@RequestParam String type) {
        String filename;
        String csv;
        switch (type) {
            case "inventory" -> { filename = "库存余额报表"; csv = inventoryCsv(); }
            case "warning" -> { filename = "库存预警报表"; csv = warningCsv(); }
            case "purchase" -> { filename = "采购分析报表"; csv = purchaseCsv(); }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的报表类型：" + type);
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "_" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private String inventoryCsv() {
        StringBuilder builder = new StringBuilder("商品编码,商品名称,分类,单位,期初库存,累计入库,累计出库,当前库存,安全库存,货值,状态\n");
        for (InventoryBalanceRow row : reportService.inventoryBalance()) builder.append(csv(row.sku())).append(',').append(csv(row.name())).append(',').append(csv(row.category())).append(',').append(csv(row.unit())).append(',').append(row.openingStock()).append(',').append(row.stockIn()).append(',').append(row.stockOut()).append(',').append(row.currentStock()).append(',').append(row.safetyStock()).append(',').append(row.stockValue()).append(',').append(csv(row.status())).append('\n');
        return builder.toString();
    }
    private String warningCsv() {
        StringBuilder builder = new StringBuilder("商品编码,商品名称,分类,预警类型,当前库存,安全库存,缺口,商品状态\n");
        for (InventoryWarningRow row : reportService.lowStock()) builder.append(csv(row.sku())).append(',').append(csv(row.name())).append(',').append(csv(row.category())).append(',').append(csv(row.warningType())).append(',').append(row.currentStock()).append(',').append(row.safetyStock()).append(',').append(row.gap()).append(',').append(csv(row.status())).append('\n');
        return builder.toString();
    }
    private String purchaseCsv() {
        PurchaseAnalysis analysis = reportService.purchaseAnalysis();
        StringBuilder builder = new StringBuilder("报表,维度,指标,金额,备注\n");
        analysis.suppliers().forEach(row -> builder.append("采购分析,供应商,").append(csv(row.name())).append(',').append(row.orderAmount()).append(",订单数 ").append(row.orderCount()).append(" / 已入库金额 ").append(row.receivedAmount()).append('\n'));
        analysis.statuses().forEach(row -> builder.append("采购分析,订单状态,").append(csv(row.status())).append(',').append(row.orderAmount()).append(",订单数 ").append(row.orderCount()).append('\n'));
        analysis.months().forEach(row -> builder.append("采购趋势,月份,").append(row.month()).append(',').append(row.orderAmount()).append(",入库金额 ").append(row.receivedAmount()).append('\n'));
        return builder.toString();
    }
    private String csv(String value) { return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\""; }
}
