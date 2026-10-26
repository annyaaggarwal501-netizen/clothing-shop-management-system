# CSMS Prototype — Dummy Data Pack (Mansi Collection)

Dummy data for the Clothing Shop Management System prototype. It matches SRS v1.0.1:
Section 10.3.1 (import format) and Section 10.2.2 (schema).

## What's inside

| File | What it is | How to use it |
|---|---|---|
| `products_import.csv` | 50 products → **274 variants** (size × colour), with SKU-ready barcodes | Test the bulk-import screen (REQ-PRD-5), or open it in Excel to browse |
| `V1__schema.sql` | Database tables for the prototype (MySQL 8) | Put in `src/main/resources/db/migration/` — Flyway runs it first |
| `V2__seed_data.sql` | Users, GST slabs, 27 categories, the 50 products and 274 variants, opening stock, 3 suppliers, 30 customers, 1 offer | Same folder — Flyway runs it after V1 |
| `team_test_customers.sql` | 5 test customers = your team, opted in for WhatsApp | **Edit first** (see below), then run manually |
| `customers.csv` | The same 30 dummy customers as a spreadsheet | Reference / customer-import testing |
| `gst_test_cases.csv` | 11 billing test cases with the exact expected rate, taxable value, CGST and SGST | Use as your unit tests for the TaxCalculator and in the demo |

Tested: both SQL files load without errors (MariaDB 10, MySQL-compatible), and stock in
`variant` matches the `stock_movement` ledger exactly.

## Logins (demo only — change before any real use)

| Username | Password | Role | Owner PIN |
|---|---|---|---|
| `owner` | `Owner@123` | OWNER | `2580` |
| `staff` | `Staff@123` | SALES_STAFF | — |

Passwords are stored as BCrypt hashes (cost 12), as REQ-ACC-3 requires.

## ⚠️ WhatsApp safety rules

1. The 30 dummy customers have **random mobile numbers** that may belong to real people.
   They are all `wa_opt_in = FALSE`, so the app's consent rule blocks messages to them.
   **Never** share products or bills with them manually.
2. Use only `team_test_customers.sql` for WhatsApp tests. Replace each `REPLACE_ME` with a
   team member's own number (with their permission). The database **rejects the file** until
   every number is a valid 10-digit mobile.

## Built-in demo situations

- **GST tax classes.** Sarees and *Unstitched Suit Material* are `FABRIC` → **5% at any price**.
  The government treats sarees as fabric, not garments. All other items are `APPAREL`
  (5% up to ₹2,500 taxable value per piece, 18% above).
- **Boundary items:**
  - ₹1,050 kurti → 5%
  - ₹2,625 Anarkali → 5% (taxable exactly ₹2,500)
  - ₹2,626 party kurta set → 18%
  - ₹2,950 lehenga → 18%
  - ₹3,540 blazer → 18% (taxable ₹3,000)
  - ₹3,200 silk saree → **5%**
- **Discount changes the slab:** the blazer with ₹1,000 off becomes ₹2,540 → 5% (test TC-BIL-010).
- **Stock alerts:** 14 variants are at 0 stock (OUT_OF_STOCK) and 33 are at 1–2 (LOW_STOCK, reorder level 2).
- **Returns:** innerwear, socks, blouse, nightsuit, romper, sherwani and bridal lehenga are **non-returnable**
  (ERR-RET-003 test).
- **Offer:** "Festive 15% off on Sarees", valid 1 Oct – 15 Nov 2026.
- **Barcodes:** EAN-13 numbers starting with **200**, the GS1 range reserved for in-store use, so they can never
  clash with real product barcodes. Print them as labels and scan them at the POS.

## Rounding note

CGST is rounded half-up to 2 decimals, and SGST = total tax − CGST, so the two halves always
add up exactly to the tag price. This means they can differ by ₹0.01 (e.g., TC-BIL-003).

## Product photos

Photos are not included. Options:
- The owner's own phone photos (best).
- Free images from Unsplash or Pexels (search "kurti", "saree", "lehenga").

Name each file after the SKU (e.g., `KUR-0001-M-BLU.jpg`) so it's easy to attach to the right variant.
