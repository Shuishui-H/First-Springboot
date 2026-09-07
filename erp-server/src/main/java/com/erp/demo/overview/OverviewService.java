package com.erp.demo.overview;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import com.erp.demo.procurement.PurchaseOrder;
import com.erp.demo.procurement.PurchaseOrderItem;
import com.erp.demo.procurement.PurchaseReceipt;
import com.erp.demo.procurement.PurchaseService;
import com.erp.demo.sales.SalesOrder;
import com.erp.demo.sales.SalesOrderItem;
import com.erp.demo.sales.SalesService;
import com.erp.demo.sales.StockOutOrder;
import com.erp.demo.warehouse.InventoryBalance;
import com.erp.demo.warehouse.StockFlow;
import com.erp.demo.warehouse.WarehouseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 统一经营概览口径，避免前端自行拼接多个报表接口。 */
@Service
public class OverviewService {
    private static final Set<String> RANGES = Set.of("today", "7d", "month");
    private static final Set<String> ACTIVITY_TYPES = Set.of("采购入库", "销售出库", "销售退货", "手工入库", "手工出库");
    private final ProductService productService;
    private final PurchaseService purchaseService;
    private final SalesService salesService;
    private final WarehouseService warehouseService;

    public OverviewService(ProductService productService, PurchaseService purchaseService, SalesService salesService,
                           WarehouseService warehouseService) {
        this.productService = productService;
        this.purchaseService = purchaseService;
        this.salesService = salesService;
        this.warehouseService = warehouseService;
    }

    public synchronized OverviewResponse overview(String range, Integer riskLimit, Integer activityLimit) {
        String actualRange = range == null || range.isBlank() ? "month" : range;
        if (!RANGES.contains(actualRange)) throw badRequest("range 仅支持 today、7d、month");
        int actualRiskLimit = limit(riskLimit, 5, 20, "riskLimit");
        int actualActivityLimit = limit(activityLimit, 10, 30, "activityLimit");
        LocalDate today = LocalDate.now();
        LocalDate startDate = switch (actualRange) {
            case "today" -> today;
            case "7d" -> today.minusDays(6);
            default -> today.withDayOfMonth(1);
        };

        List<Product> products = productService.findAll();
        List<PurchaseOrder> purchaseOrders = purchaseService.findOrders(null, null);
        List<PurchaseReceipt> receipts = purchaseService.findReceipts().stream().filter(item -> "已确认".equals(item.status())).toList();
        List<SalesOrder> salesOrders = salesService.findOrders(null, null);
        List<StockOutOrder> stockOuts = salesService.findStockOutOrders(null).stream().filter(item -> "已确认".equals(item.status())).toList();
        List<StockFlow> flows = warehouseService.findFlows(null, null, null, null);
        Map<Long, BigDecimal> purchasePrices = purchasePrices(purchaseOrders);
        Map<Long, BigDecimal> salesPrices = salesPrices(salesOrders);

        List<PurchaseReceipt> periodReceipts = receipts.stream().filter(item -> inRange(item.stockInDate(), startDate, today)).toList();
        List<StockOutOrder> periodStockOuts = stockOuts.stream().filter(item -> inRange(item.stockOutDate(), startDate, today)).toList();
        BigDecimal purchaseAmount = scale2(periodReceipts.stream().map(item -> receiptAmount(item, purchasePrices)).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal salesAmount = scale2(periodStockOuts.stream().map(item -> stockOutAmount(item, salesPrices)).reduce(BigDecimal.ZERO, BigDecimal::add));
        int stockOutQuantity = periodStockOuts.stream().mapToInt(StockOutOrder::totalQuantity).sum();
        BigDecimal inventoryValue = scale2(products.stream().map(item -> item.price().multiply(BigDecimal.valueOf(item.stock()))).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<OverviewResponse.Risk> allMainWarehouseRisks = risks(products, products.size());
        int lowStockCount = allMainWarehouseRisks.size();

        return new OverviewResponse(
                new OverviewResponse.Meta(actualRange, startDate, today, LocalDateTime.now()),
                new OverviewResponse.Metrics(salesAmount, purchaseAmount, stockOutQuantity, inventoryValue, lowStockCount),
                todos(purchaseOrders, salesOrders),
                allMainWarehouseRisks.stream().limit(actualRiskLimit).toList(),
                activities(flows, actualActivityLimit),
                trend(startDate, today, receipts, stockOuts, purchasePrices, salesPrices)
        );
    }

    private List<OverviewResponse.Todo> todos(List<PurchaseOrder> purchases, List<SalesOrder> sales) {
        int purchaseApproval = (int) purchases.stream().filter(item -> "待审核".equals(item.status())).count();
        int purchaseReceipt = (int) purchases.stream().filter(item -> "已审核".equals(item.status()) && item.pendingQuantity() > 0).count();
        int salesApproval = (int) sales.stream().filter(item -> "待审核".equals(item.status())).count();
        int salesStockOut = (int) sales.stream().filter(item -> "已审核".equals(item.status()) && item.pendingQuantity() > 0).count();
        return List.of(
                new OverviewResponse.Todo("purchaseApproval", "待采购审核", purchaseApproval, "purchases", "待审核", "review"),
                new OverviewResponse.Todo("purchaseReceipt", "待采购入库", purchaseReceipt, "purchases", "已审核", "receipt"),
                new OverviewResponse.Todo("salesApproval", "待销售审核", salesApproval, "sales", "待审核", "review"),
                new OverviewResponse.Todo("salesStockOut", "待销售出库", salesStockOut, "sales", "已审核", "stockOut")
        );
    }

    private List<OverviewResponse.Risk> risks(List<Product> products, int limit) {
        Map<Long, InventoryBalance> mainBalances = new HashMap<>();
        warehouseService.findBalances(1L, null, null).forEach(item -> mainBalances.put(item.productId(), item));
        return products.stream().map(product -> {
                    InventoryBalance balance = mainBalances.get(product.id());
                    int available = balance == null ? 0 : balance.availableQuantity();
                    int gap = Math.max(product.safetyStock() - available, 0);
                    String type = available <= 0 ? "缺货" : "库存偏低";
                    return new OverviewResponse.Risk(product.id(), product.sku(), product.name(), balance == null ? "主仓" : balance.warehouseName(),
                            product.stock(), available, product.safetyStock(), gap, type);
                }).filter(item -> item.gap() > 0)
                .sorted(Comparator.comparing((OverviewResponse.Risk item) -> !"缺货".equals(item.warningType()))
                        .thenComparing(OverviewResponse.Risk::gap, Comparator.reverseOrder())
                        .thenComparing(OverviewResponse.Risk::sku))
                .limit(limit).toList();
    }

    private List<OverviewResponse.Activity> activities(List<StockFlow> flows, int limit) {
        return flows.stream().filter(item -> ACTIVITY_TYPES.contains(item.businessType()))
                .sorted(Comparator.comparing(StockFlow::time).reversed()).limit(limit)
                .map(item -> new OverviewResponse.Activity(item.flowNo(), item.businessType(), item.productName(), item.warehouseName(),
                        item.changeQuantity(), item.sourceNo(), item.time())).toList();
    }

    private List<OverviewResponse.TrendPoint> trend(LocalDate start, LocalDate end, List<PurchaseReceipt> receipts,
                                                     List<StockOutOrder> stockOuts, Map<Long, BigDecimal> purchasePrices,
                                                     Map<Long, BigDecimal> salesPrices) {
        List<OverviewResponse.TrendPoint> result = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate actualDay = day;
            List<PurchaseReceipt> dailyReceipts = receipts.stream().filter(item -> actualDay.equals(item.stockInDate())).toList();
            List<StockOutOrder> dailyStockOuts = stockOuts.stream().filter(item -> actualDay.equals(item.stockOutDate())).toList();
            result.add(new OverviewResponse.TrendPoint(day,
                    scale2(dailyReceipts.stream().map(item -> receiptAmount(item, purchasePrices)).reduce(BigDecimal.ZERO, BigDecimal::add)),
                    scale2(dailyStockOuts.stream().map(item -> stockOutAmount(item, salesPrices)).reduce(BigDecimal.ZERO, BigDecimal::add)),
                    dailyReceipts.stream().mapToInt(PurchaseReceipt::totalQuantity).sum(),
                    dailyStockOuts.stream().mapToInt(StockOutOrder::totalQuantity).sum()));
        }
        return result;
    }

    private Map<Long, BigDecimal> purchasePrices(List<PurchaseOrder> orders) { Map<Long, BigDecimal> prices = new HashMap<>(); orders.forEach(order -> order.items().forEach(item -> prices.put(item.id(), item.unitPrice()))); return prices; }
    private Map<Long, BigDecimal> salesPrices(List<SalesOrder> orders) { Map<Long, BigDecimal> prices = new HashMap<>(); orders.forEach(order -> order.items().forEach(item -> prices.put(item.id(), item.unitPrice()))); return prices; }
    private BigDecimal receiptAmount(PurchaseReceipt receipt, Map<Long, BigDecimal> prices) { return receipt.items().stream().map(item -> prices.getOrDefault(item.purchaseOrderItemId(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(item.receivedQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal stockOutAmount(StockOutOrder stockOut, Map<Long, BigDecimal> prices) { return stockOut.items().stream().map(item -> prices.getOrDefault(item.salesOrderItemId(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(item.shippedQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private boolean inRange(LocalDate value, LocalDate start, LocalDate end) { return value != null && !value.isBefore(start) && !value.isAfter(end); }
    private int limit(Integer value, int fallback, int max, String field) { int actual = value == null ? fallback : value; if (actual < 1 || actual > max) throw badRequest(field + " 必须在 1 到 " + max + " 之间"); return actual; }
    private BigDecimal scale2(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
