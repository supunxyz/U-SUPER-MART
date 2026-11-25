# EasyMart POS audit — 2026-11-19

## Scope and verification

Reviewed the application entry point, login, bundled database startup and migrations, sales and stock paths, purchase and expense forms, and the existing unit tests. The Maven test suite passes: 101 tests, 0 failures. Disposable bundled MariaDB probes covered fresh startup, restart, repair of an untracked older schema, administrator reset, legacy password upgrade, concurrent sales, and sale voiding. A physical printer, installer, and interactive UI were not tested.

## Fixed in this audit

1. Purchase creation and deletion now attribute stock movements to the signed-in user instead of user ID 1 (`PurchaseForm.java`).
2. Purchase deletion now records a negative stock movement, matching the reduction of product stock (`PurchaseForm.java`).
3. Expense entry now rejects zero/negative amounts, uses decimal arithmetic, and records the signed-in user (`ExpenseForm.java`).
4. Thermal receipt item headers and benefit title fit their assigned space with the installed Windows font. The two failing receipt tests now pass (`ReceiptDialog.java`).
5. The README's stray prefix and outdated split-payment statement were corrected.

## Follow-up fixes completed

### High — fixed default credentials and plaintext user passwords: resolved

New installations prompt for an administrator username and password. The external database password fallback was removed. New passwords use salted PBKDF2-SHA256; legacy plaintext passwords are upgraded at startup. Existing bundled installations using the shipped administrator password must replace it at startup before login. The live dashboard supports adding users, password changes and resets, and account enable/disable.

### High — database migrations are not tracked or checked completely: resolved

Bundled database startup tracks applied scripts in `schema_migrations`, reapplies versionless older databases using the idempotent scripts, fails on migration process errors, retains logs, and checks required tables and columns. The V3 script creates a nonunique foreign-key index before removing the old unique sale-payment index.

### Medium — invoice number generation races: resolved

The displayed pre-sale number remains a preview. Sale completion reserves a UUID placeholder and changes it to an invoice number derived from the inserted sale ID inside the transaction; the committed receipt and result use that final number.

### Medium — legacy sale form bypasses transactional checkout: resolved

`SalesForm` is now a compatibility wrapper around `CheckoutFrame`, so older launchers use the same transactional checkout. Its obsolete NetBeans form was removed.

### Low — legacy dashboard buttons are unimplemented: resolved

`DashboardForm` is now a compatibility wrapper around `PosDashboard`, and its obsolete NetBeans form was removed.

## Suggested next verification

The automated suite passes. Disposable bundled MariaDB databases passed fresh startup, restart, first-admin creation/authentication, reset of the shipped administrator credential, legacy password upgrade, repair from an untracked older schema missing V3/V4 objects, two concurrent sales sharing one preview number, and a sale void with stock restoration. Interactive account dialogs, purchase create/delete, expense author attribution, installer packaging, and a real thermal printer still need end-to-end verification.
