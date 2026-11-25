package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.ReceiptData.ReceiptItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptBenefitCalculatorTest {
    @Test void includesSavedPromotionBenefitWithoutChangingTheSaleTotal() {
        ReceiptItem promoted = item("1020.00", null, "918.00", "1");
        assertEquals(money("102.00"), ReceiptBenefitCalculator.calculate(List.of(promoted), money("0.00")));
    }

    @Test void addsRetailBenefitPromotionBenefitAndManualDiscountOnce() {
        ReceiptItem item = item("100.00", "150.00", "75.00", "1");
        assertEquals(money("75.00"), ReceiptBenefitCalculator.calculate(List.of(item), money("0.00")));
        assertEquals(money("85.00"), ReceiptBenefitCalculator.calculate(List.of(item), money("10.00")));
    }

    @Test void keepsRetailVsOurSavingInformationalAndAddsPromotionOnlyOnce() {
        ReceiptItem item = item("1020.00", "1030.00", "816.00", "1");

        assertEquals(money("214.00"), ReceiptBenefitCalculator.calculate(List.of(item), money("0.00")));
    }

    @Test void sumsRetailSavingPromotionSavingAndManualDiscountAcrossItems() {
        List<ReceiptItem> items = List.of(
                item("410.00", "550.00", "410.00", "3"),
                item("350.00", "400.00", "350.00", "1"),
                item("1020.00", "1030.00", "816.00", "1"));

        assertEquals(money("684.00"), ReceiptBenefitCalculator.calculate(items, money("0.00")));
        assertEquals(money("734.00"), ReceiptBenefitCalculator.calculate(items, money("50.00")));
    }

    private static ReceiptItem item(String regular, String retail, String finalPrice, String quantity) {
        BigDecimal qty = money(quantity); BigDecimal finalUnit = money(finalPrice);
        return new ReceiptItem("Item", qty, retail == null ? null : money(retail), money(regular), finalUnit, finalUnit.multiply(qty));
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
}
