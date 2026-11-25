package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Customer-management-only loyalty card registration and assignment operations. */
public final class LoyaltyCardService {
    @FunctionalInterface interface ConnectionProvider { Connection get() throws SQLException; }

    private final ConnectionProvider connections;
    public LoyaltyCardService() { this(DBConnection::getConnection); }
    LoyaltyCardService(ConnectionProvider connections) { this.connections = connections; }

    /** Searches existing customers by name, normalized phone, or either card-code scan format. */
    public List<CustomerCardRow> listCustomers(String search) throws SQLException {
        String value = search == null ? "" : search.trim();
        String phone = normalizePhone(value);
        String card = normalizeCardCode(value);
        List<CustomerCardRow> customers = new ArrayList<>();
        try (Connection connection = connections.get()) {
            // Archived customers are hidden from search; their sales history remains in reports.
            String activeOnly = SchemaSupport.hasColumn(connection, "customers", "active") ? "c.active=TRUE AND " : "";
            String sql = "SELECT c.customer_id, c.customer_name, c.phone, c.loyalty_points, lc.card_code, lc.status card_status, lc.assigned_at "
                    + "FROM customers c LEFT JOIN loyalty_cards lc ON lc.loyalty_card_id=(SELECT current_card.loyalty_card_id FROM loyalty_cards current_card "
                    + "WHERE current_card.customer_id=c.customer_id AND current_card.status IN ('ASSIGNED','BLOCKED') "
                    + "ORDER BY CASE current_card.status WHEN 'ASSIGNED' THEN 0 ELSE 1 END, COALESCE(current_card.blocked_at, current_card.assigned_at) DESC, current_card.loyalty_card_id DESC LIMIT 1) "
                    + "WHERE " + activeOnly + "(?='' OR c.customer_name LIKE ? ESCAPE '\\\\' "
                    + "OR REPLACE(REPLACE(TRIM(COALESCE(c.phone, '')), ' ', ''), '-', '')=? "
                    + "OR EXISTS (SELECT 1 FROM loyalty_cards search_card WHERE search_card.customer_id=c.customer_id "
                    + "AND REPLACE(TRIM(search_card.card_code), ' ', '')=?)) ORDER BY c.customer_name, c.customer_id";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value); statement.setString(2, "%" + escapeLike(value) + "%"); statement.setString(3, phone); statement.setString(4, card);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String cardCode = rows.getString("card_code");
                        customers.add(new CustomerCardRow(rows.getInt("customer_id"), rows.getString("customer_name"), rows.getString("phone"),
                                cardCode == null ? "NO CARD" : cardCode, cardCode == null ? "UNASSIGNED" : rows.getString("card_status"),
                                rows.getInt("loyalty_points"), rows.getTimestamp("assigned_at")));
                    }
                }
            }
        }
        return customers;
    }

    /** Finds existing customers for callers that need a compact selector. */
    public List<CustomerMatch> findCustomers(String search) throws SQLException {
        return listCustomers(search).stream().map(row -> new CustomerMatch(row.customerId(), row.customerName(), row.phone())).toList();
    }

    /** Looks up a card without altering its stored spacing or status. */
    public Optional<CardLookup> lookupCard(String enteredCardCode) throws SQLException {
        String normalized = normalizeCardCode(enteredCardCode);
        if (normalized.isEmpty()) return Optional.empty();
        String sql = "SELECT lc.loyalty_card_id, lc.card_code, lc.status, lc.customer_id, c.customer_name FROM loyalty_cards lc "
                + "LEFT JOIN customers c ON c.customer_id=lc.customer_id WHERE REPLACE(TRIM(lc.card_code), ' ', '')=?";
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(cardLookup(rows)) : Optional.empty(); }
        }
    }

    /** Returns an assigned or blocked card linked to this customer, if present. */
    public Optional<CardAssignment> currentCard(int customerId) throws SQLException {
        String sql = "SELECT card_code, status, assigned_at FROM loyalty_cards WHERE customer_id=? AND status IN ('ASSIGNED','BLOCKED') "
                + "ORDER BY CASE status WHEN 'ASSIGNED' THEN 0 ELSE 1 END, COALESCE(blocked_at, assigned_at) DESC, loyalty_card_id DESC LIMIT 1";
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new CardAssignment(rows.getString("card_code"), rows.getString("status"), rows.getTimestamp("assigned_at"))) : Optional.empty();
            }
        }
    }

    /** Creates a customer; when a card code is given it is assigned (and registered if new) in the same transaction. */
    public int createLoyaltyCustomer(String enteredCardCode, String customerName, String phone) throws LoyaltyCardAssignmentException {
        String name = customerName == null ? "" : customerName.trim();
        String enteredPhone = phone == null ? "" : phone.trim();
        String normalizedPhone = normalizePhone(enteredPhone);
        if (name.isEmpty()) throw new LoyaltyCardAssignmentException("Customer name is required.");
        if (enteredPhone.isEmpty() || normalizedPhone.isEmpty()) throw new LoyaltyCardAssignmentException("Phone number is required.");
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                boolean withCard = !normalizeCardCode(enteredCardCode).isEmpty();
                int cardId = withCard ? lockAvailableCard(connection, enteredCardCode) : 0;
                if (withCard) rejectDuplicateLoyaltyPhone(connection, normalizedPhone);
                int customerId = insertLoyaltyCustomer(connection, name, enteredPhone, withCard);
                if (withCard) assignLockedCard(connection, cardId, customerId);
                connection.commit(); return customerId;
            } catch (Exception failure) {
                rollback(connection, failure);
                if (failure instanceof LoyaltyCardAssignmentException assignmentFailure) throw assignmentFailure;
                throw new LoyaltyCardAssignmentException("Unable to create loyalty customer.", failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardAssignmentException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardAssignmentException("Unable to create loyalty customer.", failure); }
    }

    /** Assigns one available card to an existing customer in a single transaction. */
    public void assignCard(int customerId, String enteredCardCode) throws LoyaltyCardAssignmentException {
        if (customerId <= 0) throw new LoyaltyCardAssignmentException("Select an existing customer.");
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                lockCustomer(connection, customerId);
                rejectIfCustomerAlreadyHasCard(connection, customerId);
                int cardId = lockAvailableCard(connection, enteredCardCode);
                assignLockedCard(connection, cardId, customerId);
                markCustomerAsLoyalty(connection, customerId);
                connection.commit();
            } catch (Exception failure) {
                rollback(connection, failure);
                if (failure instanceof LoyaltyCardAssignmentException assignmentFailure) throw assignmentFailure;
                throw new LoyaltyCardAssignmentException("Unable to assign loyalty card.", failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardAssignmentException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardAssignmentException("Unable to assign loyalty card.", failure); }
    }

    /** Updates customer details and assigns one available card as one customer-management transaction. */
    public void updateCustomerAndAssignCard(int customerId, String customerName, String phone, String enteredCardCode)
            throws LoyaltyCardAssignmentException {
        if (customerId <= 0) throw new LoyaltyCardAssignmentException("Select an existing customer.");
        String name = customerName == null ? "" : customerName.trim();
        String enteredPhone = phone == null ? "" : phone.trim();
        if (name.isEmpty()) throw new LoyaltyCardAssignmentException("Customer name is required.");
        if (enteredPhone.isEmpty() || normalizePhone(enteredPhone).isEmpty()) throw new LoyaltyCardAssignmentException("Phone number is required.");
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                lockCustomer(connection, customerId);
                rejectIfCustomerAlreadyHasCard(connection, customerId);
                int cardId = lockAvailableCard(connection, enteredCardCode);
                updateCustomerDetails(connection, customerId, name, enteredPhone);
                assignLockedCard(connection, cardId, customerId);
                markCustomerAsLoyalty(connection, customerId);
                connection.commit();
            } catch (Exception failure) {
                rollback(connection, failure);
                if (failure instanceof LoyaltyCardAssignmentException assignmentFailure) throw assignmentFailure;
                throw new LoyaltyCardAssignmentException("Unable to update customer and assign loyalty card.", failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardAssignmentException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardAssignmentException("Unable to update customer and assign loyalty card.", failure); }
    }

    /** Blocks the customer's current assigned card without breaking its historical customer link. */
    public void blockCurrentCard(int customerId) throws LoyaltyCardLifecycleException {
        inLifecycleTransaction(customerId, (connection, current) -> {
            if (current == null || !"ASSIGNED".equalsIgnoreCase(current.status()))
                throw new LoyaltyCardLifecycleException("This customer does not have an assigned loyalty card to block.");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE loyalty_cards SET status='BLOCKED', blocked_at=CURRENT_TIMESTAMP WHERE loyalty_card_id=? AND status='ASSIGNED'")) {
                statement.setInt(1, current.cardId());
                if (statement.executeUpdate() != 1) throw new LoyaltyCardLifecycleException("The loyalty card is no longer assigned.");
            }
        }, "Unable to block loyalty card.");
    }

    /** Restores a blocked card only when this customer has no other active assigned card. */
    public void unblockCurrentCard(int customerId) throws LoyaltyCardLifecycleException {
        inLifecycleTransaction(customerId, (connection, current) -> {
            if (current == null || !"BLOCKED".equalsIgnoreCase(current.status()))
                throw new LoyaltyCardLifecycleException("This customer does not have a blocked loyalty card to unblock.");
            if (hasAssignedCard(connection, customerId))
                throw new LoyaltyCardLifecycleException("This customer already has another active loyalty card.");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE loyalty_cards SET status='ASSIGNED', blocked_at=NULL WHERE loyalty_card_id=? AND status='BLOCKED'")) {
                statement.setInt(1, current.cardId());
                if (statement.executeUpdate() != 1) throw new LoyaltyCardLifecycleException("The loyalty card is no longer blocked.");
            }
        }, "Unable to unblock loyalty card.");
    }

    /** Retains the old card as BLOCKED and atomically assigns an available replacement card. */
    public void replaceCurrentCard(int customerId, String enteredReplacementCard) throws LoyaltyCardLifecycleException {
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                lockCustomerForLifecycle(connection, customerId);
                CurrentCard current = lockCurrentCard(connection, customerId);
                if (current == null) throw new LoyaltyCardLifecycleException("This customer has no loyalty card to replace.");
                if ("ASSIGNED".equalsIgnoreCase(current.status())) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE loyalty_cards SET status='BLOCKED', blocked_at=CURRENT_TIMESTAMP WHERE loyalty_card_id=? AND status='ASSIGNED'")) {
                        statement.setInt(1, current.cardId());
                        if (statement.executeUpdate() != 1) throw new LoyaltyCardLifecycleException("The current loyalty card changed before replacement.");
                    }
                } else if (!"BLOCKED".equalsIgnoreCase(current.status())) {
                    throw new LoyaltyCardLifecycleException("This customer has no replaceable loyalty card.");
                }
                if (hasAssignedCard(connection, customerId))
                    throw new LoyaltyCardLifecycleException("This customer already has another active loyalty card.");
                int replacementCardId = lockAvailableCard(connection, enteredReplacementCard);
                assignLockedCard(connection, replacementCardId, customerId);
                markCustomerAsLoyalty(connection, customerId);
                connection.commit();
            } catch (Exception failure) {
                rollbackLifecycle(connection, failure);
                if (failure instanceof LoyaltyCardLifecycleException lifecycleFailure) throw lifecycleFailure;
                if (failure instanceof LoyaltyCardAssignmentException assignmentFailure) throw new LoyaltyCardLifecycleException(assignmentFailure.getMessage(), assignmentFailure);
                throw new LoyaltyCardLifecycleException("Unable to replace loyalty card.", failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardLifecycleException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardLifecycleException("Unable to replace loyalty card.", failure); }
    }

    /** Read-only chronological record of every card retained for a customer. */
    public List<CardHistoryRow> cardHistory(int customerId) throws SQLException {
        String sql = "SELECT card_code, status, assigned_at, blocked_at FROM loyalty_cards WHERE customer_id=? "
                + "ORDER BY CASE status WHEN 'ASSIGNED' THEN 0 WHEN 'BLOCKED' THEN 1 ELSE 2 END, COALESCE(blocked_at, assigned_at) DESC, loyalty_card_id DESC";
        List<CardHistoryRow> history = new ArrayList<>();
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) history.add(new CardHistoryRow(rows.getString("card_code"), rows.getString("status"), rows.getTimestamp("assigned_at"), rows.getTimestamp("blocked_at")));
            }
        }
        return history;
    }

    /** Indicates whether any retained loyalty-card row belongs to this customer. */
    public boolean hasCardHistory(int customerId) throws SQLException {
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM loyalty_cards WHERE customer_id=? LIMIT 1")) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    /** Checks deletion safety without modifying the customer or its historical records. */
    public DeleteEligibility canDeleteCustomer(int customerId) throws SQLException {
        try (Connection connection = connections.get()) { return deleteEligibility(connection, customerId, false); }
    }

    /** Safely releases unused cards and deletes only a customer without transaction or loyalty history. */
    public void deleteCustomerIfSafe(int customerId) throws LoyaltyCardLifecycleException {
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                DeleteEligibility eligibility = deleteEligibility(connection, customerId, true);
                if (!eligibility.allowed()) throw new LoyaltyCardLifecycleException(eligibility.reason());
                try (PreparedStatement release = connection.prepareStatement(
                        "UPDATE loyalty_cards SET customer_id=NULL, status='AVAILABLE', assigned_at=NULL, blocked_at=NULL WHERE customer_id=?")) {
                    release.setInt(1, customerId); release.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM customers WHERE customer_id=?")) {
                    delete.setInt(1, customerId);
                    if (delete.executeUpdate() != 1) throw new LoyaltyCardLifecycleException("Customer was not found.");
                }
                connection.commit();
            } catch (Exception failure) {
                rollbackLifecycle(connection, failure);
                if (failure instanceof LoyaltyCardLifecycleException lifecycleFailure) throw lifecycleFailure;
                throw new LoyaltyCardLifecycleException("Unable to delete customer.", failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardLifecycleException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardLifecycleException("Unable to delete customer.", failure); }
    }

    /**
     * Archives a customer who has sales or loyalty history: every record is kept, the customer disappears from
     * the customer list and checkout, and any loyalty card is released so it can be issued again.
     */
    public void archiveCustomer(int customerId) throws LoyaltyCardLifecycleException {
        try (Connection connection = connections.get()) {
            if (!SchemaSupport.hasColumn(connection, "customers", "active"))
                throw new LoyaltyCardLifecycleException("Archiving customers needs the V4__customer_archive.sql database upgrade.");
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                lockCustomerForLifecycle(connection, customerId);
                DeleteEligibility eligibility = deleteEligibility(connection, customerId, true);
                if ("Walk-in Customer cannot be deleted.".equals(eligibility.reason())) throw new LoyaltyCardLifecycleException(eligibility.reason());
                try (PreparedStatement release = connection.prepareStatement(
                        "UPDATE loyalty_cards SET customer_id=NULL, status='AVAILABLE', assigned_at=NULL, blocked_at=NULL WHERE customer_id=?")) {
                    release.setInt(1, customerId); release.executeUpdate();
                }
                try (PreparedStatement archive = connection.prepareStatement("UPDATE customers SET active=FALSE WHERE customer_id=?")) {
                    archive.setInt(1, customerId);
                    if (archive.executeUpdate() != 1) throw new LoyaltyCardLifecycleException("Customer was not found.");
                }
                connection.commit();
            } catch (Exception failure) {
                rollbackLifecycle(connection, failure);
                if (failure instanceof LoyaltyCardLifecycleException lifecycleFailure) throw lifecycleFailure;
                throw new LoyaltyCardLifecycleException("Unable to archive customer.", failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardLifecycleException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardLifecycleException("Unable to archive customer.", failure); }
    }

    private void inLifecycleTransaction(int customerId, LifecycleOperation operation, String failureMessage)
            throws LoyaltyCardLifecycleException {
        if (customerId <= 0) throw new LoyaltyCardLifecycleException("Select an existing customer.");
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                lockCustomerForLifecycle(connection, customerId);
                operation.apply(connection, lockCurrentCard(connection, customerId));
                connection.commit();
            } catch (Exception failure) {
                rollbackLifecycle(connection, failure);
                if (failure instanceof LoyaltyCardLifecycleException lifecycleFailure) throw lifecycleFailure;
                throw new LoyaltyCardLifecycleException(failureMessage, failure);
            } finally { restoreAutoCommit(connection, originalAutoCommit); }
        } catch (LoyaltyCardLifecycleException failure) { throw failure; }
        catch (SQLException failure) { throw new LoyaltyCardLifecycleException(failureMessage, failure); }
    }

    private static void lockCustomerForLifecycle(Connection connection, int customerId) throws SQLException, LoyaltyCardLifecycleException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT customer_id FROM customers WHERE customer_id=? FOR UPDATE")) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new LoyaltyCardLifecycleException("Customer was not found.");
            }
        }
    }

    private static CurrentCard lockCurrentCard(Connection connection, int customerId) throws SQLException {
        String sql = "SELECT loyalty_card_id, card_code, status, assigned_at, blocked_at FROM loyalty_cards "
                + "WHERE customer_id=? AND status IN ('ASSIGNED','BLOCKED') "
                + "ORDER BY CASE status WHEN 'ASSIGNED' THEN 0 ELSE 1 END, COALESCE(blocked_at, assigned_at) DESC, loyalty_card_id DESC LIMIT 1 FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new CurrentCard(rows.getInt("loyalty_card_id"), rows.getString("card_code"), rows.getString("status"), rows.getTimestamp("assigned_at"), rows.getTimestamp("blocked_at")) : null;
            }
        }
    }

    private static boolean hasAssignedCard(Connection connection, int customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT loyalty_card_id FROM loyalty_cards WHERE customer_id=? AND status='ASSIGNED' FOR UPDATE")) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static DeleteEligibility deleteEligibility(Connection connection, int customerId, boolean lockCustomer)
            throws SQLException {
        String customerSql = "SELECT customer_name, loyalty_points FROM customers WHERE customer_id=?" + (lockCustomer ? " FOR UPDATE" : "");
        String customerName;
        int loyaltyPoints;
        try (PreparedStatement statement = connection.prepareStatement(customerSql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return new DeleteEligibility(false, "Customer was not found.", false);
                customerName = rows.getString("customer_name"); loyaltyPoints = rows.getInt("loyalty_points");
            }
        }
        if ("walk-in customer".equalsIgnoreCase(customerName == null ? "" : customerName.trim()))
            return new DeleteEligibility(false, "Walk-in Customer cannot be deleted.", false);
        if (loyaltyPoints > 0)
            return new DeleteEligibility(false, "This customer has transaction or loyalty history, so it cannot be permanently deleted.", true);

        java.util.Set<String> checkedReferences = new java.util.HashSet<>();
        String references = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='customers'";
        try (PreparedStatement statement = connection.prepareStatement(references); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String table = rows.getString("TABLE_NAME"); String column = rows.getString("COLUMN_NAME");
                if ("loyalty_cards".equalsIgnoreCase(table) || !safeIdentifier(table) || !safeIdentifier(column)) continue;
                checkedReferences.add(table.toLowerCase(java.util.Locale.ROOT) + "." + column.toLowerCase(java.util.Locale.ROOT));
                if (hasCustomerReference(connection, table, column, customerId))
                    return new DeleteEligibility(false, "This customer has transaction or loyalty history, so it cannot be permanently deleted.", true);
            }
        }
        for (String[] knownReference : List.of(new String[]{"sales", "customer_id"}, new String[]{"sale_loyalty", "customer_id"})) {
            String key = knownReference[0] + "." + knownReference[1];
            if (!checkedReferences.contains(key) && SchemaSupport.hasTable(connection, knownReference[0])
                    && SchemaSupport.hasColumn(connection, knownReference[0], knownReference[1])
                    && hasCustomerReference(connection, knownReference[0], knownReference[1], customerId))
                return new DeleteEligibility(false, "This customer has transaction or loyalty history, so it cannot be permanently deleted.", true);
        }
        return new DeleteEligibility(true, "", false);
    }

    private static boolean hasCustomerReference(Connection connection, String table, String column, int customerId) throws SQLException {
        String sql = "SELECT 1 FROM `" + table + "` WHERE `" + column + "`=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static boolean safeIdentifier(String identifier) { return identifier != null && identifier.matches("[A-Za-z0-9_]+"); }

    private static void rollbackLifecycle(Connection connection, Exception originalFailure) throws LoyaltyCardLifecycleException {
        try { connection.rollback(); }
        catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
            throw new LoyaltyCardLifecycleException("Unable to roll back loyalty-card changes.", originalFailure);
        }
    }

    private static int insertLoyaltyCustomer(Connection connection, String name, String phone, boolean loyalty) throws SQLException, LoyaltyCardAssignmentException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO customers (customer_name, phone, loyalty_points, loyalty_customer) VALUES (?, ?, 0, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name); statement.setString(2, phone); statement.setBoolean(3, loyalty);
            if (statement.executeUpdate() != 1) throw new LoyaltyCardAssignmentException("Customer could not be created.");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new LoyaltyCardAssignmentException("Customer ID could not be created.");
                return keys.getInt(1);
            }
        }
    }

    private static void rejectDuplicateLoyaltyPhone(Connection connection, String normalizedPhone) throws SQLException, LoyaltyCardAssignmentException {
        String sql = "SELECT customer_id FROM customers WHERE loyalty_customer=1 AND REPLACE(REPLACE(TRIM(COALESCE(phone, '')), ' ', ''), '-', '')=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedPhone);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) throw new LoyaltyCardAssignmentException("A loyalty customer already uses this phone number.");
            }
        }
    }

    private static void lockCustomer(Connection connection, int customerId) throws SQLException, LoyaltyCardAssignmentException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT customer_id FROM customers WHERE customer_id=? FOR UPDATE")) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) { if (!rows.next()) throw new LoyaltyCardAssignmentException("Customer was not found."); }
        }
    }

    private static void rejectIfCustomerAlreadyHasCard(Connection connection, int customerId) throws SQLException, LoyaltyCardAssignmentException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT card_code, status FROM loyalty_cards WHERE customer_id=? AND status IN ('ASSIGNED','BLOCKED') FOR UPDATE")) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) throw new LoyaltyCardAssignmentException("This customer already has card " + rows.getString("card_code") + " (" + rows.getString("status") + ").");
            }
        }
    }

    private static int lockAvailableCard(Connection connection, String enteredCardCode) throws SQLException, LoyaltyCardAssignmentException {
        String normalized = normalizeCardCode(enteredCardCode);
        if (normalized.isEmpty()) throw new LoyaltyCardAssignmentException("Enter or scan a loyalty card code.");
        String sql = "SELECT lc.loyalty_card_id, lc.card_code, lc.status, lc.customer_id, c.customer_name FROM loyalty_cards lc "
                + "LEFT JOIN customers c ON c.customer_id=lc.customer_id WHERE REPLACE(TRIM(lc.card_code), ' ', '')=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return registerNewCard(connection, normalized);
                CardLookup card = cardLookup(rows);
                if ("ASSIGNED".equalsIgnoreCase(card.status())) throw new LoyaltyCardAssignmentException("This loyalty card is ASSIGNED to " + customerDescription(card) + ".");
                if ("BLOCKED".equalsIgnoreCase(card.status())) throw new LoyaltyCardAssignmentException("This loyalty card is BLOCKED and cannot be used.");
                if (!"AVAILABLE".equalsIgnoreCase(card.status())) throw new LoyaltyCardAssignmentException("This loyalty card is not AVAILABLE.");
                return card.loyaltyCardId();
            }
        }
    }

    /** Cards are never pre-loaded, so a first-time scanned code is registered as AVAILABLE and then assigned by the caller. */
    private static int registerNewCard(Connection connection, String normalizedCode) throws SQLException, LoyaltyCardAssignmentException {
        if (normalizedCode.length() > 30) throw new LoyaltyCardAssignmentException("Loyalty card code must be 30 characters or fewer.");
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO loyalty_cards (card_code, status) VALUES (?, 'AVAILABLE')", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalizedCode);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new LoyaltyCardAssignmentException("Loyalty card could not be registered.");
                return keys.getInt(1);
            }
        }
    }

    private static CardLookup cardLookup(ResultSet rows) throws SQLException {
        int assignedId = rows.getInt("customer_id");
        boolean noAssignedCustomer = rows.wasNull();
        return new CardLookup(rows.getInt("loyalty_card_id"), rows.getString("card_code"), rows.getString("status"), noAssignedCustomer ? null : assignedId, rows.getString("customer_name"));
    }

    private static String customerDescription(CardLookup card) {
        if (card.customerId() == null) return "another customer";
        return "customer #" + card.customerId() + (card.customerName() == null || card.customerName().isBlank() ? "" : " (" + card.customerName() + ")");
    }

    private static void assignLockedCard(Connection connection, int cardId, int customerId) throws SQLException, LoyaltyCardAssignmentException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE loyalty_cards SET customer_id=?, status='ASSIGNED', assigned_at=CURRENT_TIMESTAMP, blocked_at=NULL WHERE loyalty_card_id=? AND status='AVAILABLE'")) {
            statement.setInt(1, customerId); statement.setInt(2, cardId);
            if (statement.executeUpdate() != 1) throw new LoyaltyCardAssignmentException("The loyalty card is no longer AVAILABLE.");
        }
    }

    private static void markCustomerAsLoyalty(Connection connection, int customerId) throws SQLException, LoyaltyCardAssignmentException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE customers SET loyalty_customer=1 WHERE customer_id=?")) {
            statement.setInt(1, customerId);
            if (statement.executeUpdate() != 1) throw new LoyaltyCardAssignmentException("Customer was not found.");
        }
    }

    private static void updateCustomerDetails(Connection connection, int customerId, String customerName, String phone)
            throws SQLException, LoyaltyCardAssignmentException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE customers SET customer_name=?, phone=? WHERE customer_id=?")) {
            statement.setString(1, customerName); statement.setString(2, phone); statement.setInt(3, customerId);
            if (statement.executeUpdate() != 1) throw new LoyaltyCardAssignmentException("Customer was not found.");
        }
    }

    private static void rollback(Connection connection, Exception originalFailure) throws LoyaltyCardAssignmentException {
        try { connection.rollback(); } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
            throw new LoyaltyCardAssignmentException("Unable to roll back loyalty card transaction.", originalFailure);
        }
    }
    private static void restoreAutoCommit(Connection connection, boolean originalAutoCommit) { try { connection.setAutoCommit(originalAutoCommit); } catch (SQLException ignored) { } }

    static String normalizePhone(String phone) { return phone == null ? "" : phone.replaceAll("[\\s-]+", ""); }
    static String normalizeCardCode(String cardCode) { return cardCode == null ? "" : cardCode.trim().replaceAll("\\s+", ""); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    public record CustomerMatch(int customerId, String customerName, String phone) {
        @Override public String toString() { return "#" + customerId + " - " + customerName + (phone == null || phone.isBlank() ? "" : " (" + phone + ")"); }
    }
    public record CustomerCardRow(int customerId, String customerName, String phone, String cardCode, String cardStatus, int loyaltyPoints, Timestamp assignedAt) { }
    public record CardLookup(int loyaltyCardId, String cardCode, String status, Integer customerId, String customerName) { }
    public record CardAssignment(String cardCode, String status, Timestamp assignedAt) { }
    public record CardHistoryRow(String cardCode, String status, Timestamp assignedAt, Timestamp blockedAt) { }
    /** archivable: the customer has history, so archiving (hiding) is offered instead of permanent deletion. */
    public record DeleteEligibility(boolean allowed, String reason, boolean archivable) { }
    private record CurrentCard(int cardId, String cardCode, String status, Timestamp assignedAt, Timestamp blockedAt) { }
    @FunctionalInterface private interface LifecycleOperation {
        void apply(Connection connection, CurrentCard current) throws SQLException, LoyaltyCardLifecycleException;
    }
    public static final class LoyaltyCardAssignmentException extends Exception {
        public LoyaltyCardAssignmentException(String message) { super(message); }
        public LoyaltyCardAssignmentException(String message, Throwable cause) { super(message, cause); }
    }
    public static final class LoyaltyCardLifecycleException extends Exception {
        public LoyaltyCardLifecycleException(String message) { super(message); }
        public LoyaltyCardLifecycleException(String message, Throwable cause) { super(message, cause); }
    }
}
