INSERT IGNORE INTO sys_permission (id, parent_id, name, permission_code, permission_type, path, sort_no, status) VALUES
    (110, 100, '角色列表', 'system:role:list', 'MENU', '/settings/roles', 2, 1),
    (106, 100, '系统设置', 'system:settings:manage', 'BUTTON', '/settings', 6, 1),
    (107, 100, '商品管理', 'base:product:list', 'MENU', '/products', 7, 1),
    (108, 100, '供应商查看', 'base:supplier:list', 'MENU', '/settings/suppliers', 8, 1),
    (109, 100, '客户查看', 'base:customer:list', 'MENU', '/settings/customers', 9, 1),
    (111, 100, '操作日志', 'system:operation-log:view', 'MENU', '/settings/operation-logs', 10, 1),
    (210, 200, '采购订单创建', 'purchase:order:create', 'BUTTON', NULL, 3, 1),
    (211, 200, '采购订单查看', 'purchase:order:list', 'BUTTON', NULL, 4, 1),
    (212, 200, '采购入库查看', 'purchase:receipt:list', 'BUTTON', NULL, 5, 1),
    (213, 200, '采购入库确认', 'purchase:receipt:confirm', 'BUTTON', NULL, 6, 1),
    (310, 300, '销售订单创建', 'sales:order:create', 'BUTTON', NULL, 4, 1),
    (311, 300, '销售订单查看', 'sales:order:list', 'BUTTON', NULL, 5, 1),
    (312, 300, '销售出库查看', 'sales:stockout:list', 'BUTTON', NULL, 6, 1),
    (313, 300, '销售出库确认', 'sales:stockout:confirm', 'BUTTON', NULL, 7, 1),
    (314, 300, '销售退货查看', 'sales:return:list', 'BUTTON', NULL, 8, 1),
    (315, 300, '销售退货创建', 'sales:return:create', 'BUTTON', NULL, 9, 1),
    (316, 300, '销售退货审核', 'sales:return:approve', 'BUTTON', NULL, 10, 1),
    (410, 400, '报表查看', 'report:view', 'BUTTON', NULL, 1, 1),
    (510, 500, '库存余额查看', 'inventory:balance:view', 'BUTTON', NULL, 4, 1),
    (511, 500, '库存流水查看', 'inventory:flow:view', 'BUTTON', NULL, 3, 1),
    (520, 500, '仓库档案管理', 'base:warehouse:manage', 'BUTTON', NULL, 5, 1),
    (521, 500, '商品档案管理', 'base:product:manage', 'BUTTON', NULL, 6, 1),
    (522, 500, '供应商管理', 'base:supplier:manage', 'BUTTON', NULL, 7, 1),
    (523, 500, '客户管理', 'base:customer:manage', 'BUTTON', NULL, 8, 1),
    (524, 500, '手工库存调整', 'inventory:movement:manage', 'BUTTON', NULL, 9, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'admin';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'manager' AND p.permission_code = 'system:operation-log:view';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'purchaser' AND p.permission_code IN ('purchase:order:list', 'purchase:order:create', 'base:supplier:list', 'base:product:list');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'purchase_manager' AND p.permission_code IN ('purchase:order:list', 'purchase:order:approve', 'report:view');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'seller' AND p.permission_code IN ('sales:order:list', 'sales:order:create', 'base:customer:list', 'base:product:list');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'sales_manager' AND p.permission_code IN ('sales:order:list', 'sales:order:approve', 'report:view');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'warehouse' AND p.permission_code IN ('inventory:balance:view', 'inventory:flow:view', 'inventory:movement:manage', 'inventory:transfer:manage', 'inventory:stocktake:manage', 'base:warehouse:list', 'base:product:list');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'manager' AND p.permission_code IN ('report:view', 'inventory:balance:view', 'inventory:flow:view', 'purchase:order:list', 'sales:order:list');
