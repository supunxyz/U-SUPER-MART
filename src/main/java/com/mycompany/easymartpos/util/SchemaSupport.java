package com.mycompany.easymartpos.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Small compatibility helper for additive, manually-applied schema upgrades. */
public final class SchemaSupport {
    private SchemaSupport() { }

    public static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet columns = meta.getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) return true;
        }
        try (ResultSet columns = meta.getColumns(connection.getCatalog(), null, table.toUpperCase(), column.toUpperCase())) {
            return columns.next();
        }
    }

    public static boolean hasTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet tables = meta.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            if (tables.next()) return true;
        }
        try (ResultSet tables = meta.getTables(connection.getCatalog(), null, table.toUpperCase(), new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    /** True when a unique primary/unique index is defined only on the named column. */
    public static boolean hasUniqueIndexOnColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet indexes = meta.getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (indexes.next()) {
                String indexed = indexes.getString("COLUMN_NAME");
                if (column.equalsIgnoreCase(indexed) && indexes.getShort("ORDINAL_POSITION") == 1) {
                    // A second index row with the same name would make this composite, which is safe.
                    String name = indexes.getString("INDEX_NAME");
                    boolean composite = false;
                    try (ResultSet members = meta.getIndexInfo(connection.getCatalog(), null, table, true, false)) {
                        while (members.next()) if (name != null && name.equals(members.getString("INDEX_NAME")) && members.getShort("ORDINAL_POSITION") > 1) { composite = true; break; }
                    }
                    if (!composite) return true;
                }
            }
        }
        return false;
    }
}
