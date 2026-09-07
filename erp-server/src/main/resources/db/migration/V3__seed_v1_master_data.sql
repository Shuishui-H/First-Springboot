INSERT IGNORE INTO md_product (id, sku, name, category, unit, price, safety_stock, status) VALUES
    (1, 'SP-1001', 'A4 复印纸', '办公耗材', '包', 26.80, 40, 1),
    (2, 'SP-1002', '黑色签字笔', '办公耗材', '支', 2.50, 30, 1),
    (3, 'SP-2001', '无线鼠标', '办公设备', '个', 79.00, 10, 1);

INSERT IGNORE INTO md_warehouse (id, code, name, manager, address, status, remark) VALUES
    (1, 'WH-MAIN', '主仓', '张伟', '默认演示仓库', 1, 'V1 初始主仓'),
    (2, 'WH-EAST', '华东仓', '李娜', '默认演示仓库', 1, 'V1 初始华东仓');

INSERT IGNORE INTO md_supplier (id, code, name, contact, phone, status, remark) VALUES
    (1001, 'SUP-0001', '上海优采办公用品有限公司', '王玲', '13800000001', 1, 'V1 演示供应商'),
    (1002, 'SUP-0002', '深圳智联办公设备有限公司', '陈涛', '13800000002', 1, 'V1 演示供应商');

INSERT IGNORE INTO md_customer (id, code, name, contact, phone, status, remark) VALUES
    (6001, 'CUS-1001', '上海星河办公有限公司', '周敏', '13800001001', 1, 'V1 演示客户'),
    (6002, 'CUS-1002', '杭州云启科技有限公司', '陈浩', '13900001002', 1, 'V1 演示客户');

INSERT IGNORE INTO wh_inventory_balance (warehouse_id, product_id, quantity, locked_quantity, available_quantity, version) VALUES
    (1, 1, 120, 0, 120, 0),
    (1, 2, 18, 0, 18, 0),
    (1, 3, 36, 0, 36, 0);
