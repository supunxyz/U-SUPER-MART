package com.mycompany.easymartpos;

import com.mycompany.easymartpos.ui.CheckoutFrame;

/** Compatibility name for old launchers; all sales use the transactional checkout. */
@Deprecated
public class SalesForm extends CheckoutFrame {
    public SalesForm() { super(); }
    public static void main(String[] args) { EasyMartPOS.main(args); }
}
