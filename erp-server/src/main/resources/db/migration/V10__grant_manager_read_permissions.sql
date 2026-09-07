-- 经营管理者可查看各业务页面所需的只读数据，但不获得任何新增、审核或确认权限。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'manager'
   AND p.permission_code IN (
       'base:product:list', 'base:supplier:list', 'base:customer:list', 'base:warehouse:list',
       'purchase:order:list', 'purchase:receipt:list',
       'sales:order:list', 'sales:stockout:list', 'sales:return:list',
       'inventory:balance:view', 'inventory:flow:view', 'report:view', 'system:operation-log:view'
   );
