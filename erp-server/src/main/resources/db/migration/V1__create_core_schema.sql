CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    role_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role_status (role_id, status),
    CONSTRAINT fk_sys_user_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(80) NOT NULL,
    permission_code VARCHAR(100),
    permission_type VARCHAR(10) NOT NULL,
    path VARCHAR(150),
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code),
    KEY idx_sys_permission_parent_sort (parent_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    module VARCHAR(40) NOT NULL,
    action VARCHAR(40) NOT NULL,
    business_type VARCHAR(40),
    business_id BIGINT,
    business_no VARCHAR(50),
    before_summary JSON,
    after_summary JSON,
    operator_id BIGINT,
    operator_name VARCHAR(50),
    ip_address VARCHAR(45),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_operation_business_time (business_no, created_at),
    KEY idx_operation_operator_time (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS md_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(32) NOT NULL,
    name VARCHAR(80) NOT NULL,
    category VARCHAR(40) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    price DECIMAL(18,2) NOT NULL DEFAULT 0,
    safety_stock DECIMAL(18,4) NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_product_sku (sku),
    KEY idx_md_product_status_category (status, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS md_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(80) NOT NULL,
    manager VARCHAR(50),
    address VARCHAR(200),
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_warehouse_code (code),
    KEY idx_md_warehouse_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS md_supplier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    contact VARCHAR(50),
    phone VARCHAR(30),
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_supplier_code (code),
    KEY idx_md_supplier_status_name (status, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS md_customer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    contact VARCHAR(50),
    phone VARCHAR(30),
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_customer_code (code),
    KEY idx_md_customer_status_name (status, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pur_purchase_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(100) NOT NULL,
    order_date DATE NOT NULL,
    expected_arrival_date DATE,
    status VARCHAR(20) NOT NULL,
    total_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    approval_comment VARCHAR(255),
    submitted_at DATETIME,
    approved_at DATETIME,
    approved_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_no (order_no),
    KEY idx_purchase_order_status_date (status, order_date),
    KEY idx_purchase_order_supplier (supplier_id),
    CONSTRAINT fk_purchase_order_supplier FOREIGN KEY (supplier_id) REFERENCES md_supplier (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pur_purchase_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(32) NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    ordered_quantity DECIMAL(18,4) NOT NULL,
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(18,2) NOT NULL,
    line_amount DECIMAL(18,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_purchase_item_order (purchase_order_id),
    KEY idx_purchase_item_product (product_id),
    CONSTRAINT fk_purchase_item_order FOREIGN KEY (purchase_order_id) REFERENCES pur_purchase_order (id),
    CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_purchase_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_no VARCHAR(50) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    purchase_order_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(80) NOT NULL,
    stock_in_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    confirmed_at DATETIME,
    confirmed_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_receipt_no (receipt_no),
    KEY idx_purchase_receipt_order (purchase_order_id),
    KEY idx_purchase_receipt_status_date (status, stock_in_date),
    CONSTRAINT fk_purchase_receipt_order FOREIGN KEY (purchase_order_id) REFERENCES pur_purchase_order (id),
    CONSTRAINT fk_purchase_receipt_warehouse FOREIGN KEY (warehouse_id) REFERENCES md_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_purchase_receipt_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_receipt_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    received_quantity DECIMAL(18,4) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_receipt_item_receipt (purchase_receipt_id),
    CONSTRAINT fk_receipt_item_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES wh_purchase_receipt (id),
    CONSTRAINT fk_receipt_item_order_item FOREIGN KEY (purchase_order_item_id) REFERENCES pur_purchase_order_item (id),
    CONSTRAINT fk_receipt_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sal_sales_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(80) NOT NULL,
    order_date DATE NOT NULL,
    required_ship_date DATE,
    status VARCHAR(20) NOT NULL,
    total_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    shipped_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    approval_comment VARCHAR(255),
    submitted_at DATETIME,
    approved_at DATETIME,
    approved_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_no (order_no),
    KEY idx_sales_order_status_date (status, order_date),
    KEY idx_sales_order_customer (customer_id),
    CONSTRAINT fk_sales_order_customer FOREIGN KEY (customer_id) REFERENCES md_customer (id),
    CONSTRAINT fk_sales_order_warehouse FOREIGN KEY (warehouse_id) REFERENCES md_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sal_sales_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sales_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(32) NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    ordered_quantity DECIMAL(18,4) NOT NULL,
    shipped_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    returned_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(18,2) NOT NULL,
    line_amount DECIMAL(18,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sales_item_order (sales_order_id),
    KEY idx_sales_item_product (product_id),
    CONSTRAINT fk_sales_item_order FOREIGN KEY (sales_order_id) REFERENCES sal_sales_order (id),
    CONSTRAINT fk_sales_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_stock_out_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_out_no VARCHAR(50) NOT NULL,
    sales_order_id BIGINT NOT NULL,
    sales_order_no VARCHAR(50) NOT NULL,
    customer_id BIGINT,
    customer_name VARCHAR(100) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(80) NOT NULL,
    stock_out_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    confirmed_at DATETIME,
    confirmed_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_out_no (stock_out_no),
    KEY idx_stock_out_order_status_date (status, stock_out_date),
    KEY idx_stock_out_sales_order (sales_order_id),
    CONSTRAINT fk_stock_out_sales_order FOREIGN KEY (sales_order_id) REFERENCES sal_sales_order (id),
    CONSTRAINT fk_stock_out_warehouse FOREIGN KEY (warehouse_id) REFERENCES md_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_stock_out_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_out_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    shipped_quantity DECIMAL(18,4) NOT NULL,
    returned_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_stock_out_item_order (stock_out_order_id),
    CONSTRAINT fk_stock_out_item_order FOREIGN KEY (stock_out_order_id) REFERENCES wh_stock_out_order (id),
    CONSTRAINT fk_stock_out_item_sales_item FOREIGN KEY (sales_order_item_id) REFERENCES sal_sales_order_item (id),
    CONSTRAINT fk_stock_out_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sal_sales_return (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_no VARCHAR(50) NOT NULL,
    source_stock_out_id BIGINT NOT NULL,
    source_stock_out_no VARCHAR(50) NOT NULL,
    sales_order_id BIGINT NOT NULL,
    sales_order_no VARCHAR(50) NOT NULL,
    customer_id BIGINT,
    customer_name VARCHAR(100) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(80) NOT NULL,
    return_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    reason VARCHAR(255),
    remark VARCHAR(255),
    approval_comment VARCHAR(255),
    confirmed_at DATETIME,
    confirmed_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_return_no (return_no),
    KEY idx_sales_return_status_date (status, return_date),
    KEY idx_sales_return_stock_out (source_stock_out_id),
    CONSTRAINT fk_sales_return_stock_out FOREIGN KEY (source_stock_out_id) REFERENCES wh_stock_out_order (id),
    CONSTRAINT fk_sales_return_sales_order FOREIGN KEY (sales_order_id) REFERENCES sal_sales_order (id),
    CONSTRAINT fk_sales_return_warehouse FOREIGN KEY (warehouse_id) REFERENCES md_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sal_sales_return_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sales_return_id BIGINT NOT NULL,
    stock_out_order_item_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    returned_quantity DECIMAL(18,4) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sales_return_item_return (sales_return_id),
    CONSTRAINT fk_sales_return_item_return FOREIGN KEY (sales_return_id) REFERENCES sal_sales_return (id),
    CONSTRAINT fk_sales_return_item_stock_out_item FOREIGN KEY (stock_out_order_item_id) REFERENCES wh_stock_out_order_item (id),
    CONSTRAINT fk_sales_return_item_sales_item FOREIGN KEY (sales_order_item_id) REFERENCES sal_sales_order_item (id),
    CONSTRAINT fk_sales_return_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_inventory_balance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    locked_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    available_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_warehouse_product (warehouse_id, product_id),
    KEY idx_inventory_product (product_id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES md_warehouse (id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_stock_flow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flow_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(80) NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(32) NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    change_quantity DECIMAL(18,4) NOT NULL,
    before_quantity DECIMAL(18,4) NOT NULL,
    after_quantity DECIMAL(18,4) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(50) NOT NULL,
    remark VARCHAR(255),
    operator_id BIGINT,
    operator_name VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_flow_source (source_type, source_id, product_id, business_type),
    UNIQUE KEY uk_stock_flow_no (flow_no),
    KEY idx_stock_flow_query (warehouse_id, product_id, created_at),
    KEY idx_stock_flow_source_query (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
