package com.mycompany.easymartpos;

/** Compatibility name for old launchers; all navigation uses the live dashboard. */
@Deprecated
public class DashboardForm extends PosDashboard {
    public DashboardForm() { super(); }
    public static void main(String[] args) { EasyMartPOS.main(args); }
}
