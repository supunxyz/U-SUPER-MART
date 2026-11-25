package com.mycompany.easymartpos.repository;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.BatchStock;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only access to inventory batches available for a future sale. */
public class BatchRepository {

    private static final String SELLABLE_BATCHES_SQL = "SELECT batch_id, product_id, cost_price, selling_price, "
            + "retail_price, expiry_date, quantity_available FROM product_batches "
            + "WHERE product_id = ? AND active = 1 AND quantity_available > 0 "
            + "AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE) "
            + "ORDER BY CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END, expiry_date ASC, created_at ASC, batch_id ASC";

    public List<BatchStock> findSellableBatches(int productId) throws SQLException {
        if (productId <= 0) throw new IllegalArgumentException("Product ID must be positive.");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELLABLE_BATCHES_SQL)) {
            statement.setInt(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                List<BatchStock> batches = new ArrayList<>();
                while (results.next()) batches.add(map(results));
                return List.copyOf(batches);
            }
        }
    }

    private BatchStock map(ResultSet result) throws SQLException {
        Date expiry = result.getDate("expiry_date");
        return new BatchStock(result.getInt("batch_id"), result.getInt("product_id"),
                result.getBigDecimal("cost_price"), result.getBigDecimal("selling_price"),
                result.getBigDecimal("retail_price"), expiry == null ? null : expiry.toLocalDate(),
                result.getBigDecimal("quantity_available"));
    }
}
