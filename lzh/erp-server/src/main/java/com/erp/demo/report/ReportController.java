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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增：业务报表模块 —— 报表查询与 CSV 导出接口。
 * 遵循项目既有约定：接口并入既有 /api 体系（前缀统一为 /api/reports/*），与商品/采购模块同款分层。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 新增：经营看板（RPT-01）。 */
    @GetMapping("/dashboard")
    public DashboardReport dashboard() {
        return reportService.dashboard();
    }

    /** 新增：采购分析（RPT-03）。 */
    @GetMapping("/purchase-analysis")
    public PurchaseAnalysis purchaseAnalysis() {
        return reportService.purchaseAnalysis();
    }

    /** 新增：库存余额报表（RPT-04）。 */
    @GetMapping("/inventory-balance")
    public List<InventoryBalanceRow> inventoryBalance() {
        return reportService.inventoryBalance();
    }

    /** 新增：库存预警（RPT-06）。 */
    @GetMapping("/low-stock")
    public List<InventoryWarningRow> lowStock() {
        return reportService.lowStock();
    }

    /**
     * 新增：报表 CSV 导出（RPT-07）。type 取值：inventory | warning | purchase。
     * 返回带 UTF-8 BOM 的 CSV，避免 Excel 打开中文乱码。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam String type) {
        String csv;
        String filename;
        switch (type) {
            case "inventory" -> {
                filename = "库存余额报表";
                csv = inventoryCsv();
            }
            case "warning" -> {
                filename = "库存预警报表";
                csv = warningCsv();
            }
            case "purchase" -> {
                filename = "采购分析报表";
                csv = purchaseCsv();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的报表类型：" + type);
        }
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "_" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    // ==================== CSV 拼接（新增） ====================
    private String inventoryCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("商品编码,商品名称,分类,单位,期初库存,累计入库,累计出库,当前库存,安全库存,货值,状态\n");
        for (InventoryBalanceRow row : reportService.inventoryBalance()) {
            builder.append(csv(row.sku())).append(',')
                    .append(csv(row.name())).append(',')
                    .append(csv(row.category())).append(',')
                    .append(csv(row.unit())).append(',')
                    .append(row.openingStock()).append(',')
                    .append(row.stockIn()).append(',')
                    .append(row.stockOut()).append(',')
                    .append(row.currentStock()).append(',')
                    .append(row.safetyStock()).append(',')
                    .append(row.stockValue()).append(',')
                    .append(csv(row.status())).append('\n');
        }
        return builder.toString();
    }

    private String warningCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("商品编码,商品名称,分类,预警类型,当前库存,安全库存,缺口,商品状态\n");
        for (InventoryWarningRow row : reportService.lowStock()) {
            builder.append(csv(row.sku())).append(',')
                    .append(csv(row.name())).append(',')
                    .append(csv(row.category())).append(',')
                    .append(csv(row.warningType())).append(',')
                    .append(row.currentStock()).append(',')
                    .append(row.safetyStock()).append(',')
                    .append(row.gap()).append(',')
                    .append(csv(row.status())).append('\n');
        }
        return builder.toString();
    }

    private String purchaseCsv() {
        PurchaseAnalysis analysis = reportService.purchaseAnalysis();
        StringBuilder builder = new StringBuilder();
        builder.append("报表,维度,指标,金额,备注\n");
        // 供应商维度
        for (PurchaseAnalysis.SupplierLine line : analysis.suppliers()) {
            builder.append("采购分析,供应商,").append(csv(line.name())).append(',')
                    .append(line.orderAmount()).append(',')
                    .append("订单数 ").append(line.orderCount())
                    .append(" / 已入库金额 ").append(line.receivedAmount()).append('\n');
        }
        // 状态维度
        for (PurchaseAnalysis.StatusLine line : analysis.statuses()) {
            builder.append("采购分析,订单状态,").append(csv(line.status())).append(',')
                    .append(line.orderAmount()).append(',')
                    .append("订单数 ").append(line.orderCount()).append('\n');
        }
        // 月度趋势
        for (PurchaseAnalysis.MonthlyPoint point : analysis.months()) {
            builder.append("采购趋势,月份,").append(point.month()).append(',')
                    .append(point.orderAmount()).append(',')
                    .append("入库金额 ").append(point.receivedAmount()).append('\n');
        }
        return builder.toString();
    }

    /** 新增：CSV 单元格转义（包裹双引号并转义内部引号）。 */
    private String csv(String value) {
        return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }
}
