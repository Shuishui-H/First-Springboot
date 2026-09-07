INSERT IGNORE INTO sys_permission (id, parent_id, name, permission_code, permission_type, path, sort_no, status) VALUES
    (500, 0, '库存操作', NULL, 'MENU', '/warehouse/operations', 5, 1),
    (501, 500, '仓库调拨', 'inventory:transfer:manage', 'BUTTON', NULL, 1, 1),
    (502, 500, '库存盘点', 'inventory:stocktake:manage', 'BUTTON', NULL, 2, 1),
    (503, 500, '库存流水查询', 'inventory:flow:view', 'BUTTON', NULL, 3, 1),
    (504, 500, '库存余额查询', 'inventory:balance:view', 'BUTTON', NULL, 4, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role_id, permission_id FROM (
    SELECT 1 AS role_id, 501 AS permission_id UNION ALL SELECT 1, 502 UNION ALL SELECT 1, 503 UNION ALL SELECT 1, 504
    UNION ALL SELECT 6, 501 UNION ALL SELECT 6, 502 UNION ALL SELECT 6, 503 UNION ALL SELECT 6, 504
) AS required_permissions;
