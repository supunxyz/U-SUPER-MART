-- Operational tables and additive columns required by the current POS application.
-- Run after V1__core_pos.sql and V2__sale_loyalty.sql. This migration creates
-- missing structures, adds missing columns, removes V1's split-payment block,
-- and backfills a payment row for completed legacy sales that have none.

CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id INT AUTO_INCREMENT PRIMARY KEY,
    supplier_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NULL,
    address VARCHAR(255) NULL,
    email VARCHAR(100) NULL
);

CREATE TABLE IF NOT EXISTS suppliers_purchases (
    purchase_id INT AUTO_INCREMENT PRIMARY KEY,
    supplier_id INT NOT NULL,
    invoice_number VARCHAR(50) NULL,
    purchase_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(12,2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PAID',
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id)
);

CREATE TABLE IF NOT EXISTS purchase_items (
    purchase_item_id INT AUTO_INCREMENT PRIMARY KEY,
    purchase_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_purchase_item_purchase FOREIGN KEY (purchase_id) REFERENCES suppliers_purchases(purchase_id),
    CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS product_batches (
    batch_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    purchase_id INT NULL,
    purchase_item_id INT NULL,
    source_type ENUM('OPENING','PURCHASE','ADJUSTMENT') NOT NULL DEFAULT 'PURCHASE',
    cost_price DECIMAL(12,2) NOT NULL,
    selling_price DECIMAL(12,2) NOT NULL,
    retail_price DECIMAL(12,2) NULL,
    expiry_date DATE NULL,
    quantity_received DECIMAL(12,3) NOT NULL,
    quantity_available DECIMAL(12,3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_batch_product (product_id),
    INDEX idx_batch_product_expiry (product_id, expiry_date),
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT fk_batch_purchase FOREIGN KEY (purchase_id) REFERENCES suppliers_purchases(purchase_id),
    CONSTRAINT fk_batch_purchase_item FOREIGN KEY (purchase_item_id) REFERENCES purchase_items(purchase_item_id)
);

CREATE TABLE IF NOT EXISTS expenses (
    expense_id INT AUTO_INCREMENT PRIMARY KEY,
    expense_type VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    amount DECIMAL(12,2) NOT NULL,
    expense_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id INT NULL,
    CONSTRAINT fk_expense_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS promotions (
    promotion_id INT AUTO_INCREMENT PRIMARY KEY,
    promotion_name VARCHAR(100) NOT NULL,
    product_id INT NOT NULL,
    discount_type ENUM('PERCENTAGE','FIXED') NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    customer_scope ENUM('ALL','LOYALTY') NOT NULL DEFAULT 'ALL',
    start_datetime DATETIME NOT NULL,
    end_datetime DATETIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_promotion_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS loyalty_cards (
    loyalty_card_id INT AUTO_INCREMENT PRIMARY KEY,
    card_code VARCHAR(30) NOT NULL UNIQUE,
    customer_id INT NULL,
    status ENUM('AVAILABLE','ASSIGNED','BLOCKED') NOT NULL DEFAULT 'AVAILABLE',
    assigned_at DATETIME NULL,
    blocked_at DATETIME NULL,
    CONSTRAINT fk_loyalty_card_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE IF NOT EXISTS cashier_shifts (
    shift_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    opening_cash DECIMAL(12,2) NOT NULL,
    closing_cash DECIMAL(12,2) NULL,
    expected_cash DECIMAL(12,2) NULL,
    cash_difference DECIMAL(12,2) NULL,
    opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,
    status VARCHAR(10) NOT NULL,
    open_user_id INT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN user_id ELSE NULL END) STORED,
    UNIQUE KEY uq_cashier_shifts_one_open_user (open_user_id),
    CONSTRAINT fk_shift_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS sale_item_batch_allocations (
    allocation_id INT AUTO_INCREMENT PRIMARY KEY,
    sale_item_id INT NOT NULL,
    batch_id INT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_sale_item_batch (sale_item_id, batch_id),
    CONSTRAINT fk_allocation_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items(sale_item_id),
    CONSTRAINT fk_allocation_batch FOREIGN KEY (batch_id) REFERENCES product_batches(batch_id)
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS retail_price DECIMAL(12,2) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS loyalty_points INT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS loyalty_customer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS shift_id INT NULL;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS retail_price DECIMAL(12,2) NULL;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS cost_price DECIMAL(12,2) NULL;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS regular_unit_price DECIMAL(12,2) NULL;
ALTER TABLE sale_payments ADD COLUMN IF NOT EXISTS transfer_reference VARCHAR(100) NULL;
ALTER TABLE sale_payments ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100) NULL;

-- Preserve payment reporting for completed sales recorded before sale_payments existed.
INSERT INTO sale_payments (sale_id, payment_method, amount, tendered_amount, change_amount, payment_status)
SELECT s.sale_id, s.payment_method, s.total_amount, NULL, 0, 'COMPLETED'
FROM sales s
LEFT JOIN sale_payments existing ON existing.sale_id = s.sale_id
WHERE s.sale_status = 'COMPLETED' AND existing.payment_id IS NULL;

-- V1 originally made sale_id unique, which prevented the split-payment flow.
-- Drop that index only when it is a unique index in the current schema.
-- Keep a nonunique index for the sale_payments foreign key before dropping its old unique index.
SET @add_sale_payment_index = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE sale_payments ADD INDEX ix_sale_payments_sale_id (sale_id)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sale_payments'
      AND index_name = 'ix_sale_payments_sale_id'
);
PREPARE add_sale_payment_index FROM @add_sale_payment_index;
EXECUTE add_sale_payment_index;
DEALLOCATE PREPARE add_sale_payment_index;

SET @drop_sale_payment_unique = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE sale_payments DROP INDEX sale_id', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sale_payments'
      AND index_name = 'sale_id' AND non_unique = 0
);
PREPARE drop_sale_payment_unique FROM @drop_sale_payment_unique;
EXECUTE drop_sale_payment_unique;
DEALLOCATE PREPARE drop_sale_payment_unique;
