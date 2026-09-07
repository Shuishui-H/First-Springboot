ALTER TABLE sys_user
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 AFTER must_change_password,
    ADD COLUMN locked_until DATETIME NULL AFTER failed_login_count;

CREATE INDEX idx_sys_user_login_state ON sys_user (status, locked_until);
