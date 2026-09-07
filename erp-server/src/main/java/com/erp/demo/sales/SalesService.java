package com.erp.demo.sales;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SalesService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong nextOrderId = new AtomicLong(4002);
    private final AtomicLong nextOrderItemId = new AtomicLong(4103);
    private final AtomicLong nextStockOutId = new AtomicLong(7001);
    private final AtomicLong nextStockOutItemId = new AtomicLong(7103);
    private final AtomicLong nextReturnId = new AtomicLong(8001);
    private final AtomicLong nextReturnItemId = new AtomicLong(8103);
    private final ProductService productService;
    private final CustomerService customerService;
    private final WarehouseService warehouseService;
    private final JdbcTemplate jdbcTemplate;
    private final List<SalesOrder> orders = new ArrayList<>();
    private final List<StockOutOrder> stockOutOrders = new ArrayList<>();
    private final List<SalesReturnOrder> returnOrders = new ArrayList<>();

    public SalesService(ProductService productService, CustomerService customerService, WarehouseService warehouseService) {
        this(productService, customerService, warehouseService, null);
    }

    @Autowired
    public SalesService(ProductService productService, CustomerService customerService, WarehouseService warehouseService,
                        ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.productService = productService;
        this.customerService = customerService;
        this.warehouseService = warehouseService;
        this.jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
        Product paper = productService.findById(1L);
        Product mouse = productService.findById(3L);
        List<SalesOrderItem> items = List.of(
                new SalesOrderItem(4101L, paper.id(), paper.sku(), paper.name(), paper.unit(), 5, 0, 0,
                        new BigDecimal("29.90"), new BigDecimal("149.50")),
                new SalesOrderItem(4102L, mouse.id(), mouse.sku(), mouse.name(), mouse.unit(), 2, 0, 0,
                        new BigDecimal("89.00"), new BigDecimal("178.00")));
        orders.add(new SalesOrder(4001L, "SO" + LocalDate.now().format(DATE_FORMAT) + "-0001", 6001L,
                "上海星河办公有限公司", 1L, "主仓", LocalDate.now(), LocalDate.now().plusDays(2), "已审核",
                7, 0, 0, new BigDecimal("327.50"), "演示销售订单，可直接发起出库", null, items));
    }

    public synchronized List<SalesOrder> findOrders(String keyword, String status) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("SELECT id FROM sal_sales_order ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readOrder)
                    .filter(order -> query.isBlank() || order.orderNo().toLowerCase().contains(query) || order.customerName().toLowerCase().contains(query))
                    .filter(order -> status == null || status.isBlank() || "全部状态".equals(status) || status.equals(order.status())).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return orders.stream()
                .filter(order -> query.isBlank() || order.orderNo().toLowerCase().contains(query)
                        || order.customerName().toLowerCase().contains(query))
                .filter(order -> status == null || status.isBlank() || "全部状态".equals(status)
                        || order.status().equals(status))
                .sorted((left, right) -> Long.compare(right.id(), left.id()))
                .toList();
    }

    public synchronized SalesOrder findOrder(Long id) {
        if (relationalDataAvailable()) return readOrder(id);
        return orders.stream().filter(order -> order.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("销售订单不存在"));
    }

    @Transactional
    public synchronized SalesOrder create(SalesOrderRequest request) {
        if (relationalDataAvailable()) return createRelational(request);
        validateDates(request.orderDate(), request.requiredShipDate());
        Customer customer = enabledCustomer(request.customerId());
        List<SalesOrderItem> items = buildOrderItems(request.items());
        long id = nextOrderId.getAndIncrement();
        SalesOrder order = new SalesOrder(id, nextOrderNo("SO", id, 4000), customer.id(), customer.name(),
                request.warehouseId(), "主仓", request.orderDate(), request.requiredShipDate(), "草稿",
                totalQuantity(items), 0, 0, totalAmount(items), request.remark(), null, items);
        orders.add(order);
        return order;
    }

    public synchronized SalesOrder update(Long id, SalesOrderRequest request) {
        if (relationalDataAvailable()) return updateRelational(id, request);
        SalesOrder current = findOrder(id);
        assertStatus(current.status(), "草稿", "已驳回");
        validateDates(request.orderDate(), request.requiredShipDate());
        Customer customer = enabledCustomer(request.customerId());
        List<SalesOrderItem> items = buildOrderItems(request.items());
        SalesOrder updated = new SalesOrder(current.id(), current.orderNo(), customer.id(), customer.name(),
                request.warehouseId(), "主仓", request.orderDate(), request.requiredShipDate(), current.status(),
                totalQuantity(items), 0, 0, totalAmount(items), request.remark(), current.approvalComment(), items);
        replaceOrder(current, updated);
        return updated;
    }

    public synchronized void delete(Long id) {
        if (relationalDataAvailable()) {
            SalesOrder current = findOrder(id); assertStatus(current.status(), "草稿"); jdbcTemplate.update("DELETE FROM sal_sales_order WHERE id = ?", id); return;
        }
        SalesOrder current = findOrder(id);
        assertStatus(current.status(), "草稿");
        orders.remove(current);
    }

    public synchronized SalesOrder submit(Long id) {
        if (relationalDataAvailable()) return updateStatusRelational(id, "待审核", null, "草稿", "已驳回");
        SalesOrder current = findOrder(id);
        assertStatus(current.status(), "草稿", "已驳回");
        SalesOrder updated = replaceStatus(current, "待审核", null);
        replaceOrder(current, updated);
        return updated;
    }

    public synchronized SalesOrder approve(Long id, String comment) {
        if (relationalDataAvailable()) return updateStatusRelational(id, "已审核", blankToNull(comment), "待审核");
        SalesOrder current = findOrder(id);
        assertStatus(current.status(), "待审核");
        SalesOrder updated = replaceStatus(current, "已审核", blankToNull(comment));
        replaceOrder(current, updated);
        return updated;
    }

    public synchronized SalesOrder reject(Long id, String comment) {
        if (relationalDataAvailable()) { if (comment == null || comment.isBlank()) throw badRequest("驳回时必须填写审核意见"); return updateStatusRelational(id, "已驳回", comment.trim(), "待审核"); }
        SalesOrder current = findOrder(id);
        assertStatus(current.status(), "待审核");
        if (comment == null || comment.isBlank()) throw badRequest("驳回时必须填写审核意见");
        SalesOrder updated = replaceStatus(current, "已驳回", comment.trim());
        replaceOrder(current, updated);
        return updated;
    }

    public synchronized SalesOrder voidOrder(Long id, String comment) {
        if (relationalDataAvailable()) {
            SalesOrder current = findOrder(id); if (!Set.of("草稿", "已驳回", "待审核", "已审核").contains(current.status()) || current.shippedQuantity() > 0) throw conflict("当前销售订单已发生出库，不能作废");
            return updateStatusRelational(id, "已作废", blankToNull(comment), "草稿", "已驳回", "待审核", "已审核");
        }
        SalesOrder current = findOrder(id);
        if (!Set.of("草稿", "已驳回", "待审核", "已审核").contains(current.status()) || current.shippedQuantity() > 0) {
            throw conflict("当前销售订单已发生出库，不能作废");
        }
        SalesOrder updated = replaceStatus(current, "已作废", blankToNull(comment));
        replaceOrder(current, updated);
        return updated;
    }

    public synchronized List<SalesOrder> findShippableOrders() {
        if (relationalDataAvailable()) return findOrders(null, "已审核").stream().filter(order -> order.pendingQuantity() > 0).toList();
        return orders.stream().filter(order -> "已审核".equals(order.status()) && order.pendingQuantity() > 0).toList();
    }

    public synchronized List<StockOutOrder> findStockOutOrders(String keyword) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("SELECT id FROM wh_stock_out_order ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readStockOut)
                    .filter(item -> query.isBlank() || item.stockOutNo().toLowerCase().contains(query) || item.salesOrderNo().toLowerCase().contains(query) || item.customerName().toLowerCase().contains(query)).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return stockOutOrders.stream()
                .filter(item -> query.isBlank() || item.stockOutNo().toLowerCase().contains(query)
                        || item.salesOrderNo().toLowerCase().contains(query) || item.customerName().toLowerCase().contains(query))
                .sorted((left, right) -> Long.compare(right.id(), left.id())).toList();
    }

    public synchronized StockOutOrder findStockOut(Long id) {
        if (relationalDataAvailable()) return readStockOut(id);
        return stockOutOrders.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("销售出库单不存在"));
    }

    @Transactional
    public synchronized StockOutOrder createStockOut(StockOutRequest request) {
        if (relationalDataAvailable()) return createStockOutRelational(request);
        SalesOrder order = findOrder(request.salesOrderId());
        assertStatus(order.status(), "已审核");
        List<StockOutOrder.Item> items = new ArrayList<>();
        Set<Long> itemIds = new HashSet<>();
        for (StockOutRequest.Item requestItem : request.items()) {
            if (!itemIds.add(requestItem.salesOrderItemId())) throw badRequest("出库明细不能重复商品");
            SalesOrderItem source = findOrderItem(order, requestItem.salesOrderItemId());
            if (requestItem.shippedQuantity() > source.pendingQuantity()) throw conflict("出库数量超过订单待出库数量");
            items.add(new StockOutOrder.Item(nextStockOutItemId.getAndIncrement(), source.id(), source.productId(),
                    source.productName(), requestItem.shippedQuantity(), 0));
        }
        if (items.isEmpty()) throw badRequest("出库明细不能为空");
        long id = nextStockOutId.getAndIncrement();
        StockOutOrder stockOut = new StockOutOrder(id, nextOrderNo("OUT", id, 7000), order.id(), order.orderNo(),
                order.customerName(), order.warehouseId(), order.warehouseName(), request.stockOutDate(), "草稿",
                items.stream().mapToInt(StockOutOrder.Item::shippedQuantity).sum(), request.remark(), items);
        stockOutOrders.add(stockOut);
        return stockOut;
    }

    @Transactional
    public synchronized StockOutOrder confirmStockOut(Long id) {
        if (relationalDataAvailable()) return confirmStockOutRelational(id);
        StockOutOrder current = findStockOut(id);
        assertStatus(current.status(), "草稿");
        SalesOrder order = findOrder(current.salesOrderId());
        assertStatus(order.status(), "已审核");
        for (StockOutOrder.Item item : current.items()) {
            SalesOrderItem source = findOrderItem(order, item.salesOrderItemId());
            if (item.shippedQuantity() > source.pendingQuantity()) throw conflict("订单剩余待出库数量不足，请刷新后重试");
            Product product = productService.findById(item.productId());
            if (product.stock() < item.shippedQuantity()) throw conflict("商品库存不足：" + product.name());
            warehouseService.assertAvailableStock(current.warehouseId(), item.productId(), item.shippedQuantity());
        }
        for (StockOutOrder.Item item : current.items()) {
            productService.decreaseStock(item.productId(), item.shippedQuantity());
            warehouseService.recordBusinessMovement(current.warehouseId(), item.productId(), item.shippedQuantity(),
                    false, "销售出库", current.stockOutNo(), current.remark());
        }
        List<SalesOrderItem> updatedItems = new ArrayList<>(order.items());
        for (StockOutOrder.Item item : current.items()) {
            int index = findItemIndex(updatedItems, item.salesOrderItemId());
            SalesOrderItem source = updatedItems.get(index);
            updatedItems.set(index, new SalesOrderItem(source.id(), source.productId(), source.sku(), source.productName(), source.unit(),
                    source.orderedQuantity(), source.shippedQuantity() + item.shippedQuantity(), source.returnedQuantity(), source.unitPrice(), source.lineAmount()));
        }
        int shipped = updatedItems.stream().mapToInt(SalesOrderItem::shippedQuantity).sum();
        SalesOrder updatedOrder = new SalesOrder(order.id(), order.orderNo(), order.customerId(), order.customerName(), order.warehouseId(),
                order.warehouseName(), order.orderDate(), order.requiredShipDate(), updatedItems.stream().allMatch(item -> item.pendingQuantity() == 0) ? "已完成" : "已审核",
                order.totalQuantity(), shipped, order.returnedQuantity(), order.totalAmount(), order.remark(), order.approvalComment(), updatedItems);
        replaceOrder(order, updatedOrder);
        StockOutOrder confirmed = new StockOutOrder(current.id(), current.stockOutNo(), current.salesOrderId(), current.salesOrderNo(),
                current.customerName(), current.warehouseId(), current.warehouseName(), current.stockOutDate(), "已确认", current.totalQuantity(), current.remark(), current.items());
        replaceStockOut(current, confirmed);
        return confirmed;
    }

    public synchronized List<StockOutOrder> findReturnableStockOuts() {
        if (relationalDataAvailable()) return findStockOutOrders(null).stream().filter(item -> "已确认".equals(item.status()) && item.items().stream().anyMatch(detail -> detail.returnableQuantity() > 0)).toList();
        return stockOutOrders.stream().filter(item -> "已确认".equals(item.status())
                && item.items().stream().anyMatch(detail -> detail.returnableQuantity() > 0)).toList();
    }

    public synchronized List<SalesReturnOrder> findReturnOrders(String keyword) {
        if (relationalDataAvailable()) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase();
            return jdbcTemplate.query("SELECT id FROM sal_sales_return ORDER BY id DESC", (rs, row) -> rs.getLong(1)).stream().map(this::readReturn)
                    .filter(item -> query.isBlank() || item.returnNo().toLowerCase().contains(query) || item.salesOrderNo().toLowerCase().contains(query) || item.customerName().toLowerCase().contains(query)).toList();
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return returnOrders.stream().filter(item -> query.isBlank() || item.returnNo().toLowerCase().contains(query)
                        || item.salesOrderNo().toLowerCase().contains(query) || item.customerName().toLowerCase().contains(query))
                .sorted((left, right) -> Long.compare(right.id(), left.id())).toList();
    }

    public synchronized SalesReturnOrder findReturn(Long id) {
        if (relationalDataAvailable()) return readReturn(id);
        return returnOrders.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("销售退货单不存在"));
    }

    @Transactional
    public synchronized SalesReturnOrder createReturn(SalesReturnRequest request) {
        if (relationalDataAvailable()) return createReturnRelational(request);
        StockOutOrder stockOut = findStockOut(request.sourceStockOutId());
        assertStatus(stockOut.status(), "已确认");
        SalesOrder order = findOrder(stockOut.salesOrderId());
        List<SalesReturnOrder.Item> items = new ArrayList<>();
        Set<Long> itemIds = new HashSet<>();
        for (SalesReturnRequest.Item requestItem : request.items()) {
            if (!itemIds.add(requestItem.sourceStockOutItemId())) throw badRequest("退货明细不能重复商品");
            StockOutOrder.Item source = stockOut.items().stream().filter(item -> item.id().equals(requestItem.sourceStockOutItemId())).findFirst()
                    .orElseThrow(() -> badRequest("退货明细不是当前出库单的商品"));
            if (requestItem.returnedQuantity() > source.returnableQuantity()) throw conflict("退货数量超过可退数量");
            items.add(new SalesReturnOrder.Item(nextReturnItemId.getAndIncrement(), source.id(), source.salesOrderItemId(),
                    source.productId(), source.productName(), requestItem.returnedQuantity()));
        }
        long id = nextReturnId.getAndIncrement();
        SalesReturnOrder result = new SalesReturnOrder(id, nextOrderNo("RT", id, 8000), stockOut.id(), stockOut.stockOutNo(),
                order.id(), order.orderNo(), order.customerName(), stockOut.warehouseId(), stockOut.warehouseName(), request.returnDate(),
                "草稿", items.stream().mapToInt(SalesReturnOrder.Item::returnedQuantity).sum(), request.reason().trim(), request.remark(), items);
        returnOrders.add(result);
        return result;
    }

    public synchronized SalesReturnOrder submitReturn(Long id) { if (relationalDataAvailable()) return changeReturnStatusRelational(id, "草稿", "待审核", null); return changeReturnStatus(id, "草稿", "待审核", null); }
    public synchronized SalesReturnOrder approveReturn(Long id, String comment) { if (relationalDataAvailable()) return changeReturnStatusRelational(id, "待审核", "已审核", comment); return changeReturnStatus(id, "待审核", "已审核", comment); }
    public synchronized SalesReturnOrder rejectReturn(Long id, String comment) {
        if (comment == null || comment.isBlank()) throw badRequest("驳回时必须填写审核意见");
        if (relationalDataAvailable()) return changeReturnStatusRelational(id, "待审核", "已驳回", comment.trim());
        return changeReturnStatus(id, "待审核", "已驳回", comment.trim());
    }

    @Transactional
    public synchronized SalesReturnOrder confirmReturn(Long id) {
        if (relationalDataAvailable()) return confirmReturnRelational(id);
        SalesReturnOrder current = findReturn(id);
        assertStatus(current.status(), "已审核");
        StockOutOrder stockOut = findStockOut(current.stockOutId());
        SalesOrder order = findOrder(current.salesOrderId());
        for (SalesReturnOrder.Item item : current.items()) {
            StockOutOrder.Item source = findStockOutItem(stockOut, item.stockOutItemId());
            if (item.returnedQuantity() > source.returnableQuantity()) throw conflict("当前出库单可退数量不足，请刷新后重试");
        }
        for (SalesReturnOrder.Item item : current.items()) {
            productService.increaseStock(item.productId(), item.returnedQuantity());
            warehouseService.recordBusinessMovement(current.warehouseId(), item.productId(), item.returnedQuantity(),
                    true, "销售退货", current.returnNo(), current.remark());
        }
        List<StockOutOrder.Item> updatedStockItems = new ArrayList<>(stockOut.items());
        List<SalesOrderItem> updatedOrderItems = new ArrayList<>(order.items());
        for (SalesReturnOrder.Item item : current.items()) {
            StockOutOrder.Item stockItem = findStockOutItem(stockOut, item.stockOutItemId());
            int stockIndex = findStockOutItemIndex(updatedStockItems, stockItem.id());
            updatedStockItems.set(stockIndex, new StockOutOrder.Item(stockItem.id(), stockItem.salesOrderItemId(), stockItem.productId(), stockItem.productName(),
                    stockItem.shippedQuantity(), stockItem.returnedQuantity() + item.returnedQuantity()));
            int orderIndex = findItemIndex(updatedOrderItems, item.salesOrderItemId());
            SalesOrderItem orderItem = updatedOrderItems.get(orderIndex);
            updatedOrderItems.set(orderIndex, new SalesOrderItem(orderItem.id(), orderItem.productId(), orderItem.sku(), orderItem.productName(), orderItem.unit(),
                    orderItem.orderedQuantity(), orderItem.shippedQuantity(), orderItem.returnedQuantity() + item.returnedQuantity(), orderItem.unitPrice(), orderItem.lineAmount()));
        }
        StockOutOrder updatedStockOut = new StockOutOrder(stockOut.id(), stockOut.stockOutNo(), stockOut.salesOrderId(), stockOut.salesOrderNo(), stockOut.customerName(),
                stockOut.warehouseId(), stockOut.warehouseName(), stockOut.stockOutDate(), stockOut.status(), stockOut.totalQuantity(), stockOut.remark(), updatedStockItems);
        replaceStockOut(stockOut, updatedStockOut);
        int returned = updatedOrderItems.stream().mapToInt(SalesOrderItem::returnedQuantity).sum();
        SalesOrder updatedOrder = new SalesOrder(order.id(), order.orderNo(), order.customerId(), order.customerName(), order.warehouseId(), order.warehouseName(),
                order.orderDate(), order.requiredShipDate(), order.status(), order.totalQuantity(), order.shippedQuantity(), returned, order.totalAmount(), order.remark(), order.approvalComment(), updatedOrderItems);
        replaceOrder(order, updatedOrder);
        SalesReturnOrder confirmed = replaceReturnStatus(current, "已确认", null);
        replaceReturn(current, confirmed);
        return confirmed;
    }

    public synchronized SalesReturnOrder voidReturn(Long id, String comment) {
        if (relationalDataAvailable()) return changeReturnStatusRelational(id, "草稿", "已作废", blankToNull(comment));
        SalesReturnOrder current = findReturn(id);
        assertStatus(current.status(), "草稿", "已驳回");
        SalesReturnOrder updated = replaceReturnStatus(current, "已作废", blankToNull(comment));
        replaceReturn(current, updated);
        return updated;
    }

    private List<SalesOrderItem> buildOrderItems(List<SalesOrderRequest.Item> requests) {
        Set<Long> productIds = new HashSet<>();
        List<SalesOrderItem> items = new ArrayList<>();
        for (SalesOrderRequest.Item request : requests) {
            if (!productIds.add(request.productId())) throw badRequest("同一销售订单不能重复添加商品");
            Product product = productService.findById(request.productId());
            if (!"启用".equals(product.status())) throw badRequest("商品已停用，不能销售");
            BigDecimal price = request.unitPrice().setScale(2, RoundingMode.HALF_UP);
            items.add(new SalesOrderItem(nextOrderItemId.getAndIncrement(), product.id(), product.sku(), product.name(), product.unit(),
                    request.orderedQuantity(), 0, 0, price, price.multiply(BigDecimal.valueOf(request.orderedQuantity())).setScale(2, RoundingMode.HALF_UP)));
        }
        return items;
    }

    private SalesOrder createRelational(SalesOrderRequest request) {
        validateDates(request.orderDate(), request.requiredShipDate());
        Customer customer = enabledCustomer(request.customerId());
        var warehouse = warehouseService.findWarehouse(request.warehouseId());
        if (!"启用".equals(warehouse.status())) throw badRequest("仓库已停用，不能销售");
        List<SalesOrderItem> items = buildOrderItems(request.items());
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 4000) + 1 FROM sal_sales_order", Long.class);
        String no = nextOrderNo("SO", id, 4000);
        jdbcTemplate.update("INSERT INTO sal_sales_order (id, order_no, customer_id, customer_name, warehouse_id, warehouse_name, order_date, required_ship_date, status, total_quantity, shipped_quantity, returned_quantity, total_amount, remark, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '草稿', ?, 0, 0, ?, ?, 0, 0)",
                id, no, customer.id(), customer.name(), warehouse.id(), warehouse.name(), request.orderDate(), request.requiredShipDate(), totalQuantity(items), totalAmount(items), request.remark());
        for (SalesOrderItem item : items) jdbcTemplate.update("INSERT INTO sal_sales_order_item (sales_order_id, product_id, sku, product_name, unit, ordered_quantity, shipped_quantity, returned_quantity, unit_price, line_amount) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)",
                id, item.productId(), item.sku(), item.productName(), item.unit(), item.orderedQuantity(), item.unitPrice(), item.lineAmount());
        return readOrder(id);
    }

    private SalesOrder updateRelational(Long id, SalesOrderRequest request) {
        SalesOrder current = findOrder(id); assertStatus(current.status(), "草稿", "已驳回");
        validateDates(request.orderDate(), request.requiredShipDate());
        Customer customer = enabledCustomer(request.customerId()); var warehouse = warehouseService.findWarehouse(request.warehouseId());
        List<SalesOrderItem> items = buildOrderItems(request.items());
        jdbcTemplate.update("UPDATE sal_sales_order SET customer_id = ?, customer_name = ?, warehouse_id = ?, warehouse_name = ?, order_date = ?, required_ship_date = ?, total_quantity = ?, total_amount = ?, remark = ?, updated_by = 0 WHERE id = ?",
                customer.id(), customer.name(), warehouse.id(), warehouse.name(), request.orderDate(), request.requiredShipDate(), totalQuantity(items), totalAmount(items), request.remark(), id);
        jdbcTemplate.update("DELETE FROM sal_sales_order_item WHERE sales_order_id = ?", id);
        for (SalesOrderItem item : items) jdbcTemplate.update("INSERT INTO sal_sales_order_item (sales_order_id, product_id, sku, product_name, unit, ordered_quantity, shipped_quantity, returned_quantity, unit_price, line_amount) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)",
                id, item.productId(), item.sku(), item.productName(), item.unit(), item.orderedQuantity(), item.unitPrice(), item.lineAmount());
        return readOrder(id);
    }

    private SalesOrder updateStatusRelational(Long id, String target, String comment, String... allowed) {
        SalesOrder current = findOrder(id); assertStatus(current.status(), allowed);
        if (jdbcTemplate.update("UPDATE sal_sales_order SET status = ?, approval_comment = ?, submitted_at = CASE WHEN ? = '待审核' THEN CURRENT_TIMESTAMP ELSE submitted_at END, approved_at = CASE WHEN ? = '已审核' THEN CURRENT_TIMESTAMP ELSE approved_at END, approved_by = CASE WHEN ? = '已审核' THEN 0 ELSE approved_by END, updated_by = 0 WHERE id = ? AND status = ?", target, comment, target, target, target, id, current.status()) != 1)
            throw conflict("销售订单状态已变化，请刷新后重试");
        return readOrder(id);
    }

    private StockOutOrder createStockOutRelational(StockOutRequest request) {
        SalesOrder order = findOrder(request.salesOrderId()); assertStatus(order.status(), "已审核");
        List<StockOutOrder.Item> items = new ArrayList<>(); Set<Long> itemIds = new HashSet<>();
        for (StockOutRequest.Item requestItem : request.items()) {
            if (!itemIds.add(requestItem.salesOrderItemId())) throw badRequest("出库明细不能重复商品");
            SalesOrderItem source = findOrderItem(order, requestItem.salesOrderItemId());
            if (requestItem.shippedQuantity() > source.pendingQuantity()) throw conflict("出库数量超过订单待出库数量");
            items.add(new StockOutOrder.Item(requestItem.salesOrderItemId(), source.id(), source.productId(), source.productName(), requestItem.shippedQuantity(), 0));
        }
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 7000) + 1 FROM wh_stock_out_order", Long.class);
        String no = nextOrderNo("OUT", id, 7000);
        jdbcTemplate.update("INSERT INTO wh_stock_out_order (id, stock_out_no, sales_order_id, sales_order_no, customer_id, customer_name, warehouse_id, warehouse_name, stock_out_date, status, total_quantity, remark, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '草稿', ?, ?, 0)",
                id, no, order.id(), order.orderNo(), order.customerId(), order.customerName(), order.warehouseId(), order.warehouseName(), request.stockOutDate(), items.stream().mapToInt(StockOutOrder.Item::shippedQuantity).sum(), request.remark());
        for (StockOutOrder.Item item : items) jdbcTemplate.update("INSERT INTO wh_stock_out_order_item (stock_out_order_id, sales_order_item_id, product_id, product_name, unit, shipped_quantity, returned_quantity) VALUES (?, ?, ?, ?, ?, ?, 0)",
                id, item.salesOrderItemId(), item.productId(), item.productName(), order.items().stream().filter(line -> line.id().equals(item.salesOrderItemId())).findFirst().orElseThrow().unit(), item.shippedQuantity());
        return readStockOut(id);
    }

    private StockOutOrder confirmStockOutRelational(Long id) {
        StockOutOrder current = findStockOut(id); assertStatus(current.status(), "草稿");
        SalesOrder order = findOrder(current.salesOrderId()); assertStatus(order.status(), "已审核");
        for (StockOutOrder.Item item : current.items()) {
            SalesOrderItem source = findOrderItem(order, item.salesOrderItemId());
            if (item.shippedQuantity() > source.pendingQuantity()) throw conflict("订单剩余待出库数量不足，请刷新后重试");
            warehouseService.assertAvailableStock(current.warehouseId(), item.productId(), item.shippedQuantity());
        }
        for (StockOutOrder.Item item : current.items()) {
            warehouseService.recordBusinessMovement(current.warehouseId(), item.productId(), item.shippedQuantity(), false, "销售出库", current.stockOutNo(), current.remark());
            jdbcTemplate.update("UPDATE sal_sales_order_item SET shipped_quantity = shipped_quantity + ? WHERE id = ? AND shipped_quantity + ? <= ordered_quantity", item.shippedQuantity(), item.salesOrderItemId(), item.shippedQuantity());
        }
        SalesOrder after = readOrder(order.id());
        jdbcTemplate.update("UPDATE sal_sales_order SET shipped_quantity = ?, status = ?, updated_by = 0 WHERE id = ?", after.items().stream().mapToInt(SalesOrderItem::shippedQuantity).sum(), after.items().stream().allMatch(item -> item.pendingQuantity() == 0) ? "已完成" : "已审核", order.id());
        if (jdbcTemplate.update("UPDATE wh_stock_out_order SET status = '已确认', confirmed_at = CURRENT_TIMESTAMP, confirmed_by = 0 WHERE id = ? AND status = '草稿'", id) != 1) throw conflict("出库单状态已变化，请刷新后重试");
        return readStockOut(id);
    }

    private SalesReturnOrder createReturnRelational(SalesReturnRequest request) {
        StockOutOrder stockOut = findStockOut(request.sourceStockOutId()); assertStatus(stockOut.status(), "已确认");
        SalesOrder order = findOrder(stockOut.salesOrderId()); List<SalesReturnOrder.Item> items = new ArrayList<>(); Set<Long> itemIds = new HashSet<>();
        for (SalesReturnRequest.Item requestItem : request.items()) {
            if (!itemIds.add(requestItem.sourceStockOutItemId())) throw badRequest("退货明细不能重复商品");
            StockOutOrder.Item source = findStockOutItem(stockOut, requestItem.sourceStockOutItemId());
            if (requestItem.returnedQuantity() > source.returnableQuantity()) throw conflict("退货数量超过可退数量");
            items.add(new SalesReturnOrder.Item(requestItem.sourceStockOutItemId(), source.id(), source.salesOrderItemId(), source.productId(), source.productName(), requestItem.returnedQuantity()));
        }
        long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 8000) + 1 FROM sal_sales_return", Long.class); String no = nextOrderNo("RT", id, 8000);
        jdbcTemplate.update("INSERT INTO sal_sales_return (id, return_no, source_stock_out_id, source_stock_out_no, sales_order_id, sales_order_no, customer_id, customer_name, warehouse_id, warehouse_name, return_date, status, total_quantity, reason, remark, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '草稿', ?, ?, ?, 0)",
                id, no, stockOut.id(), stockOut.stockOutNo(), order.id(), order.orderNo(), order.customerId(), order.customerName(), stockOut.warehouseId(), stockOut.warehouseName(), request.returnDate(), items.stream().mapToInt(SalesReturnOrder.Item::returnedQuantity).sum(), request.reason().trim(), request.remark());
        for (SalesReturnOrder.Item item : items) jdbcTemplate.update("INSERT INTO sal_sales_return_item (sales_return_id, stock_out_order_item_id, sales_order_item_id, product_id, product_name, unit, returned_quantity) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, item.stockOutItemId(), item.salesOrderItemId(), item.productId(), item.productName(), order.items().stream().filter(line -> line.id().equals(item.salesOrderItemId())).findFirst().orElseThrow().unit(), item.returnedQuantity());
        return readReturn(id);
    }

    private SalesReturnOrder changeReturnStatusRelational(Long id, String from, String to, String comment) {
        SalesReturnOrder current = findReturn(id); assertStatus(current.status(), from);
        if (jdbcTemplate.update("UPDATE sal_sales_return SET status = ?, approval_comment = ? WHERE id = ? AND status = ?", to, comment, id, from) != 1) throw conflict("退货单状态已变化，请刷新后重试");
        return readReturn(id);
    }

    private SalesReturnOrder confirmReturnRelational(Long id) {
        SalesReturnOrder current = findReturn(id); assertStatus(current.status(), "已审核");
        StockOutOrder stockOut = findStockOut(current.stockOutId()); SalesOrder order = findOrder(current.salesOrderId());
        for (SalesReturnOrder.Item item : current.items()) if (item.returnedQuantity() > findStockOutItem(stockOut, item.stockOutItemId()).returnableQuantity()) throw conflict("当前出库单可退数量不足，请刷新后重试");
        for (SalesReturnOrder.Item item : current.items()) {
            warehouseService.recordBusinessMovement(current.warehouseId(), item.productId(), item.returnedQuantity(), true, "销售退货", current.returnNo(), current.remark());
            jdbcTemplate.update("UPDATE wh_stock_out_order_item SET returned_quantity = returned_quantity + ? WHERE id = ?", item.returnedQuantity(), item.stockOutItemId());
            jdbcTemplate.update("UPDATE sal_sales_order_item SET returned_quantity = returned_quantity + ? WHERE id = ?", item.returnedQuantity(), item.salesOrderItemId());
        }
        int returned = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(returned_quantity), 0) FROM sal_sales_order_item WHERE sales_order_id = ?", Integer.class, order.id());
        jdbcTemplate.update("UPDATE sal_sales_order SET returned_quantity = ?, updated_by = 0 WHERE id = ?", returned, order.id());
        if (jdbcTemplate.update("UPDATE sal_sales_return SET status = '已确认', confirmed_at = CURRENT_TIMESTAMP, confirmed_by = 0 WHERE id = ? AND status = '已审核'", id) != 1) throw conflict("退货单状态已变化，请刷新后重试");
        return readReturn(id);
    }

    private SalesOrder readOrder(Long id) {
        List<SalesOrder> rows = jdbcTemplate.query("SELECT id, order_no, customer_id, customer_name, warehouse_id, warehouse_name, order_date, required_ship_date, status, total_quantity, shipped_quantity, returned_quantity, total_amount, remark, approval_comment FROM sal_sales_order WHERE id = ?",
                (rs, row) -> new SalesOrder(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"), rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getDate("order_date").toLocalDate(), rs.getDate("required_ship_date") == null ? null : rs.getDate("required_ship_date").toLocalDate(), rs.getString("status"), rs.getInt("total_quantity"), rs.getInt("shipped_quantity"), rs.getInt("returned_quantity"), rs.getBigDecimal("total_amount"), rs.getString("remark"), rs.getString("approval_comment"), readOrderItems(id)), id);
        if (rows.isEmpty()) throw notFound("销售订单不存在"); return rows.get(0);
    }

    private List<SalesOrderItem> readOrderItems(Long id) { return jdbcTemplate.query("SELECT id, product_id, sku, product_name, unit, ordered_quantity, shipped_quantity, returned_quantity, unit_price, line_amount FROM sal_sales_order_item WHERE sales_order_id = ? ORDER BY id", (rs, row) -> new SalesOrderItem(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"), rs.getString("product_name"), rs.getString("unit"), rs.getInt("ordered_quantity"), rs.getInt("shipped_quantity"), rs.getInt("returned_quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_amount")), id); }

    private StockOutOrder readStockOut(Long id) {
        List<StockOutOrder> rows = jdbcTemplate.query("SELECT id, stock_out_no, sales_order_id, sales_order_no, customer_name, warehouse_id, warehouse_name, stock_out_date, status, total_quantity, remark FROM wh_stock_out_order WHERE id = ?", (rs, row) -> new StockOutOrder(rs.getLong("id"), rs.getString("stock_out_no"), rs.getLong("sales_order_id"), rs.getString("sales_order_no"), rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getDate("stock_out_date").toLocalDate(), rs.getString("status"), rs.getInt("total_quantity"), rs.getString("remark"), readStockOutItems(id)), id);
        if (rows.isEmpty()) throw notFound("销售出库单不存在"); return rows.get(0);
    }

    private List<StockOutOrder.Item> readStockOutItems(Long id) { return jdbcTemplate.query("SELECT id, sales_order_item_id, product_id, product_name, shipped_quantity, returned_quantity FROM wh_stock_out_order_item WHERE stock_out_order_id = ? ORDER BY id", (rs, row) -> new StockOutOrder.Item(rs.getLong("id"), rs.getLong("sales_order_item_id"), rs.getLong("product_id"), rs.getString("product_name"), rs.getInt("shipped_quantity"), rs.getInt("returned_quantity")), id); }

    private SalesReturnOrder readReturn(Long id) {
        List<SalesReturnOrder> rows = jdbcTemplate.query("SELECT id, return_no, source_stock_out_id, source_stock_out_no, sales_order_id, sales_order_no, customer_name, warehouse_id, warehouse_name, return_date, status, total_quantity, reason, remark FROM sal_sales_return WHERE id = ?", (rs, row) -> new SalesReturnOrder(rs.getLong("id"), rs.getString("return_no"), rs.getLong("source_stock_out_id"), rs.getString("source_stock_out_no"), rs.getLong("sales_order_id"), rs.getString("sales_order_no"), rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getDate("return_date").toLocalDate(), rs.getString("status"), rs.getInt("total_quantity"), rs.getString("reason"), rs.getString("remark"), readReturnItems(id)), id);
        if (rows.isEmpty()) throw notFound("销售退货单不存在"); return rows.get(0);
    }

    private List<SalesReturnOrder.Item> readReturnItems(Long id) { return jdbcTemplate.query("SELECT id, stock_out_order_item_id, sales_order_item_id, product_id, product_name, returned_quantity FROM sal_sales_return_item WHERE sales_return_id = ? ORDER BY id", (rs, row) -> new SalesReturnOrder.Item(rs.getLong("id"), rs.getLong("stock_out_order_item_id"), rs.getLong("sales_order_item_id"), rs.getLong("product_id"), rs.getString("product_name"), rs.getInt("returned_quantity")), id); }

    private boolean relationalDataAvailable() { return jdbcTemplate != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM md_customer", Integer.class) > 0; }

    private Customer enabledCustomer(Long id) {
        Customer customer = customerService.findById(id);
        if (!"启用".equals(customer.status())) throw badRequest("客户已停用，不能销售");
        return customer;
    }

    private SalesOrderItem findOrderItem(SalesOrder order, Long id) {
        return order.items().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> badRequest("销售订单明细不存在"));
    }

    private StockOutOrder.Item findStockOutItem(StockOutOrder order, Long id) {
        return order.items().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> badRequest("出库明细不存在"));
    }

    private void validateDates(LocalDate orderDate, LocalDate shipDate) {
        if (shipDate != null && shipDate.isBefore(orderDate)) throw badRequest("要求发货日期不能早于订单日期");
    }

    private String nextOrderNo(String prefix, long id, long base) {
        return prefix + LocalDate.now().format(DATE_FORMAT) + "-" + String.format("%04d", id - base);
    }

    private int totalQuantity(List<SalesOrderItem> items) { return items.stream().mapToInt(SalesOrderItem::orderedQuantity).sum(); }
    private BigDecimal totalAmount(List<SalesOrderItem> items) { return items.stream().map(SalesOrderItem::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP); }

    private SalesOrder replaceStatus(SalesOrder order, String status, String comment) {
        return new SalesOrder(order.id(), order.orderNo(), order.customerId(), order.customerName(), order.warehouseId(), order.warehouseName(), order.orderDate(), order.requiredShipDate(), status,
                order.totalQuantity(), order.shippedQuantity(), order.returnedQuantity(), order.totalAmount(), order.remark(), comment, order.items());
    }
    private void replaceOrder(SalesOrder oldValue, SalesOrder newValue) { orders.set(orders.indexOf(oldValue), newValue); }
    private void replaceStockOut(StockOutOrder oldValue, StockOutOrder newValue) { stockOutOrders.set(stockOutOrders.indexOf(oldValue), newValue); }
    private void replaceReturn(SalesReturnOrder oldValue, SalesReturnOrder newValue) { returnOrders.set(returnOrders.indexOf(oldValue), newValue); }
    private SalesReturnOrder replaceReturnStatus(SalesReturnOrder item, String status, String comment) {
        return new SalesReturnOrder(item.id(), item.returnNo(), item.stockOutId(), item.stockOutNo(), item.salesOrderId(), item.salesOrderNo(), item.customerName(), item.warehouseId(), item.warehouseName(), item.returnDate(), status, item.totalQuantity(), item.reason(), item.remark(), item.items());
    }
    private SalesReturnOrder changeReturnStatus(Long id, String from, String to, String comment) {
        SalesReturnOrder current = findReturn(id);
        assertStatus(current.status(), from);
        SalesReturnOrder updated = replaceReturnStatus(current, to, to.equals("已驳回") ? comment : blankToNull(comment));
        replaceReturn(current, updated);
        return updated;
    }
    private int findItemIndex(List<SalesOrderItem> items, Long id) { for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(id)) return i; throw badRequest("销售订单明细不存在"); }
    private int findStockOutItemIndex(List<StockOutOrder.Item> items, Long id) { for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(id)) return i; throw badRequest("出库明细不存在"); }
    private void assertStatus(String current, String... allowed) { for (String status : allowed) if (status.equals(current)) return; throw conflict("当前状态不允许执行此操作"); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public synchronized State exportState() {
        return new State(List.copyOf(orders), List.copyOf(stockOutOrders), List.copyOf(returnOrders), nextOrderId.get(), nextOrderItemId.get(), nextStockOutId.get(), nextStockOutItemId.get(), nextReturnId.get(), nextReturnItemId.get());
    }
    public synchronized void restoreState(State state) {
        orders.clear(); orders.addAll(state.orders()); stockOutOrders.clear(); stockOutOrders.addAll(state.stockOutOrders()); returnOrders.clear(); returnOrders.addAll(state.returnOrders());
        nextOrderId.set(state.nextOrderId()); nextOrderItemId.set(state.nextOrderItemId()); nextStockOutId.set(state.nextStockOutId()); nextStockOutItemId.set(state.nextStockOutItemId()); nextReturnId.set(state.nextReturnId()); nextReturnItemId.set(state.nextReturnItemId());
    }
    public record State(List<SalesOrder> orders, List<StockOutOrder> stockOutOrders, List<SalesReturnOrder> returnOrders,
                        long nextOrderId, long nextOrderItemId, long nextStockOutId, long nextStockOutItemId, long nextReturnId, long nextReturnItemId) {}
}
