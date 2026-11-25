package com.mycompany.easymartpos.repository;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.CustomerChoice;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CustomerRepository {
    /** The checkout schema requires a customer ID even for an anonymous sale. */
    public void ensureWalkInCustomer() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO customers (customer_name) "
                     + "SELECT 'Walk-in Customer' WHERE NOT EXISTS ("
                     + "SELECT 1 FROM customers WHERE LOWER(TRIM(customer_name))='walk-in customer')")) {
            statement.executeUpdate();
        }
    }

    public List<CustomerChoice> list() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            boolean phone = SchemaSupport.hasColumn(connection, "customers", "phone");
            boolean points = SchemaSupport.hasColumn(connection, "customers", "loyalty_points");
            boolean loyaltyCustomer = SchemaSupport.hasColumn(connection, "customers", "loyalty_customer");
            boolean cards = SchemaSupport.hasTable(connection, "loyalty_cards")
                    && SchemaSupport.hasColumn(connection, "loyalty_cards", "loyalty_card_id")
                    && SchemaSupport.hasColumn(connection, "loyalty_cards", "customer_id")
                    && SchemaSupport.hasColumn(connection, "loyalty_cards", "card_code")
                    && SchemaSupport.hasColumn(connection, "loyalty_cards", "status");

            String cardJoin = cards
                    ? " LEFT JOIN loyalty_cards lc ON lc.loyalty_card_id=(SELECT current_card.loyalty_card_id FROM loyalty_cards current_card "
                    + "WHERE current_card.customer_id=c.customer_id AND current_card.status IN ('ASSIGNED','BLOCKED') "
                    + "ORDER BY CASE current_card.status WHEN 'ASSIGNED' THEN 0 ELSE 1 END, current_card.loyalty_card_id DESC LIMIT 1)"
                    : "";
            // Archived customers keep their sales history but must not be selectable for new sales.
            String activeOnly = SchemaSupport.hasColumn(connection, "customers", "active") ? " WHERE c.active=TRUE" : "";
            String sql = "SELECT c.customer_id, c.customer_name, "
                    + (phone ? "c.phone" : "NULL") + " phone, "
                    + (points ? "c.loyalty_points" : "0") + " loyalty_points, "
                    + (loyaltyCustomer ? "c.loyalty_customer" : "FALSE") + " loyalty_customer, "
                    + (cards ? "lc.card_code" : "NULL") + " card_code, "
                    + (cards ? "lc.status" : "NULL") + " status FROM customers c"
                    + cardJoin + activeOnly + " ORDER BY c.customer_name";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet results = statement.executeQuery()) {
            List<CustomerChoice> customers = new ArrayList<>();
            while (results.next()) customers.add(new CustomerChoice(results.getInt("customer_id"), results.getString("customer_name"),
                    results.getString("phone"), results.getString("card_code"), results.getString("status"),
                    results.getInt("loyalty_points"), results.getBoolean("loyalty_customer")));
            return customers;
            }
        }
    }
}
