-- EasyMart POS core schema. Run this file against the easymart_pos MySQL database.
CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(150) NULL,
    address VARCHAR(255) NULL
);

CREATE TABLE IF NOT EXISTS products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    barcode VARCHAR(100) NOT NULL UNIQUE,
    product_name VARCHAR(200) NOT NULL,
    category_id INT NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    selling_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    stock_quantity DECIMAL(12,3) NOT NULL DEFAULT 0,
    minimum_stock DECIMAL(12,3) NOT NULL DEFAULT 0,
    expiry_date DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

CREATE TABLE IF NOT EXISTS sales (
    sale_id INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number VARCHAR(60) NOT NULL UNIQUE,
    customer_id INT NOT NULL,
    user_id INT NULL,
    sale_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(10) NOT NULL,
    sale_status VARCHAR(12) NOT NULL DEFAULT 'COMPLETED',
    void_reason VARCHAR(255) NULL,
    voided_by INT NULL,
    voided_at DATETIME NULL,
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE IF NOT EXISTS sale_items (
    sale_item_id INT AUTO_INCREMENT PRIMARY KEY,
    sale_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    regular_unit_price DECIMAL(12,2) NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(sale_id),
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
    movement_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    reference_id INT NULL,
    user_id INT NULL,
    reason VARCHAR(255) NULL,
    movement_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Additive upgrades for databases created by the original prototype.
ALTER TABLE products ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS sale_status VARCHAR(12) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE sales ADD COLUMN IF NOT EXISTS void_reason VARCHAR(255) NULL;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS voided_by INT NULL;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS voided_at DATETIME NULL;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS reason VARCHAR(255) NULL;
-- Receipt reprints must retain the normal "Our Price" at the time of sale; unit_price is the promoted/charged price.
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS regular_unit_price DECIMAL(12,2) NULL;

CREATE TABLE IF NOT EXISTS sale_payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    sale_id INT NOT NULL UNIQUE,
    payment_method VARCHAR(10) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    tendered_amount DECIMAL(12,2) NULL,
    change_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    card_reference VARCHAR(100) NULL,
    payment_status VARCHAR(12) NOT NULL DEFAULT 'COMPLETED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sale_payments_sale FOREIGN KEY (sale_id) REFERENCES sales(sale_id)
);

INSERT INTO categories (category_name)
SELECT 'General' WHERE NOT EXISTS (SELECT 1 FROM categories);
INSERT INTO customers (customer_name)
SELECT 'Walk-in Customer' WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_name = 'Walk-in Customer');
