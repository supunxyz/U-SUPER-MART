# EasyMart POS

## Installer (all-in-one)

The Windows installer built with `jpackage --app-content bundle\mariadb ...` includes a bundled MariaDB. On first launch the app creates its own database under `%LOCALAPPDATA%\UshanthaSuperMartPOS`, applies the V1-V4 migrations with version tracking, and asks you to create the first administrator. Existing installations using the old shipped administrator password must set a new one at startup. No separate MySQL, Java, or environment variables are needed. Setting `EASYMART_DB_URL` switches the app to an external MySQL server instead (see Setup below).

## Setup

When running the project from NetBeans, set the working directory to the project root (the default). The application finds `bundle/mariadb` there and starts its local database automatically, unless `EASYMART_DB_URL` is set. On first launch, create an administrator in the setup dialog.

1. Create the `easymart_pos` MySQL database and run the migration files in numeric order (`V1`, `V2`, `V3`, then `V4`) once. V3 adds the login, purchase, batch-inventory, promotion, loyalty-card, expense, and cashier-shift tables used by the application.
2. Configure database access without storing passwords in source code:
   - `EASYMART_DB_URL` (default: `jdbc:mysql://localhost:3306/easymart_pos`)
   - `EASYMART_DB_USER` (default: `root`)
   - `EASYMART_DB_PASSWORD` (set this for password-protected external MySQL; there is no password built into the app)
3. On first launch, create the administrator in the setup dialog with a password of at least 12 characters. Existing plaintext passwords are upgraded to PBKDF2 hashes at startup. Administrators can add users, reset passwords, and enable or disable accounts from User Accounts. Every user can change their own password.
4. Run `mvn test` and then start `com.mycompany.easymartpos.EasyMartPOS`.

## Core workflows

- Add/update/archive products in Item Management. Enter an opening quantity when creating an item; it creates a sellable opening batch. Later stock changes use Stock Adjustment, with a reason for every correction.
- At checkout, scan/select active items, enter a fixed LKR discount (`250`) or a percentage of the bill (`10%`), then use cash tender/change or a card terminal reference.
- Completed sales are fully transactional: sale, payment, items, stock change, and audit movement succeed or fail together.
- Sales History can preview/print a receipt and void a completed sale with a reason. Voiding restores stock and marks the payment void.

Checkout supports cash, card, bank transfer, and split payments. Tax calculation, card gateways, and partial returns are not implemented.
