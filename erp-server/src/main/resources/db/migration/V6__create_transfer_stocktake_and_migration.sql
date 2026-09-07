CREATE TABLE wh_transfer_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(50) NOT NULL,
    from_warehouse_id BIGINT NOT NULL,
    to_warehouse_id BIGINT NOT NULL,
    transfer_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_by BIGINT,
    confirmed_at DATETIME,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_transfer_from_status_date (from_warehouse_id, status, transfer_date),
    KEY idx_transfer_to_status_date (to_warehouse_id, status, transfer_date),
    CONSTRAINT ck_transfer_warehouses CHECK (from_warehouse_id <> to_warehouse_id),
    CONSTRAINT fk_transfer_from_warehouse FOREIGN KEY (from_warehouse_id) REFERENCES md_warehouse (id),
    CONSTRAINT fk_transfer_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES md_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wh_transfer_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    remark VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_item_product (transfer_order_id, product_id),
    CONSTRAINT ck_transfer_item_quantity CHECK (quantity > 0),
    CONSTRAINT fk_transfer_item_order FOREIGN KEY (transfer_order_id) REFERENCES wh_transfer_order (id),
    CONSTRAINT fk_transfer_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wh_stocktake_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stocktake_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    stocktake_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_by BIGINT,
    confirmed_at DATETIME,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_no (stocktake_no),
    KEY idx_stocktake_warehouse_status_date (warehouse_id, status, stocktake_date),
    CONSTRAINT fk_stocktake_warehouse FOREIGN KEY (warehouse_id) REFERENCES md_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wh_stocktake_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stocktake_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    book_quantity DECIMAL(18,4) NOT NULL,
    counted_quantity DECIMAL(18,4) NOT NULL,
    difference_quantity DECIMAL(18,4) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    balance_version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_item_product (stocktake_order_id, product_id),
    CONSTRAINT ck_stocktake_counted_quantity CHECK (counted_quantity >= 0),
    CONSTRAINT fk_stocktake_item_order FOREIGN KEY (stocktake_order_id) REFERENCES wh_stocktake_order (id),
    CONSTRAINT fk_stocktake_item_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_data_migration (
    migration_key VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_updated_at DATETIME,
    summary_json JSON,
    started_at DATETIME,
    finished_at DATETIME,
    error_message VARCHAR(1000),
    PRIMARY KEY (migration_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
