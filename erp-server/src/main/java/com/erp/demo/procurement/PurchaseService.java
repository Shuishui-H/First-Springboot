package com.erp.demo.procurement;

import com.erp.demo.product.Product;
import com.erp.demo.product.ProductService;
import com.erp.demo.warehouse.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final WarehouseService warehouseService;
    private final JdbcTemplate jdbcTemplate;
    private final List<PurchaseOrder> orders = new ArrayList<>();
    private final List<PurchaseReceipt> receipts = new ArrayList<>();

    public PurchaseService(ProductService productService, SupplierService supplierService, WarehouseService warehouseService) {
        this(productService, supplierService, warehouseService, null);
    }

    @Autowired
    public PurchaseService(ProductService productService, SupplierService supplierService, WarehouseService warehouseService,
                           ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.productService = productService;
        this.supplierService = supplierService;
        this.warehouseService = warehouseService;
        this.jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
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
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("SELECT id FROM pur_purchase_order ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readOrder)
                    .filter(order -> query.isBlank() || order.orderNo().toLowerCase().contains(query) || order.supplierName().toLowerCase().contains(query))
                    .filter(order -> status == null || status.isBlank() || "全部状态".equals(status) || status.equals(order.status())).toList();
        }
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
        if (relationalDataAvailable()) return readOrder(id);
        return orders.stream().filter(order -> order.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("采购订单不存在"));
    }

    @Transactional
    public synchronized PurchaseOrder create(PurchaseOrderRequest request) {
        if (relationalDataAvailable()) return createRelational(request);
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
        if (relationalDataAvailable()) return updateRelational(id, request);
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
        if (relationalDataAvailable()) {
            PurchaseOrder current = findOrder(id); assertStatus(current, "草稿");
            jdbcTemplate.update("DELETE FROM pur_purchase_order WHERE id = ?", id);
            return;
        }
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "草稿");
        orders.remove(current);
    }

    public synchronized PurchaseOrder submit(Long id) {
        if (relationalDataAvailable()) return updateStatusRelational(id, "待审核", null, "草稿", "已驳回");
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "草稿", "已驳回");
        PurchaseOrder submitted = replaceStatus(current, "待审核", null);
        replaceOrder(current, submitted);
        return submitted;
    }

    public synchronized PurchaseOrder approve(Long id, String comment) {
        if (relationalDataAvailable()) return updateStatusRelational(id, "已审核", blankToNull(comment), "待审核");
        PurchaseOrder current = findOrder(id);
        assertStatus(current, "待审核");
        PurchaseOrder approved = replaceStatus(current, "已审核", blankToNull(comment));
        replaceOrder(current, approved);
        return approved;
    }

    public synchronized PurchaseOrder reject(Long id, String comment) {
        if (relationalDataAvailable()) {
            if (comment == null || comment.isBlank()) throw badRequest("驳回时必须填写审核意见");
            return updateStatusRelational(id, "已驳回", comment.trim(), "待审核");
        }
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
        if (relationalDataAvailable()) {
            PurchaseOrder current = findOrder(id);
            if (!Set.of("草稿", "已驳回", "待审核", "已审核").contains(current.status()) || current.receivedQuantity() > 0) throw conflict("当前采购订单已发生入库，不能作废");
            return updateStatusRelational(id, "已作废", blankToNull(comment), "草稿", "已驳回", "待审核", "已审核");
        }
        PurchaseOrder current = findOrder(id);
        if (!Set.of("草稿", "已驳回", "待审核", "已审核").contains(current.status()) || current.receivedQuantity() > 0) {
            throw conflict("当前采购订单已发生入库，不能作废");
        }
        PurchaseOrder voided = replaceStatus(current, "已作废", blankToNull(comment));
        replaceOrder(current, voided);
        return voided;
    }

    public synchronized List<PurchaseOrder> findReceivableOrders() {
        if (relationalDataAvailable()) return findOrders(null, "已审核").stream().filter(order -> order.pendingQuantity() > 0).toList();
        return orders.stream().filter(order -> order.status().equals("已审核") && order.pendingQuantity() > 0).toList();
    }

    public synchronized List<PurchaseReceipt> findReceipts() {
        if (relationalDataAvailable()) return jdbcTemplate.query("SELECT id FROM wh_purchase_receipt ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readReceipt).toList();
        return receipts.stream().sorted((left, right) -> Long.compare(right.id(), left.id())).toList();
    }

    public synchronized PurchaseReceipt findReceipt(Long id) {
        if (relationalDataAvailable()) return readReceipt(id);
        return receipts.stream().filter(receipt -> receipt.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("采购入库单不存在"));
    }

    @Transactional
    public synchronized PurchaseReceipt createReceipt(PurchaseReceiptRequest request) {
        if (relationalDataAvailable()) return createReceiptRelational(request);
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

    @Transactional
    public synchronized PurchaseReceipt confirmReceipt(Long id) {
        if (relationalDataAvailable()) return confirmReceiptRelational(id);
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
            warehouseService.recordBusinessMovement(current.warehouseId(), receiptItem.productId(), receiptItem.receivedQuantity(),
                    true, "采购入库", current.receiptNo(), current.remark());
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

    private PurchaseOrder createRelational(PurchaseOrderRequest request) {
        validateDate(request.orderDate(), request.expectedArrivalDate());
        Supplier supplier = enabledSupplier(request.supplierId());
        List<PurchaseOrderItem> items = buildItems(request.items());
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 3000) + 1 FROM pur_purchase_order", Long.class);
        jdbcTemplate.update("INSERT INTO pur_purchase_order (id, order_no, supplier_id, supplier_name, order_date, expected_arrival_date, status, total_quantity, total_amount, remark, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, '草稿', ?, ?, ?, 0, 0)",
                id, nextOrderNo(id), supplier.id(), supplier.name(), request.orderDate(), request.expectedArrivalDate(), totalQuantity(items), totalAmount(items), request.remark());
        for (PurchaseOrderItem item : items) jdbcTemplate.update("INSERT INTO pur_purchase_order_item (purchase_order_id, product_id, sku, product_name, unit, ordered_quantity, received_quantity, unit_price, line_amount) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                id, item.productId(), item.sku(), item.productName(), item.unit(), item.orderedQuantity(), item.unitPrice(), item.lineAmount());
        return readOrder(id);
    }

    private PurchaseOrder updateRelational(Long id, PurchaseOrderRequest request) {
        PurchaseOrder current = findOrder(id); assertStatus(current, "草稿", "已驳回");
        validateDate(request.orderDate(), request.expectedArrivalDate());
        Supplier supplier = enabledSupplier(request.supplierId());
        List<PurchaseOrderItem> items = buildItems(request.items());
        jdbcTemplate.update("UPDATE pur_purchase_order SET supplier_id = ?, supplier_name = ?, order_date = ?, expected_arrival_date = ?, total_quantity = ?, total_amount = ?, remark = ?, updated_by = 0 WHERE id = ?",
                supplier.id(), supplier.name(), request.orderDate(), request.expectedArrivalDate(), totalQuantity(items), totalAmount(items), request.remark(), id);
        jdbcTemplate.update("DELETE FROM pur_purchase_order_item WHERE purchase_order_id = ?", id);
        for (PurchaseOrderItem item : items) jdbcTemplate.update("INSERT INTO pur_purchase_order_item (purchase_order_id, product_id, sku, product_name, unit, ordered_quantity, received_quantity, unit_price, line_amount) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                id, item.productId(), item.sku(), item.productName(), item.unit(), item.orderedQuantity(), item.unitPrice(), item.lineAmount());
        return readOrder(id);
    }

    private PurchaseOrder updateStatusRelational(Long id, String target, String comment, String... allowed) {
        PurchaseOrder current = findOrder(id); assertStatus(current, allowed);
        int changed = jdbcTemplate.update("UPDATE pur_purchase_order SET status = ?, approval_comment = ?, submitted_at = CASE WHEN ? = '待审核' THEN CURRENT_TIMESTAMP ELSE submitted_at END, approved_at = CASE WHEN ? = '已审核' THEN CURRENT_TIMESTAMP ELSE approved_at END, approved_by = CASE WHEN ? = '已审核' THEN 0 ELSE approved_by END, updated_by = 0 WHERE id = ? AND status = ?",
                target, comment, target, target, target, id, current.status());
        if (changed != 1) throw conflict("采购订单状态已变化，请刷新后重试");
        return readOrder(id);
    }

    private PurchaseReceipt createReceiptRelational(PurchaseReceiptRequest request) {
        PurchaseOrder order = findOrder(request.purchaseOrderId());
        assertStatus(order, "已审核");
        if (request.items().stream().map(PurchaseReceiptRequest.Item::purchaseOrderItemId).distinct().count() != request.items().size()) throw badRequest("入库明细不能重复商品");
        List<PurchaseReceipt.Item> items = request.items().stream().map(item -> {
            PurchaseOrderItem source = order.items().stream().filter(orderItem -> orderItem.id().equals(item.purchaseOrderItemId())).findFirst().orElseThrow(() -> badRequest("入库明细不是当前采购订单的商品"));
            if (item.receivedQuantity() > source.pendingQuantity()) throw conflict("入库数量超过订单未入库数量");
            return new PurchaseReceipt.Item(item.purchaseOrderItemId(), item.purchaseOrderItemId(), source.productId(), source.productName(), item.receivedQuantity());
        }).toList();
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 5000) + 1 FROM wh_purchase_receipt", Long.class);
        String no = "IN" + LocalDate.now().format(ORDER_DATE_FORMAT) + "-" + String.format("%04d", id - 5000);
        jdbcTemplate.update("INSERT INTO wh_purchase_receipt (id, receipt_no, purchase_order_id, purchase_order_no, warehouse_id, warehouse_name, stock_in_date, status, total_quantity, remark, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, '草稿', ?, ?, 0)",
                id, no, order.id(), order.orderNo(), request.warehouseId(), request.warehouseName() == null || request.warehouseName().isBlank() ? warehouseService.findWarehouse(request.warehouseId()).name() : request.warehouseName(), request.stockInDate(), items.stream().mapToInt(PurchaseReceipt.Item::receivedQuantity).sum(), request.remark());
        for (PurchaseReceipt.Item item : items) jdbcTemplate.update("INSERT INTO wh_purchase_receipt_item (purchase_receipt_id, purchase_order_item_id, product_id, product_name, unit, received_quantity) VALUES (?, ?, ?, ?, ?, ?)",
                id, item.purchaseOrderItemId(), item.productId(), item.productName(), order.items().stream().filter(line -> line.id().equals(item.purchaseOrderItemId())).findFirst().orElseThrow().unit(), item.receivedQuantity());
        return readReceipt(id);
    }

    private PurchaseReceipt confirmReceiptRelational(Long id) {
        PurchaseReceipt current = findReceipt(id); assertStatus(current.status(), "草稿");
        PurchaseOrder order = findOrder(current.purchaseOrderId()); assertStatus(order, "已审核");
        for (PurchaseReceipt.Item receiptItem : current.items()) {
            PurchaseOrderItem source = order.items().stream().filter(item -> item.id().equals(receiptItem.purchaseOrderItemId())).findFirst().orElseThrow(() -> badRequest("采购订单明细不存在"));
            if (receiptItem.receivedQuantity() > source.pendingQuantity()) throw conflict("当前订单剩余未入库数量不足，请刷新后重试");
            warehouseService.recordBusinessMovement(current.warehouseId(), receiptItem.productId(), receiptItem.receivedQuantity(), true, "采购入库", current.receiptNo(), current.remark());
            jdbcTemplate.update("UPDATE pur_purchase_order_item SET received_quantity = received_quantity + ? WHERE id = ? AND received_quantity + ? <= ordered_quantity", receiptItem.receivedQuantity(), source.id(), receiptItem.receivedQuantity());
        }
        PurchaseOrder after = readOrder(order.id());
        jdbcTemplate.update("UPDATE pur_purchase_order SET status = ?, updated_by = 0 WHERE id = ?", after.items().stream().allMatch(item -> item.pendingQuantity() == 0) ? "已完成" : "已审核", order.id());
        if (jdbcTemplate.update("UPDATE wh_purchase_receipt SET status = '已确认', confirmed_at = CURRENT_TIMESTAMP, confirmed_by = 0 WHERE id = ? AND status = '草稿'", id) != 1)
            throw conflict("入库单状态已变化，请刷新后重试");
        return readReceipt(id);
    }

    private PurchaseOrder readOrder(Long id) {
        List<PurchaseOrder> rows = jdbcTemplate.query("SELECT id, order_no, supplier_id, supplier_name, order_date, expected_arrival_date, status, total_quantity, total_amount, remark, approval_comment FROM pur_purchase_order WHERE id = ?",
                (rs, row) -> new PurchaseOrder(rs.getLong("id"), rs.getString("order_no"), rs.getLong("supplier_id"), rs.getString("supplier_name"), rs.getDate("order_date").toLocalDate(), rs.getDate("expected_arrival_date") == null ? null : rs.getDate("expected_arrival_date").toLocalDate(), rs.getString("status"), rs.getInt("total_quantity"), rs.getBigDecimal("total_amount"), rs.getString("remark"), rs.getString("approval_comment"), readOrderItems(id)), id);
        if (rows.isEmpty()) throw notFound("采购订单不存在");
        return rows.get(0);
    }

    private List<PurchaseOrderItem> readOrderItems(Long id) {
        return jdbcTemplate.query("SELECT id, product_id, sku, product_name, unit, ordered_quantity, received_quantity, unit_price, line_amount FROM pur_purchase_order_item WHERE purchase_order_id = ? ORDER BY id",
                (rs, row) -> new PurchaseOrderItem(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"), rs.getString("product_name"), rs.getString("unit"), rs.getInt("ordered_quantity"), rs.getInt("received_quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_amount")), id);
    }

    private PurchaseReceipt readReceipt(Long id) {
        List<PurchaseReceipt> rows = jdbcTemplate.query("SELECT id, receipt_no, purchase_order_id, purchase_order_no, warehouse_id, warehouse_name, stock_in_date, status, total_quantity, remark FROM wh_purchase_receipt WHERE id = ?",
                (rs, row) -> new PurchaseReceipt(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("purchase_order_id"), rs.getString("purchase_order_no"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getDate("stock_in_date").toLocalDate(), rs.getString("status"), rs.getInt("total_quantity"), rs.getString("remark"), readReceiptItems(id)), id);
        if (rows.isEmpty()) throw notFound("采购入库单不存在");
        return rows.get(0);
    }

    private List<PurchaseReceipt.Item> readReceiptItems(Long id) {
        return jdbcTemplate.query("SELECT id, purchase_order_item_id, product_id, product_name, received_quantity FROM wh_purchase_receipt_item WHERE purchase_receipt_id = ? ORDER BY id",
                (rs, row) -> new PurchaseReceipt.Item(rs.getLong("id"), rs.getLong("purchase_order_item_id"), rs.getLong("product_id"), rs.getString("product_name"), rs.getInt("received_quantity")), id);
    }

    private boolean relationalDataAvailable() { return jdbcTemplate != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_supplier", Integer.class) > 0; }

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

    public synchronized State exportState() { return new State(List.copyOf(orders), List.copyOf(receipts), nextOrderId.get(), nextItemId.get(), nextReceiptId.get()); }
    public synchronized void restoreState(State state) {
        orders.clear(); orders.addAll(state.orders()); receipts.clear(); receipts.addAll(state.receipts());
        nextOrderId.set(state.nextOrderId()); nextItemId.set(state.nextItemId()); nextReceiptId.set(state.nextReceiptId());
    }
    public record State(List<PurchaseOrder> orders, List<PurchaseReceipt> receipts, long nextOrderId, long nextItemId, long nextReceiptId) {}
}
