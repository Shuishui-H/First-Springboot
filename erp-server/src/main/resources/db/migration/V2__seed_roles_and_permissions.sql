INSERT IGNORE INTO sys_role (id, role_code, role_name, status, remark) VALUES
    (1, 'admin', '系统管理员', 1, '系统内置，拥有全部权限'),
    (2, 'purchaser', '采购员', 1, '创建和提交采购订单'),
    (3, 'purchase_manager', '采购主管', 1, '审核采购订单'),
    (4, 'seller', '销售员', 1, '创建和提交销售订单'),
    (5, 'sales_manager', '销售主管', 1, '审核销售订单'),
    (6, 'warehouse', '仓库管理员', 1, '确认出入库与退货'),
    (7, 'manager', '经营管理者', 1, '经营报表只读');

INSERT IGNORE INTO sys_permission (id, parent_id, name, permission_code, permission_type, path, sort_no, status) VALUES
    (100, 0, '系统设置', NULL, 'MENU', '/settings', 1, 1),
    (101, 100, '用户管理', 'system:user:list', 'MENU', '/settings/users', 1, 1),
    (1011, 101, '新增用户', 'system:user:add', 'BUTTON', NULL, 1, 1),
    (1012, 101, '编辑用户', 'system:user:edit', 'BUTTON', NULL, 2, 1),
    (1013, 101, '启停用户', 'system:user:status', 'BUTTON', NULL, 3, 1),
    (1014, 101, '重置密码', 'system:user:password', 'BUTTON', NULL, 4, 1),
    (102, 100, '角色权限', 'system:role:list', 'MENU', '/settings/roles', 2, 1),
    (1021, 102, '配置角色权限', 'system:role:config', 'BUTTON', NULL, 1, 1),
    (1022, 102, '启停角色', 'system:role:status', 'BUTTON', NULL, 2, 1),
    (103, 100, '仓库管理', 'base:warehouse:list', 'MENU', '/settings/warehouses', 3, 1),
    (104, 100, '供应商管理', 'base:supplier:list', 'MENU', '/settings/suppliers', 4, 1),
    (105, 100, '客户管理', 'base:customer:list', 'MENU', '/settings/customers', 5, 1),
    (200, 0, '采购管理', 'purchase:order:list', 'MENU', '/purchases', 2, 1),
    (201, 200, '采购审核', 'purchase:order:approve', 'BUTTON', NULL, 1, 1),
    (202, 200, '采购入库确认', 'purchase:receipt:confirm', 'BUTTON', NULL, 2, 1),
    (300, 0, '销售管理', 'sales:order:list', 'MENU', '/sales', 3, 1),
    (301, 300, '销售审核', 'sales:order:approve', 'BUTTON', NULL, 1, 1),
    (302, 300, '销售出库确认', 'sales:stockout:confirm', 'BUTTON', NULL, 2, 1),
    (303, 300, '销售退货确认', 'sales:return:confirm', 'BUTTON', NULL, 3, 1),
    (400, 0, '业务报表', 'report:view', 'MENU', '/reports', 4, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;
