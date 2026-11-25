package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.model.BatchStock;
import com.mycompany.easymartpos.model.CustomerChoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CheckoutFrameBatchAvailabilityTest {

    @Test
    void treatsTypedCustomerTextAsNoCustomerInsteadOfCrashing() {
        assertNull(CheckoutFrame.selectedCustomerChoice("CS 001"));
        CustomerChoice customer = new CustomerChoice(1, "Walk-in Customer");
        assertEquals(customer, CheckoutFrame.selectedCustomerChoice(customer));
    }

    @Test
    void findsTheExactWalkInCustomerForNewSales() {
        CustomerChoice regularCustomer = new CustomerChoice(1, "Abe Stores");
        CustomerChoice walkInCustomer = new CustomerChoice(2, " Walk-in Customer ");

        assertEquals(walkInCustomer, CheckoutFrame.walkInCustomerChoice(List.of(regularCustomer, walkInCustomer)));
        assertNull(CheckoutFrame.walkInCustomerChoice(List.of(regularCustomer)));
    }

    @Test
    void commitsAnExactTypedCustomerNameAtCheckout() {
        CustomerChoice customer = new CustomerChoice(9, "CS 0001");

        assertEquals(customer, CheckoutFrame.exactCustomerNameChoice(List.of(customer), " cs 0001 "));
        assertNull(CheckoutFrame.exactCustomerNameChoice(List.of(customer), "CS 000"));
    }

    @Test
    void usesWalkInCustomerForBlankCheckoutCustomerInput() {
        CustomerChoice walkInCustomer = new CustomerChoice(2, "Walk-in Customer");

        assertEquals(walkInCustomer, CheckoutFrame.checkoutCustomerChoice(List.of(walkInCustomer), null, "  "));
    }

    @Test
    void neverReplacesUnknownTypedCustomerWithWalkInCustomer() {
        CustomerChoice walkInCustomer = new CustomerChoice(2, "Walk-in Customer");

        assertNull(CheckoutFrame.checkoutCustomerChoice(List.of(walkInCustomer), null, "Unknown customer"));
    }

    @Test
    void keepsAnExplicitCustomerSelectionAtCheckout() {
        CustomerChoice walkInCustomer = new CustomerChoice(2, "Walk-in Customer");
        CustomerChoice registeredCustomer = new CustomerChoice(3, "CS 0001");

        assertEquals(registeredCustomer, CheckoutFrame.checkoutCustomerChoice(
                List.of(walkInCustomer, registeredCustomer), registeredCustomer, "Walk-in Customer"));
    }

    @Test
    void subtractsReservationsFromTheExactBatchForEachPriceOption() {
        List<BatchStock> effective = CheckoutFrame.effectiveBatchAvailability(List.of(
                batch(46, "250", "36"), batch(63, "260", "2")),
                Map.of(46, decimal("1"), 63, decimal("2")));

        assertQuantity("35", effective.get(0).quantityAvailable());
        assertQuantity("0", effective.get(1).quantityAvailable());
    }

    @Test
    void neverShowsNegativeAvailabilityWhenCartReservationsExceedTheFreshSnapshot() {
        List<BatchStock> effective = CheckoutFrame.effectiveBatchAvailability(
                List.of(batch(63, "260", "2")), Map.of(63, decimal("3")));

        assertQuantity("0", effective.get(0).quantityAvailable());
    }

    @Test
    void removedCartLineLeavesTheFullDatabaseQuantityAvailableAgain() {
        List<BatchStock> effective = CheckoutFrame.effectiveBatchAvailability(
                List.of(batch(63, "260", "2")), Map.of());

        assertQuantity("2", effective.get(0).quantityAvailable());
    }

    @Test
    void reducingCartReservationImmediatelyReleasesTheDifference() {
        List<BatchStock> effective = CheckoutFrame.effectiveBatchAvailability(
                List.of(batch(63, "260", "2")), Map.of(63, decimal("1")));

        assertQuantity("1", effective.get(0).quantityAvailable());
    }

    @Test
    void replacementPlanCanExcludeItsOwnExistingAllocation() {
        List<BatchStock> effective = CheckoutFrame.effectiveBatchAvailability(
                List.of(batch(63, "260", "2")), Map.of());

        assertQuantity("2", effective.get(0).quantityAvailable());
    }

    private BatchStock batch(int id, String sellingPrice, String quantity) {
        return new BatchStock(id, 10, decimal("180"), decimal(sellingPrice), decimal("280"),
                LocalDate.of(2027, 1, 1), decimal(quantity));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private void assertQuantity(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual));
    }
}
