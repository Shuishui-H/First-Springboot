package com.erp.demo.report;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import com.erp.demo.procurement.PurchaseOrder;
import com.erp.demo.procurement.PurchaseReceipt;
import com.erp.demo.procurement.PurchaseService;
import com.erp.demo.sales.SalesOrder;
import com.erp.demo.sales.SalesOrderItem;
import com.erp.demo.sales.SalesService;
import com.erp.demo.sales.StockOutOrder;
import com.erp.demo.warehouse.StockFlow;
import com.erp.demo.warehouse.WarehouseService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final ProductService productService;
    private final PurchaseService purchaseService;
    private final SalesService salesService;
    private final WarehouseService warehouseService;

    public ReportService(ProductService productService, PurchaseService purchaseService, SalesService salesService,
                         WarehouseService warehouseService) {
        this.productService = productService;
        this.purchaseService = purchaseService;
        this.salesService = salesService;
        this.warehouseService = warehouseService;
    }

    public synchronized DashboardReport dashboard() {
        List<Product> products = productService.findAll();
        List<PurchaseOrder> purchaseOrders = purchaseService.findOrders(null, null);
        List<PurchaseReceipt> receipts = confirmedReceipts();
        List<StockOutOrder> stockOuts = confirmedStockOuts();
        Map<Long, BigDecimal> purchasePrices = purchasePriceMap(purchaseOrders);
        Map<Long, BigDecimal> salesPrices = salesPriceMap();
        int stockIn = receipts.stream().mapToInt(PurchaseReceipt::totalQuantity).sum();
        int saleQuantity = stockOuts.stream().mapToInt(StockOutOrder::totalQuantity).sum();
        BigDecimal purchaseAmount = receipts.stream().map(receipt -> receiptAmount(receipt, purchasePrices)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saleAmount = stockOuts.stream().map(out -> salesAmount(out, salesPrices)).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DashboardReport.DailyPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int dailyIn = receipts.stream().filter(item -> day.equals(item.stockInDate())).mapToInt(PurchaseReceipt::totalQuantity).sum();
            BigDecimal dailyPurchase = receipts.stream().filter(item -> day.equals(item.stockInDate())).map(item -> receiptAmount(item, purchasePrices)).reduce(BigDecimal.ZERO, BigDecimal::add);
            int dailySale = stockOuts.stream().filter(item -> day.equals(item.stockOutDate())).mapToInt(StockOutOrder::totalQuantity).sum();
            BigDecimal dailySales = stockOuts.stream().filter(item -> day.equals(item.stockOutDate())).map(item -> salesAmount(item, salesPrices)).reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(new DashboardReport.DailyPoint(day.toString(), dailyIn, scale2(dailyPurchase), dailySale, scale2(dailySales)));
        }
        BigDecimal inventoryValue = products.stream().map(item -> item.price().multiply(BigDecimal.valueOf(item.stock()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        int lowStock = (int) products.stream().filter(item -> item.stock() < item.safetyStock()).count();
        int pending = (int) purchaseOrders.stream().filter(item -> "待审核".equals(item.status())).count();
        return new DashboardReport(products.size(), products.stream().mapToInt(Product::stock).sum(), scale2(inventoryValue), lowStock,
                purchaseOrders.size(), pending, stockIn, scale2(purchaseAmount), saleQuantity, scale2(saleAmount), trend);
    }

    public synchronized PurchaseAnalysis purchaseAnalysis() {
        List<PurchaseOrder> orders = purchaseService.findOrders(null, null);
        List<PurchaseReceipt> receipts = confirmedReceipts();
        Map<Long, BigDecimal> prices = purchasePriceMap(orders);
        Map<Long, SupplierAggregate> bySupplier = new LinkedHashMap<>();
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        Map<String, BigDecimal> statusAmount = new LinkedHashMap<>();
        for (PurchaseOrder order : orders) {
            SupplierAggregate aggregate = bySupplier.computeIfAbsent(order.supplierId(), id -> new SupplierAggregate(order.supplierName()));
            aggregate.orderCount++;
            aggregate.orderQuantity += order.totalQuantity();
            aggregate.orderAmount = aggregate.orderAmount.add(order.totalAmount());
            statusCount.merge(order.status(), 1, Integer::sum);
            statusAmount.merge(order.status(), order.totalAmount(), BigDecimal::add);
        }
        for (PurchaseReceipt receipt : receipts) {
            PurchaseOrder order = orders.stream().filter(item -> item.id().equals(receipt.purchaseOrderId())).findFirst().orElse(null);
            if (order == null) continue;
            SupplierAggregate aggregate = bySupplier.get(order.supplierId());
            if (aggregate != null) {
                aggregate.receivedQuantity += receipt.totalQuantity();
                aggregate.receivedAmount = aggregate.receivedAmount.add(receiptAmount(receipt, prices));
            }
        }
        List<PurchaseAnalysis.SupplierLine> suppliers = bySupplier.entrySet().stream().map(entry -> {
            SupplierAggregate item = entry.getValue();
            return new PurchaseAnalysis.SupplierLine(entry.getKey(), item.name, item.orderCount, item.orderQuantity, scale2(item.orderAmount), item.receivedQuantity, scale2(item.receivedAmount));
        }).sorted(Comparator.comparing(PurchaseAnalysis.SupplierLine::orderAmount).reversed()).toList();
        List<PurchaseAnalysis.StatusLine> statuses = statusCount.keySet().stream().map(status -> new PurchaseAnalysis.StatusLine(status, statusCount.get(status), scale2(statusAmount.get(status)))).toList();
        Map<YearMonth, BigDecimal> orderByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> receiptByMonth = new HashMap<>();
        orders.forEach(order -> { if (order.orderDate() != null) orderByMonth.merge(YearMonth.from(order.orderDate()), order.totalAmount(), BigDecimal::add); });
        receipts.forEach(receipt -> { if (receipt.stockInDate() != null) receiptByMonth.merge(YearMonth.from(receipt.stockInDate()), receiptAmount(receipt, prices), BigDecimal::add); });
        List<PurchaseAnalysis.MonthlyPoint> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) { YearMonth month = YearMonth.now().minusMonths(i); months.add(new PurchaseAnalysis.MonthlyPoint(month.toString(), scale2(orderByMonth.getOrDefault(month, BigDecimal.ZERO)), scale2(receiptByMonth.getOrDefault(month, BigDecimal.ZERO)))); }
        return new PurchaseAnalysis(suppliers, statuses, months);
    }

    public synchronized List<InventoryBalanceRow> inventoryBalance() {
        List<Product> products = productService.findAll();
        List<StockFlow> flows = warehouseService.findFlows(null, null, null, null);
        Map<Long, Integer> in = new HashMap<>();
        Map<Long, Integer> out = new HashMap<>();
        flows.forEach(flow -> { if (flow.changeQuantity() >= 0) in.merge(flow.productId(), flow.changeQuantity(), Integer::sum); else out.merge(flow.productId(), -flow.changeQuantity(), Integer::sum); });
        return products.stream().map(product -> new InventoryBalanceRow(product.id(), product.sku(), product.name(), product.category(), product.unit(), 0,
                in.getOrDefault(product.id(), 0), out.getOrDefault(product.id(), 0), product.stock(), product.stock(), product.safetyStock(),
                scale2(product.price().multiply(BigDecimal.valueOf(product.stock()))), product.status()))
                .sorted(Comparator.comparing(InventoryBalanceRow::category).thenComparing(InventoryBalanceRow::name)).toList();
    }

    public synchronized List<InventoryWarningRow> lowStock() {
        return productService.findAll().stream().filter(product -> product.stock() <= 0 || product.stock() < product.safetyStock() || "停用".equals(product.status()))
                .map(product -> new InventoryWarningRow(product.id(), product.sku(), product.name(), product.category(), product.unit(), product.stock(), product.safetyStock(),
                        Math.max(0, product.safetyStock() - product.stock()), product.stock() <= 0 ? "缺货" : product.stock() < product.safetyStock() ? "库存偏低" : "停用商品", product.status()))
                .sorted(Comparator.comparing(InventoryWarningRow::warningType).thenComparing(InventoryWarningRow::productId)).toList();
    }

    private List<PurchaseReceipt> confirmedReceipts() { return purchaseService.findReceipts().stream().filter(item -> "已确认".equals(item.status())).toList(); }
    private List<StockOutOrder> confirmedStockOuts() { return salesService.findStockOutOrders(null).stream().filter(item -> "已确认".equals(item.status())).toList(); }
    private Map<Long, BigDecimal> purchasePriceMap(List<PurchaseOrder> orders) { Map<Long, BigDecimal> map = new HashMap<>(); orders.forEach(order -> order.items().forEach(item -> map.put(item.id(), item.unitPrice()))); return map; }
    private Map<Long, BigDecimal> salesPriceMap() { Map<Long, BigDecimal> map = new HashMap<>(); salesService.findOrders(null, null).forEach(order -> order.items().forEach(item -> map.put(item.id(), item.unitPrice()))); return map; }
    private BigDecimal receiptAmount(PurchaseReceipt receipt, Map<Long, BigDecimal> prices) { return scale2(receipt.items().stream().map(item -> prices.getOrDefault(item.purchaseOrderItemId(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(item.receivedQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add)); }
    private BigDecimal salesAmount(StockOutOrder out, Map<Long, BigDecimal> prices) { return scale2(out.items().stream().map(item -> prices.getOrDefault(item.salesOrderItemId(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(item.shippedQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add)); }
    private BigDecimal scale2(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }

    private static final class SupplierAggregate {
        private final String name;
        private int orderCount;
        private int orderQuantity;
        private BigDecimal orderAmount = BigDecimal.ZERO;
        private int receivedQuantity;
        private BigDecimal receivedAmount = BigDecimal.ZERO;
        private SupplierAggregate(String name) { this.name = name; }
    }
}
