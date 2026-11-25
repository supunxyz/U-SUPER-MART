package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.PaymentMethod;
import com.mycompany.easymartpos.model.PaymentRequest;

/** Hardware-neutral integration point. A printer-specific ESC/POS implementation can be supplied after physical testing. */
public interface CashDrawerHook {
    void openDrawer() throws Exception;

    static boolean hasCashComponent(PaymentRequest payment) {
        if (payment == null) return false;
        if (payment.method() == PaymentMethod.CASH) return true;
        return payment.parts().stream().anyMatch(part -> part.method() == PaymentMethod.CASH && part.amount() != null && part.amount().signum() > 0);
    }
}
