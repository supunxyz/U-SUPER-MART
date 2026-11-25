package com.mycompany.easymartpos.repository;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.Product;
import com.mycompany.easymartpos.model.ProductBatchInventory;
import com.mycompany.easymartpos.model.ProductInventory;
import com.mycompany.easymartpos.model.ProductInput;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductRepository {
    public static final List<String> DEFAULT_CATEGORIES = List.of("Snacks", "Stationery", "Beverage", "Medicine",
            "Personal Care", "Plastic Items", "Grocery", "Diary");

    /** Idempotently adds the standard shop categories; category_name is UNIQUE so re-runs are harmless. */
    public void ensureDefaultCategories() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO categories (category_name) VALUES (?)")) {
            for (String name : DEFAULT_CATEGORIES) { statement.setString(1, name); statement.addBatch(); }
            statement.executeBatch();
        }
    }

    /** Adds one category, rejecting blank and case-insensitive duplicate names, and returns its ID. */
    public int addCategory(String name) throws SQLException {
        String categoryName = name == null ? "" : name.trim();
        if (categoryName.isEmpty()) throw new IllegalArgumentException("Category name is required.");
        if (categoryName.length() > 100) throw new IllegalArgumentException("Category name must be 100 characters or fewer.");
        try (Connection connection = DBConnection.getConnection()) {
            try (PreparedStatement existing = connection.prepareStatement("SELECT category_id FROM categories WHERE LOWER(TRIM(category_name))=LOWER(?)")) {
                existing.setString(1, categoryName);
                try (ResultSet rows = existing.executeQuery()) {
                    if (rows.next()) throw new IllegalArgumentException("That category already exists.");
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO categories (category_name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, categoryName);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Category ID was not generated.");
                    return keys.getInt(1);
                }
            }
        }
    }

    public List<Product> search(String text) throws SQLException {
        String term = "%" + (text == null ? "" : text.trim()) + "%";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(productSql(SchemaSupport.hasColumn(connection, "products", "retail_price"), "WHERE p.barcode LIKE ? OR p.product_name LIKE ? ORDER BY p.active DESC, p.product_name"))) {
            statement.setString(1, term);
            statement.setString(2, term);
            try (ResultSet results = statement.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (results.next()) products.add(map(results));
                return products;
            }
        }
    }

    /**
     * Returns one product-master row per product, with physical stock calculated
     * from batches rather than from the compatibility stock_quantity column.
     */
    public List<ProductInventory> searchWithBatchInventory(String text) throws SQLException {
        String term = "%" + (text == null ? "" : text.trim()) + "%";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(productInventorySql(
                     SchemaSupport.hasColumn(connection, "products", "retail_price")))) {
            statement.setString(1, term);
            statement.setString(2, term);
            try (ResultSet results = statement.executeQuery()) {
                List<ProductInventory> products = new ArrayList<>();
                while (results.next()) {
                    Date nearestExpiry = results.getDate("nearest_expiry");
                    products.add(new ProductInventory(map(results), results.getBigDecimal("batch_total_stock"),
                            nearestExpiry == null ? null : nearestExpiry.toLocalDate(), results.getBigDecimal("expired_stock")));
                }
                return products;
            }
        }
    }

    /** Loads all batches for the selected product without allowing any mutation. */
    public List<ProductBatchInventory> findBatchInventory(int productId) throws SQLException {
        String sql = "SELECT batch_id, source_type, purchase_id, cost_price, selling_price, retail_price, expiry_date, "
                + "quantity_received, quantity_available, active, created_at "
                + "FROM product_batches WHERE product_id=? "
                + "ORDER BY CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END, expiry_date, created_at, batch_id";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                List<ProductBatchInventory> batches = new ArrayList<>();
                while (results.next()) {
                    Date expiry = results.getDate("expiry_date");
                    java.sql.Timestamp created = results.getTimestamp("created_at");
                    Object purchaseId = results.getObject("purchase_id");
                    batches.add(new ProductBatchInventory(results.getInt("batch_id"), results.getString("source_type"),
                            purchaseId instanceof Number number ? number.intValue() : null,
                            results.getBigDecimal("cost_price"), results.getBigDecimal("selling_price"),
                            results.getBigDecimal("retail_price"), expiry == null ? null : expiry.toLocalDate(),
                            results.getBigDecimal("quantity_received"), results.getBigDecimal("quantity_available"),
                            results.getBoolean("active"), created == null ? null : created.toLocalDateTime()));
                }
                return batches;
            }
        }
    }

    public Optional<Product> findActiveByBarcode(String barcode) throws SQLException {
        return findOne("WHERE p.barcode=? AND p.active=TRUE", barcode);
    }

    public Optional<Product> findActiveById(int id) throws SQLException {
        return findOne("WHERE p.product_id=? AND p.active=TRUE", id);
    }

    public int save(ProductInput product, int userId) throws SQLException {
        validate(product);
        try (Connection connection = DBConnection.getConnection()) {
            if (product.retailPrice() != null && !SchemaSupport.hasColumn(connection, "products", "retail_price")) {
                throw new IllegalStateException("Retail / MRP price requires the documented retail_price schema upgrade before it can be saved.");
            }
            connection.setAutoCommit(false);
            try {
                int id = product.id() == null ? insert(connection, product) : update(connection, product);
                if (product.id() == null && product.openingStock().signum() > 0) {
                    addOpeningBatch(connection, id, product);
                    addMovement(connection, id, "OPENING", product.openingStock(), "Initial stock", userId);
                }
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void archive(int productId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE products SET active=FALSE WHERE product_id=?")) {
            statement.setInt(1, productId);
            if (statement.executeUpdate() == 0) throw new IllegalArgumentException("Product not found.");
        }
    }

    /**
     * Permanently removes a product with its batches, movements and promotions, but only when no sale,
     * purchase or batch allocation refers to it. Returns false when history exists, so the caller can archive instead.
     */
    public boolean deleteIfUnused(int productId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockProduct(connection, productId);
                boolean hasHistory = count(connection, "SELECT COUNT(*) FROM sale_items WHERE product_id=?", productId) > 0
                        || count(connection, "SELECT COUNT(*) FROM purchase_items WHERE product_id=?", productId) > 0
                        || count(connection, "SELECT COUNT(*) FROM product_batches WHERE product_id=? AND purchase_id IS NOT NULL", productId) > 0
                        || count(connection, "SELECT COUNT(*) FROM sale_item_batch_allocations a JOIN product_batches pb ON pb.batch_id=a.batch_id WHERE pb.product_id=?", productId) > 0;
                if (hasHistory) {
                    connection.rollback();
                    return false;
                }
                execute(connection, "DELETE FROM promotions WHERE product_id=?", productId);
                execute(connection, "DELETE FROM stock_movements WHERE product_id=?", productId);
                execute(connection, "DELETE FROM product_batches WHERE product_id=?", productId);
                execute(connection, "DELETE FROM products WHERE product_id=?", productId);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Physical units in batches whose expiry date has passed; these are excluded from checkout. */
    public BigDecimal expiredStockQuantity(int productId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(quantity_available), 0) FROM product_batches "
                     + "WHERE product_id=? AND active=TRUE AND quantity_available>0 AND expiry_date<CURRENT_DATE")) {
            statement.setInt(1, productId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getBigDecimal(1); }
        }
    }

    private static void lockProduct(Connection connection, int productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT product_id FROM products WHERE product_id=? FOR UPDATE")) {
            statement.setInt(1, productId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("Product not found.");
            }
        }
    }

    private static long count(Connection connection, String sql, int productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet row = statement.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }

    private static void execute(Connection connection, String sql, int productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            statement.executeUpdate();
        }
    }

    private int insert(Connection connection, ProductInput product) throws SQLException {
        boolean hasRetailPrice = SchemaSupport.hasColumn(connection, "products", "retail_price");
        String sql = hasRetailPrice
                ? "INSERT INTO products (barcode, product_name, category_id, cost_price, selling_price, retail_price, stock_quantity, minimum_stock, expiry_date, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)"
                : "INSERT INTO products (barcode, product_name, category_id, cost_price, selling_price, stock_quantity, minimum_stock, expiry_date, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, product, false, hasRetailPrice); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Product ID was not generated.");
                return keys.getInt(1);
            }
        }
    }

    private int update(Connection connection, ProductInput product) throws SQLException {
        boolean hasRetailPrice = SchemaSupport.hasColumn(connection, "products", "retail_price");
        ProductDefaults previous = currentDefaults(connection, product.id(), hasRetailPrice);
        String sql = hasRetailPrice
                ? "UPDATE products SET barcode=?, product_name=?, category_id=?, cost_price=?, selling_price=?, retail_price=?, minimum_stock=?, expiry_date=? WHERE product_id=?"
                : "UPDATE products SET barcode=?, product_name=?, category_id=?, cost_price=?, selling_price=?, minimum_stock=?, expiry_date=? WHERE product_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, product, true, hasRetailPrice); statement.setInt(hasRetailPrice ? 9 : 8, product.id());
            if (statement.executeUpdate() == 0) throw new IllegalArgumentException("Product not found.");
        }
        followPreviousDefaults(connection, product, previous, hasRetailPrice);
        return product.id();
    }

    /** The product-master defaults that batches are created from. */
    private record ProductDefaults(BigDecimal sellingPrice, BigDecimal retailPrice, LocalDate expiryDate) { }

    private static ProductDefaults currentDefaults(Connection connection, int productId, boolean hasRetailPrice) throws SQLException {
        String sql = "SELECT selling_price, " + (hasRetailPrice ? "retail_price" : "NULL") + " retail_price, expiry_date "
                + "FROM products WHERE product_id=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("Product not found.");
                Date expiry = row.getDate("expiry_date");
                return new ProductDefaults(row.getBigDecimal("selling_price"), row.getBigDecimal("retail_price"),
                        expiry == null ? null : expiry.toLocalDate());
            }
        }
    }

    /**
     * Checkout sells from batches, so a changed default must reach the stock that was following the old default.
     * Only unsold, active batches still carrying the previous value are moved. Batches priced or dated on purpose
     * differently, and batches already sold or expired, keep their own values and history.
     */
    private static void followPreviousDefaults(Connection connection, ProductInput product, ProductDefaults previous, boolean hasRetailPrice) throws SQLException {
        if (previous.sellingPrice() != null && product.sellingPrice().compareTo(previous.sellingPrice()) != 0) {
            moveBatchDefault(connection, product.id(), "selling_price=?", "selling_price=?",
                    previous.sellingPrice(), product.sellingPrice());
        }
        if (hasRetailPrice && !sameAmount(previous.retailPrice(), product.retailPrice())) {
            moveBatchDefault(connection, product.id(), "retail_price <=> ?", "retail_price=?",
                    previous.retailPrice(), product.retailPrice());
        }
        if (!java.util.Objects.equals(previous.expiryDate(), product.expiryDate())) {
            moveBatchDefault(connection, product.id(), "expiry_date <=> ?", "expiry_date=?",
                    previous.expiryDate() == null ? null : Date.valueOf(previous.expiryDate()),
                    product.expiryDate() == null ? null : Date.valueOf(product.expiryDate()));
        }
    }

    private static void moveBatchDefault(Connection connection, int productId, String matchClause, String setClause,
            Object oldValue, Object newValue) throws SQLException {
        String sql = "UPDATE product_batches SET " + setClause + " WHERE product_id=? AND active=TRUE AND quantity_available>0 AND " + matchClause;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullable(statement, 1, newValue);
            statement.setInt(2, productId);
            setNullable(statement, 3, oldValue);
            statement.executeUpdate();
        }
    }

    private static void setNullable(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.DECIMAL);
        else if (value instanceof Date date) statement.setDate(index, date);
        else statement.setBigDecimal(index, (BigDecimal) value);
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private void bind(PreparedStatement statement, ProductInput product, boolean updating, boolean hasRetailPrice) throws SQLException {
        statement.setString(1, product.barcode().trim()); statement.setString(2, product.name().trim());
        statement.setInt(3, product.categoryId()); statement.setBigDecimal(4, product.costPrice());
        statement.setBigDecimal(5, product.sellingPrice());
        int index = 6;
        if (hasRetailPrice) {
            if (product.retailPrice() == null) statement.setNull(index, java.sql.Types.DECIMAL);
            else statement.setBigDecimal(index, product.retailPrice());
            index++;
        }
        if (!updating) { statement.setBigDecimal(index++, product.openingStock()); statement.setBigDecimal(index++, product.minimumStock()); }
        else statement.setBigDecimal(index++, product.minimumStock());
        int expiryIndex = index;
        if (product.expiryDate() == null) statement.setNull(expiryIndex, java.sql.Types.DATE);
        else statement.setDate(expiryIndex, Date.valueOf(product.expiryDate()));
    }

    private void addMovement(Connection connection, int productId, String type, BigDecimal quantity, String reason, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_id, user_id, reason) VALUES (?, ?, ?, NULL, ?, ?)")) {
            statement.setInt(1, productId); statement.setString(2, type); statement.setBigDecimal(3, quantity);
            statement.setInt(4, userId); statement.setString(5, reason); statement.executeUpdate();
        }
    }

    /** Opening quantity must be a sellable batch; otherwise batch-based checkout would show zero stock. */
    private void addOpeningBatch(Connection connection, int productId, ProductInput product) throws SQLException {
        String sql = "INSERT INTO product_batches (product_id, purchase_id, purchase_item_id, source_type, cost_price, selling_price, retail_price, expiry_date, quantity_received, quantity_available, active) "
                + "VALUES (?, NULL, NULL, 'OPENING', ?, ?, ?, ?, ?, ?, TRUE)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId); statement.setBigDecimal(2, product.costPrice()); statement.setBigDecimal(3, product.sellingPrice());
            if (product.retailPrice() == null) statement.setNull(4, java.sql.Types.DECIMAL); else statement.setBigDecimal(4, product.retailPrice());
            if (product.expiryDate() == null) statement.setNull(5, java.sql.Types.DATE); else statement.setDate(5, Date.valueOf(product.expiryDate()));
            statement.setBigDecimal(6, product.openingStock()); statement.setBigDecimal(7, product.openingStock());
            if (statement.executeUpdate() != 1) throw new SQLException("Opening stock batch could not be created.");
        }
    }

    private Optional<Product> findOne(String whereClause, Object value) throws SQLException {
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(productSql(SchemaSupport.hasColumn(connection, "products", "retail_price"), whereClause))) {
            if (value instanceof Integer id) statement.setInt(1, id); else statement.setString(1, value.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(map(result)) : Optional.empty(); }
        }
    }

    private Product map(ResultSet result) throws SQLException {
        Date expiry = result.getDate("expiry_date");
        return new Product(result.getInt("product_id"), result.getString("barcode"), result.getString("product_name"),
                result.getInt("category_id"), result.getString("category_name"), result.getBigDecimal("cost_price"),
                result.getBigDecimal("selling_price"), result.getBigDecimal("retail_price"), result.getBigDecimal("stock_quantity"),
                result.getBigDecimal("minimum_stock"), expiry == null ? null : expiry.toLocalDate(), result.getBoolean("active"));
    }

    private void validate(ProductInput product) {
        if (product.barcode() == null || product.barcode().isBlank()) throw new IllegalArgumentException("Barcode is required.");
        if (product.name() == null || product.name().isBlank()) throw new IllegalArgumentException("Product name is required.");
        if (product.categoryId() <= 0) throw new IllegalArgumentException("Select a category.");
        if (product.costPrice() == null || product.sellingPrice() == null || product.minimumStock() == null || product.openingStock() == null)
            throw new IllegalArgumentException("All prices and stock quantities are required.");
        if (product.costPrice().signum() < 0 || product.sellingPrice().signum() < 0 || (product.retailPrice() != null && product.retailPrice().signum() < 0) || product.minimumStock().signum() < 0 || product.openingStock().signum() < 0)
            throw new IllegalArgumentException("Prices and quantities cannot be negative.");
    }

    private String productSql(boolean hasRetailPrice, String whereClause) {
        return "SELECT p.product_id, p.barcode, p.product_name, p.category_id, "
                + "COALESCE(c.category_name, 'Uncategorized') category_name, p.cost_price, p.selling_price, "
                + (hasRetailPrice ? "p.retail_price" : "NULL") + " retail_price, p.stock_quantity, p.minimum_stock, p.expiry_date, p.active "
                + "FROM products p LEFT JOIN categories c ON c.category_id=p.category_id " + whereClause;
    }

    private String productInventorySql(boolean hasRetailPrice) {
        String retailPrice = hasRetailPrice ? "p.retail_price" : "NULL";
        return "SELECT p.product_id, p.barcode, p.product_name, p.category_id, "
                + "COALESCE(c.category_name, 'Uncategorized') category_name, p.cost_price, p.selling_price, "
                + retailPrice + " retail_price, COALESCE(SUM(pb.quantity_available), 0) stock_quantity, "
                + "COALESCE(SUM(pb.quantity_available), 0) batch_total_stock, p.minimum_stock, p.expiry_date, p.active, "
                + "MIN(CASE WHEN pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date>=CURRENT_DATE "
                + "THEN pb.expiry_date ELSE NULL END) nearest_expiry, "
                + "COALESCE(SUM(CASE WHEN pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date<CURRENT_DATE "
                + "THEN pb.quantity_available ELSE 0 END), 0) expired_stock "
                + "FROM products p LEFT JOIN categories c ON c.category_id=p.category_id "
                + "LEFT JOIN product_batches pb ON pb.product_id=p.product_id "
                + "WHERE p.barcode LIKE ? OR p.product_name LIKE ? "
                + "GROUP BY p.product_id, p.barcode, p.product_name, p.category_id, c.category_name, p.cost_price, "
                + "p.selling_price, " + retailPrice + ", p.minimum_stock, p.expiry_date, p.active "
                + "ORDER BY p.active DESC, p.product_name";
    }
}
