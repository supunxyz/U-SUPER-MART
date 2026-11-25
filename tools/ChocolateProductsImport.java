import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Transactional, idempotent import of the reviewed Chocolate receipt workbook. */
public final class ChocolateProductsImport {
    private record Item(int sheetRow, String name, String barcode, BigDecimal retail,
            BigDecimal stock, BigDecimal cost, BigDecimal selling, boolean retailUsedForSelling) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("preview") || args[0].equals("apply"))) {
            throw new IllegalArgumentException("Usage: ChocolateProductsImport preview|apply <chocolate_receipt_data.xlsx>");
        }
        List<Item> items = read(Path.of(args[1]));
        try (Connection connection = DBConnection.getConnection()) {
            if (!SchemaSupport.hasColumn(connection, "products", "retail_price")
                    || !SchemaSupport.hasTable(connection, "product_batches")
                    || !SchemaSupport.hasTable(connection, "stock_movements")) {
                throw new IllegalStateException("The product, batch, and stock movement schema is required.");
            }
            int chocolateCategory = chocolateCategory(connection);
            connection.setAutoCommit(false);
            try {
                int added = 0;
                int existing = 0;
                for (Item item : items) {
                    Integer found = existingId(connection, item.barcode());
                    if (found != null) {
                        existing++;
                        System.out.println("SKIP existing product #" + found + " | Excel row " + item.sheetRow()
                                + " | " + item.barcode() + " | " + item.name());
                        continue;
                    }
                    if (args[0].equals("preview")) {
                        System.out.println("WOULD ADD | Excel row " + item.sheetRow() + " | " + item.barcode()
                                + " | " + item.name() + " | sell " + item.selling()
                                + (item.retailUsedForSelling() ? " (retail fallback)" : "")
                                + " | cost " + item.cost() + " | MRP " + item.retail()
                                + " | stock " + item.stock());
                    } else {
                        int id = insertProduct(connection, item, chocolateCategory);
                        insertOpeningBatch(connection, id, item);
                        insertOpeningMovement(connection, id, item);
                        verify(connection, id, item);
                        System.out.println("ADDED product #" + id + " | Excel row " + item.sheetRow()
                                + " | " + item.barcode() + " | " + item.name());
                    }
                    added++;
                }
                if (args[0].equals("apply")) connection.commit();
                else connection.rollback();
                System.out.println("SUMMARY " + args[0] + ": " + added + " new, " + existing
                        + " existing skipped, Chocolate category.");
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static List<Item> read(Path file) throws Exception {
        List<Item> items = new ArrayList<>();
        Set<String> barcodes = new HashSet<>();
        try (var workbook = WorkbookFactory.create(new File(file.toString()))) {
            Sheet sheet = workbook.getSheet("Receipt Data");
            if (sheet == null) throw new IllegalArgumentException("The Receipt Data sheet is missing.");
            DataFormatter formatter = new DataFormatter();
            String[] headers = {"Name", "Retail / Price", "Opening Stock", "Cost Price", "Selling Pricing", "Barcode"};
            Row header = sheet.getRow(0);
            for (int column = 0; column < headers.length; column++) {
                if (header == null || !headers[column].equals(formatter.formatCellValue(header.getCell(column)))) {
                    throw new IllegalArgumentException("Unexpected header in column " + (column + 1));
                }
            }
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) throw new IllegalArgumentException("Empty row " + (index + 1));
                String name = text(row.getCell(0), formatter, "Name", index + 1);
                BigDecimal retail = decimal(row.getCell(1), 2, "Retail / Price", index + 1);
                BigDecimal stock = decimal(row.getCell(2), 3, "Opening Stock", index + 1);
                BigDecimal cost = decimal(row.getCell(3), 2, "Cost Price", index + 1);
                Cell sellingCell = row.getCell(4);
                boolean fallback = sellingCell == null || sellingCell.getCellType() == CellType.BLANK;
                BigDecimal selling = fallback ? retail : decimal(sellingCell, 2, "Selling Pricing", index + 1);
                String barcode = text(row.getCell(5), formatter, "Barcode", index + 1);
                if (!barcode.matches("[0-9]{13}") || !validEan13(barcode) || !barcodes.add(barcode)) {
                    throw new IllegalArgumentException("Invalid or repeated EAN-13 barcode in row " + (index + 1));
                }
                if (name.length() > 200 || retail.signum() <= 0 || stock.signum() <= 0
                        || cost.signum() < 0 || selling.signum() <= 0 || selling.compareTo(cost) < 0
                        || selling.compareTo(retail) > 0) {
                    throw new IllegalArgumentException("Invalid name, price, or stock in row " + (index + 1));
                }
                items.add(new Item(index + 1, name, barcode, retail, stock, cost, selling, fallback));
            }
            if (items.size() != 39) throw new IllegalArgumentException("Expected 39 Chocolate product rows.");
        }
        return List.copyOf(items);
    }

    private static boolean validEan13(String barcode) {
        int sum = 0;
        for (int index = 0; index < 12; index++) {
            sum += (barcode.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10 == barcode.charAt(12) - '0';
    }

    private static String text(Cell cell, DataFormatter formatter, String field, int row) {
        if (cell == null || cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException("Missing or formula " + field + " in row " + row);
        }
        String value = formatter.formatCellValue(cell).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing " + field + " in row " + row);
        return value;
    }

    private static BigDecimal decimal(Cell cell, int scale, String field, int row) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            throw new IllegalArgumentException("Expected a number for " + field + " in row " + row);
        }
        try { return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("Too many decimal places in " + field + " row " + row); }
    }

    private static int chocolateCategory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT category_id FROM categories WHERE category_name='Chocolate'")) {
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Chocolate category is missing.");
                int id = rows.getInt(1);
                if (rows.next()) throw new IllegalStateException("Chocolate category is ambiguous.");
                return id;
            }
        }
    }

    private static Integer existingId(Connection connection, String barcode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT product_id FROM products WHERE barcode=?")) {
            statement.setString(1, barcode);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getInt(1) : null; }
        }
    }

    private static int insertProduct(Connection connection, Item item, int categoryId) throws SQLException {
        String sql = "INSERT INTO products (barcode, product_name, category_id, cost_price, selling_price, "
                + "retail_price, stock_quantity, minimum_stock, expiry_date, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL, TRUE)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, item.barcode());
            statement.setString(2, item.name());
            statement.setInt(3, categoryId);
            statement.setBigDecimal(4, item.cost());
            statement.setBigDecimal(5, item.selling());
            statement.setBigDecimal(6, item.retail());
            statement.setBigDecimal(7, item.stock());
            if (statement.executeUpdate() != 1) throw new SQLException("Product insert failed: " + item.barcode());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated product ID: " + item.barcode());
                return keys.getInt(1);
            }
        }
    }

    private static void insertOpeningBatch(Connection connection, int id, Item item) throws SQLException {
        String sql = "INSERT INTO product_batches (product_id, purchase_id, purchase_item_id, source_type, "
                + "cost_price, selling_price, retail_price, expiry_date, quantity_received, quantity_available, active) "
                + "VALUES (?, NULL, NULL, 'OPENING', ?, ?, ?, NULL, ?, ?, TRUE)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setBigDecimal(2, item.cost());
            statement.setBigDecimal(3, item.selling());
            statement.setBigDecimal(4, item.retail());
            statement.setBigDecimal(5, item.stock());
            statement.setBigDecimal(6, item.stock());
            if (statement.executeUpdate() != 1) throw new SQLException("Opening batch insert failed: " + id);
        }
    }

    private static void insertOpeningMovement(Connection connection, int id, Item item) throws SQLException {
        String sql = "INSERT INTO stock_movements (product_id, movement_type, quantity, reference_id, user_id, reason) "
                + "VALUES (?, 'OPENING', ?, NULL, NULL, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setBigDecimal(2, item.stock());
            statement.setString(3, "Excel import chocolate_receipt_data.xlsx row " + item.sheetRow());
            if (statement.executeUpdate() != 1) throw new SQLException("Stock movement insert failed: " + id);
        }
    }

    private static void verify(Connection connection, int id, Item item) throws SQLException {
        String sql = "SELECT p.barcode, p.stock_quantity, p.selling_price, p.cost_price, p.retail_price, "
                + "b.quantity_available, b.selling_price, b.cost_price, b.retail_price, m.quantity "
                + "FROM products p JOIN product_batches b ON b.product_id=p.product_id AND b.source_type='OPENING' "
                + "JOIN stock_movements m ON m.product_id=p.product_id AND m.movement_type='OPENING' WHERE p.product_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !item.barcode().equals(row.getString(1))
                        || item.stock().compareTo(row.getBigDecimal(2)) != 0
                        || item.selling().compareTo(row.getBigDecimal(3)) != 0
                        || item.cost().compareTo(row.getBigDecimal(4)) != 0
                        || item.retail().compareTo(row.getBigDecimal(5)) != 0
                        || item.stock().compareTo(row.getBigDecimal(6)) != 0
                        || item.selling().compareTo(row.getBigDecimal(7)) != 0
                        || item.cost().compareTo(row.getBigDecimal(8)) != 0
                        || item.retail().compareTo(row.getBigDecimal(9)) != 0
                        || item.stock().compareTo(row.getBigDecimal(10)) != 0 || row.next()) {
                    throw new SQLException("Product, batch, or stock movement verification failed: " + id);
                }
            }
        }
    }
}
