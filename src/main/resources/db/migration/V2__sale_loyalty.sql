-- Per-sale loyalty audit. Apply after V1__core_pos.sql to enable point earning, redemption and void reversal.
CREATE TABLE IF NOT EXISTS sale_loyalty (
    sale_id INT NOT NULL PRIMARY KEY,
    customer_id INT NOT NULL,
    customer_points_before INT NOT NULL,
    earned_points INT NOT NULL DEFAULT 0,
    redeemed_points INT NOT NULL DEFAULT 0,
    redemption_value DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    customer_points_after INT NOT NULL,
    CONSTRAINT fk_sale_loyalty_sale FOREIGN KEY (sale_id) REFERENCES sales(sale_id),
    CONSTRAINT fk_sale_loyalty_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
