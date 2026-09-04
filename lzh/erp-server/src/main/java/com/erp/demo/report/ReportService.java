package com.erp.demo.report;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import com.erp.demo.procurement.PurchaseOrder;
import com.erp.demo.procurement.PurchaseReceipt;
import com.erp.demo.procurement.PurchaseService;
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

/**
 * 新增：业务报表模块 —— 报表聚合服务。
 * 设计约束（遵循项目既有约定）：
 * 1. 只读聚合既有业务模块的内存数据（商品档案 / 采购订单 / 采购入库单），不新增写库表；
 * 2. 采购金额口径以「已确认」采购入库单为准（receipt.status == "已确认"），
 *    单价取自对应采购订单明细行（PurchaseOrderItem.unitPrice），与开发文档金额精度（2 位小数）一致；
 * 3. 销售相关字段因销售模块尚未实现，统一返回 0，并在代码处以「待销售模块」注释标记整合点。
 */
@Service
public class ReportService {

    private final ProductService productService;
    private final PurchaseService purchaseService;

    public ReportService(ProductService productService, PurchaseService purchaseService) {
        this.productService = productService;
        this.purchaseService = purchaseService;
    }

    // ==================== 经营看板 ====================
    public synchronized DashboardReport dashboard() {
        List<Product> products = productService.findAll();
        List<PurchaseOrder> orders = purchaseService.findOrders(null, null);
        List<PurchaseReceipt> confirmed = confirmedReceipts();
        Map<Long, BigDecimal> linePrice = linePriceMap(orders);

        // 库存口径
        int productCount = products.size();
        int stockTotal = products.stream().mapToInt(Product::stock).sum();
        BigDecimal inventoryValue = products.stream()
                .map(product -> product.price().multiply(BigDecimal.valueOf(product.stock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int lowStockCount = (int) products.stream().filter(product -> product.stock() < product.safetyStock()).count();

        // 采购口径
        long purchasePendingCount = orders.stream().filter(order -> order.status().equals("待审核")).count();
        int stockInQuantity = confirmed.stream().mapToInt(PurchaseReceipt::totalQuantity).sum();
        BigDecimal purchaseAmount = confirmed.stream()
                .map(receipt -> receiptAmount(receipt, linePrice))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 近 7 日趋势
        LocalDate today = LocalDate.now();
        List<DashboardReport.DailyPoint> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int inQuantity = 0;
            BigDecimal inAmount = BigDecimal.ZERO;
            for (PurchaseReceipt receipt : confirmed) {
                if (receipt.stockInDate() != null && receipt.stockInDate().equals(day)) {
                    inQuantity += receipt.totalQuantity();
                    inAmount = inAmount.add(receiptAmount(receipt, linePrice));
                }
            }
            trend.add(new DashboardReport.DailyPoint(day.toString(), inQuantity,
                    inAmount.setScale(2, RoundingMode.HALF_UP), 0, BigDecimal.ZERO));
        }

        return new DashboardReport(productCount, stockTotal, inventoryValue, lowStockCount,
                orders.size(), (int) purchasePendingCount, stockInQuantity, purchaseAmount,
                0, BigDecimal.ZERO, trend);
    }

    // ==================== 采购分析 ====================
    public synchronized PurchaseAnalysis purchaseAnalysis() {
        List<PurchaseOrder> orders = purchaseService.findOrders(null, null);
        List<PurchaseReceipt> confirmed = confirmedReceipts();
        Map<Long, BigDecimal> linePrice = linePriceMap(orders);

        // 1) 按供应商聚合
        Map<Long, SupplierAggregate> bySupplier = new LinkedHashMap<>();
        // 2) 按订单状态聚合
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        Map<String, BigDecimal> statusAmount = new LinkedHashMap<>();

        for (PurchaseOrder order : orders) {
            SupplierAggregate aggregate = bySupplier.computeIfAbsent(order.supplierId(),
                    id -> new SupplierAggregate(order.supplierName()));
            aggregate.orderCount++;
            aggregate.orderQuantity += order.totalQuantity();
            aggregate.orderAmount = aggregate.orderAmount.add(order.totalAmount());
            statusCount.merge(order.status(), 1, Integer::sum);
            statusAmount.merge(order.status(), order.totalAmount(), BigDecimal::add);
        }

        // 已确认入库 → 供应商入库数量与入库金额
        for (PurchaseReceipt receipt : confirmed) {
            PurchaseOrder order = orders.stream()
                    .filter(item -> item.id().equals(receipt.purchaseOrderId())).findFirst().orElse(null);
            if (order == null) {
                continue; // 兼容脏数据：入库单找不到所属订单时跳过
            }
            SupplierAggregate aggregate = bySupplier.get(order.supplierId());
            if (aggregate == null) {
                continue;
            }
            aggregate.receivedQuantity += receipt.totalQuantity();
            aggregate.receivedAmount = aggregate.receivedAmount.add(receiptAmount(receipt, linePrice));
        }

        List<PurchaseAnalysis.SupplierLine> suppliers = bySupplier.entrySet().stream()
                .map(entry -> {
                    SupplierAggregate aggregate = entry.getValue();
                    return new PurchaseAnalysis.SupplierLine(entry.getKey(), aggregate.name,
                            aggregate.orderCount, aggregate.orderQuantity,
                            scale2(aggregate.orderAmount), aggregate.receivedQuantity, scale2(aggregate.receivedAmount));
                })
                .sorted(Comparator.comparing(PurchaseAnalysis.SupplierLine::orderAmount).reversed())
                .toList();

        List<PurchaseAnalysis.StatusLine> statuses = statusCount.keySet().stream()
                .map(status -> new PurchaseAnalysis.StatusLine(status, statusCount.get(status),
                        scale2(statusAmount.getOrDefault(status, BigDecimal.ZERO))))
                .sorted(Comparator.comparing(PurchaseAnalysis.StatusLine::status))
                .toList();

        // 3) 近 12 个月采购趋势（订单按订单日期 / 入库按入库日期）
        Map<YearMonth, BigDecimal> orderByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> receivedByMonth = new HashMap<>();
        for (PurchaseOrder order : orders) {
            if (order.orderDate() != null) {
                orderByMonth.merge(YearMonth.from(order.orderDate()), order.totalAmount(), BigDecimal::add);
            }
        }
        for (PurchaseReceipt receipt : confirmed) {
            if (receipt.stockInDate() != null) {
                receivedByMonth.merge(YearMonth.from(receipt.stockInDate()), receiptAmount(receipt, linePrice), BigDecimal::add);
            }
        }
        List<PurchaseAnalysis.MonthlyPoint> months = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            YearMonth month = now.minusMonths(i);
            months.add(new PurchaseAnalysis.MonthlyPoint(month.toString(),
                    scale2(orderByMonth.getOrDefault(month, BigDecimal.ZERO)),
                    scale2(receivedByMonth.getOrDefault(month, BigDecimal.ZERO))));
        }

        return new PurchaseAnalysis(suppliers, statuses, months);
    }

    // ==================== 库存余额报表 ====================
    public synchronized List<InventoryBalanceRow> inventoryBalance() {
        List<Product> products = productService.findAll();
        List<PurchaseReceipt> confirmed = confirmedReceipts();

        // 累计已确认入库数量（按商品维度）
        Map<Long, Integer> stockInByProduct = new HashMap<>();
        for (PurchaseReceipt receipt : confirmed) {
            for (PurchaseReceipt.Item item : receipt.items()) {
                stockInByProduct.merge(item.productId(), item.receivedQuantity(), Integer::sum);
            }
        }

        return products.stream()
                .map(product -> new InventoryBalanceRow(product.id(), product.sku(), product.name(),
                        product.category(), product.unit(), 0, stockInByProduct.getOrDefault(product.id(), 0), 0,
                        product.stock(), product.stock(), product.safetyStock(),
                        product.price().multiply(BigDecimal.valueOf(product.stock())).setScale(2, RoundingMode.HALF_UP),
                        product.status()))
                .sorted(Comparator.comparing(InventoryBalanceRow::category)
                        .thenComparing(InventoryBalanceRow::name))
                .toList();
    }

    // ==================== 库存预警 ====================
    public synchronized List<InventoryWarningRow> lowStock() {
        return productService.findAll().stream()
                .filter(product -> product.stock() <= 0 || product.stock() < product.safetyStock()
                        || product.status().equals("停用"))
                .map(product -> {
                    String warningType;
                    if (product.stock() <= 0) {
                        warningType = "缺货";
                    } else if (product.stock() < product.safetyStock()) {
                        warningType = "库存偏低";
                    } else {
                        warningType = "停用商品";
                    }
                    return new InventoryWarningRow(product.id(), product.sku(), product.name(), product.category(),
                            product.unit(), product.stock(), product.safetyStock(),
                            Math.max(0, product.safetyStock() - product.stock()), warningType, product.status());
                })
                .sorted(Comparator.comparing(InventoryWarningRow::warningType)
                        .thenComparing(InventoryWarningRow::productId))
                .toList();
    }

    // ==================== 公共只读辅助 ====================
    /** 新增：仅取「已确认」的采购入库单。 */
    private List<PurchaseReceipt> confirmedReceipts() {
        return purchaseService.findReceipts().stream()
                .filter(receipt -> receipt.status().equals("已确认"))
                .toList();
    }

    /** 新增：构建「采购订单明细行 id → 单价」映射，用于按入库数量还原入库金额。 */
    private Map<Long, BigDecimal> linePriceMap(List<PurchaseOrder> orders) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (PurchaseOrder order : orders) {
            order.items().forEach(item -> map.put(item.id(), item.unitPrice()));
        }
        return map;
    }

    /** 新增：计算单张已确认入库单的金额（∑ 入库数量 × 明细行单价），保留 2 位小数。 */
    private BigDecimal receiptAmount(PurchaseReceipt receipt, Map<Long, BigDecimal> linePrice) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReceipt.Item item : receipt.items()) {
            BigDecimal price = linePrice.getOrDefault(item.purchaseOrderItemId(), BigDecimal.ZERO);
            total = total.add(price.multiply(BigDecimal.valueOf(item.receivedQuantity())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** 新增：金额统一保留 2 位小数。 */
    private BigDecimal scale2(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 新增：供应商聚合的临时可变容器（仅 Service 内部使用）。 */
    private static final class SupplierAggregate {
        private final String name;
        private int orderCount;
        private int orderQuantity;
        private BigDecimal orderAmount = BigDecimal.ZERO;
        private int receivedQuantity;
        private BigDecimal receivedAmount = BigDecimal.ZERO;

        private SupplierAggregate(String name) {
            this.name = name;
        }
    }
}
