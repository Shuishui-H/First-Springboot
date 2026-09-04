package com.erp.demo.procurement;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PurchaseService {

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong nextOrderId = new AtomicLong(3002);
    private final AtomicLong nextItemId = new AtomicLong(3103);
    private final AtomicLong nextReceiptId = new AtomicLong(5001);
    private final ProductService productService;
    private final SupplierService supplierService;
    private final List<PurchaseOrder> orders = new ArrayList<>();
    private final List<PurchaseReceipt> receipts = new ArrayList<>();

    public PurchaseService(ProductService productService, SupplierService supplierService) {
        this.productService = productService;
        this.supplierService = supplierService;
        Product paper = productService.findById(1L);
        Product pen = productService.findById(2L);
        orders.add(new PurchaseOrder(3001L, "PO20260903-0001", 1001L, "上海优采办公用品有限公司",
                LocalDate.now(), LocalDate.now().plusDays(5), "已审核", 120,
                new BigDecimal("860.00"), "安全库存补货示例", null, List.of(
                new PurchaseOrderItem(3101L, paper.id(), paper.sku(), paper.name(), paper.unit(), 20, 0,
                        new BigDecimal("24.00"), new BigDecimal("480.00")),
                new PurchaseOrderItem(3102L, pen.id(), pen.sku(), pen.name(), pen.unit(), 100, 0,
                        new BigDecimal("3.80"), new BigDecimal("380.00")))));
    }

    public synchronized List<PurchaseOrder> findOrders(String keyword, String status) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return orders.stream()
                .filter(order -> query.isBlank() || order.orderNo().toLowerCase().contains(query)
                        || order.supplierName().toLowerCase().contains(query))
                .filter(order -> status == null || status.isBlank() || status.equals("全部状态")
                        || order.status().equals(status))
                .sorted((left, right) -> Long.compare(right.id(), left.id()))
                .toList();
    }

    public synchronized PurchaseOrder findOrder(Long id) {
        return orders.stream().filter(order -> order.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("采购订单不存在"));
    }

    public synchronized PurchaseOrder create(PurchaseOrderRequest request) {
        validateDate(request.orderDate(), request.expectedArrivalDate());
        Supplier supplier = enabledSupplier(request.supplierId());
        List<PurchaseOrderItem> items = buildItems(request.items());
        long id = nextOrderId.getAndIncrement();
        PurchaseOrder order = new PurchaseOrder(id, nextOrderNo(id), supplier.id(), supplier.name(), request.orderDate(),
                request.expectedArrivalDate(), "草稿", totalQuantity(items), totalAmount(items), request.remark(), null, items);
        orders.add(order);
        return order;
    }

    public synchronized PurchaseOrder update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "草稿", "已驳回");
        validateDate(request.orderDate(), request.expectedArrivalDate());
        Supplier supplier = enabledSupplier(request.supplierId());
        List<PurchaseOrderItem> items = buildItems(request.items());
        PurchaseOrder updated = new PurchaseOrder(current.id(), current.orderNo(), supplier.id(), supplier.name(),
                request.orderDate(), request.expectedArrivalDate(), current.status(), totalQuantity(items),
                totalAmount(items), request.remark(), current.approvalComment(), items);
        orders.set(orders.indexOf(current), updated);
        return updated;
    }

    public synchronized void delete(Long id) {
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "草稿");
        orders.remove(current);
    }

    public synchronized PurchaseOrder submit(Long id) {
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "草稿", "已驳回");
        PurchaseOrder submitted = replaceStatus(current, "待审核", null);
        replaceOrder(current, submitted);
        return submitted;
    }

    public synchronized PurchaseOrder approve(Long id, String comment) {
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "待审核");
        PurchaseOrder approved = replaceStatus(current, "已审核", blankToNull(comment));
        replaceOrder(current, approved);
        return approved;
    }

    public synchronized PurchaseOrder reject(Long id, String comment) {
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "待审核");
        if (comment == null || comment.isBlank()) {
            throw badRequest("驳回时必须填写审核意见");
        }
        PurchaseOrder rejected = replaceStatus(current, "已驳回", comment.trim());
        replaceOrder(current, rejected);
        return rejected;
    }

    public synchronized PurchaseOrder voidOrder(Long id, String comment) {
        PurchaseOrder current = findOrder(id);
        if (!Set.of("草稿", "已驳回", "待审核", "已审核").contains(current.status()) || current.receivedQuantity() > 0) {
            throw conflict("当前采购订单已发生入库，不能作废");
        }
        PurchaseOrder voided = replaceStatus(current, "已作废", blankToNull(comment));
        replaceOrder(current, voided);
        return voided;
    }

    public synchronized List<PurchaseOrder> findReceivableOrders() {
        return orders.stream().filter(order -> order.status().equals("已审核") && order.pendingQuantity() > 0).toList();
    }

    public synchronized List<PurchaseReceipt> findReceipts() {
        return receipts.stream().sorted((left, right) -> Long.compare(right.id(), left.id())).toList();
    }

    public synchronized PurchaseReceipt findReceipt(Long id) {
        return receipts.stream().filter(receipt -> receipt.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("采购入库单不存在"));
    }

    public synchronized PurchaseReceipt createReceipt(PurchaseReceiptRequest request) {
        PurchaseOrder order = findOrder(request.purchaseOrderId());
        assertStatus(order, "已审核");
        if (request.items().stream().map(PurchaseReceiptRequest.Item::purchaseOrderItemId).distinct().count()
                != request.items().size()) {
            throw badRequest("入库明细不能重复商品");
        }
        List<PurchaseReceipt.Item> items = request.items().stream().map(item -> {
            PurchaseOrderItem source = order.items().stream()
                    .filter(orderItem -> orderItem.id().equals(item.purchaseOrderItemId())).findFirst()
                    .orElseThrow(() -> badRequest("入库明细不是当前采购订单的商品"));
            if (item.receivedQuantity() > source.pendingQuantity()) {
                throw conflict("入库数量超过订单未入库数量");
            }
            return new PurchaseReceipt.Item(nextItemId.getAndIncrement(), source.id(), source.productId(),
                    source.productName(), item.receivedQuantity());
        }).toList();
        long receiptId = nextReceiptId.getAndIncrement();
        PurchaseReceipt receipt = new PurchaseReceipt(receiptId,
                "IN" + LocalDate.now().format(ORDER_DATE_FORMAT) + "-" + String.format("%04d", receiptId - 5000),
                order.id(), order.orderNo(), request.warehouseId(),
                request.warehouseName() == null || request.warehouseName().isBlank() ? "主仓" : request.warehouseName(),
                request.stockInDate(), "草稿", items.stream().mapToInt(PurchaseReceipt.Item::receivedQuantity).sum(),
                request.remark(), items);
        receipts.add(receipt);
        return receipt;
    }

    public synchronized PurchaseReceipt confirmReceipt(Long id) {
        PurchaseReceipt current = findReceipt(id);
        assertStatus(current.status(), "草稿");
        PurchaseOrder order = findOrder(current.purchaseOrderId());
        assertStatus(order, "已审核");

        List<PurchaseOrderItem> updatedItems = new ArrayList<>(order.items());
        for (PurchaseReceipt.Item receiptItem : current.items()) {
            int index = findItemIndex(updatedItems, receiptItem.purchaseOrderItemId());
            PurchaseOrderItem source = updatedItems.get(index);
            if (receiptItem.receivedQuantity() > source.pendingQuantity()) {
                throw conflict("当前订单剩余未入库数量不足，请刷新后重试");
            }
            productService.increaseStock(receiptItem.productId(), receiptItem.receivedQuantity());
            updatedItems.set(index, new PurchaseOrderItem(source.id(), source.productId(), source.sku(), source.productName(),
                    source.unit(), source.orderedQuantity(), source.receivedQuantity() + receiptItem.receivedQuantity(),
                    source.unitPrice(), source.lineAmount()));
        }

        PurchaseOrder updatedOrder = new PurchaseOrder(order.id(), order.orderNo(), order.supplierId(), order.supplierName(),
                order.orderDate(), order.expectedArrivalDate(), updatedItems.stream().allMatch(item -> item.pendingQuantity() == 0)
                ? "已完成" : "已审核", order.totalQuantity(), order.totalAmount(), order.remark(), order.approvalComment(), updatedItems);
        replaceOrder(order, updatedOrder);
        PurchaseReceipt confirmed = new PurchaseReceipt(current.id(), current.receiptNo(), current.purchaseOrderId(),
                current.purchaseOrderNo(), current.warehouseId(), current.warehouseName(), current.stockInDate(),
                "已确认", current.totalQuantity(), current.remark(), current.items());
        receipts.set(receipts.indexOf(current), confirmed);
        return confirmed;
    }

    private List<PurchaseOrderItem> buildItems(List<PurchaseOrderRequest.Item> requests) {
        Set<Long> productIds = new HashSet<>();
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (PurchaseOrderRequest.Item request : requests) {
            if (!productIds.add(request.productId())) {
                throw badRequest("同一采购订单不能重复添加商品");
            }
            Product product = productService.findById(request.productId());
            if (!"启用".equals(product.status())) {
                throw badRequest("商品已停用，不能采购");
            }
            BigDecimal price = request.unitPrice().setScale(2, RoundingMode.HALF_UP);
            items.add(new PurchaseOrderItem(nextItemId.getAndIncrement(), product.id(), product.sku(), product.name(),
                    product.unit(), request.orderedQuantity(), 0, price,
                    price.multiply(BigDecimal.valueOf(request.orderedQuantity())).setScale(2, RoundingMode.HALF_UP)));
        }
        return items;
    }

    private Supplier enabledSupplier(Long id) {
        Supplier supplier = supplierService.findById(id);
        if (!"启用".equals(supplier.status())) {
            throw badRequest("供应商已停用，不能采购");
        }
        return supplier;
    }

    private void validateDate(LocalDate orderDate, LocalDate arrivalDate) {
        if (arrivalDate != null && arrivalDate.isBefore(orderDate)) {
            throw badRequest("预计到货日期不能早于订单日期");
        }
    }

    private String nextOrderNo(long id) {
        return "PO" + LocalDate.now().format(ORDER_DATE_FORMAT) + "-" + String.format("%04d", id - 3000);
    }

    private int totalQuantity(List<PurchaseOrderItem> items) {
        return items.stream().mapToInt(PurchaseOrderItem::orderedQuantity).sum();
    }

    private BigDecimal totalAmount(List<PurchaseOrderItem> items) {
        return items.stream().map(PurchaseOrderItem::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private PurchaseOrder replaceStatus(PurchaseOrder order, String status, String comment) {
        return new PurchaseOrder(order.id(), order.orderNo(), order.supplierId(), order.supplierName(), order.orderDate(),
                order.expectedArrivalDate(), status, order.totalQuantity(), order.totalAmount(), order.remark(), comment, order.items());
    }

    private void replaceOrder(PurchaseOrder oldOrder, PurchaseOrder newOrder) {
        orders.set(orders.indexOf(oldOrder), newOrder);
    }

    private int findItemIndex(List<PurchaseOrderItem> items, Long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) return i;
        }
        throw badRequest("采购订单明细不存在");
    }

    private void assertStatus(PurchaseOrder order, String... allowed) {
        assertStatus(order.status(), allowed);
    }

    private void assertStatus(String current, String... allowed) {
        for (String status : allowed) if (status.equals(current)) return;
        throw conflict("当前状态不允许执行此操作");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
