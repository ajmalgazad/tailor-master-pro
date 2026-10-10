# Database schema (Room / SQLite) — version 1

All money columns are `INTEGER` **paisa** (1 PKR = 100 paisa). All dates are epoch milliseconds.
Source of truth: `app/src/main/java/pk/tailormaster/pro/data/Entities.kt`.
The exact generated schema JSON is published by CI on the `ci-log` branch (`1.json`).

| Table | Purpose | Key relations |
|---|---|---|
| `customers` | Main customer account (code `C-0001` = id) | `mergedIntoId` → customers |
| `family_members` | People under one customer; `isSelf` = the customer himself | `customerId` → customers |
| `garments` | Clothing categories (men/women/kids), default rate, receipt text, order | — |
| `measurement_fields` | Configurable measurement labels per garment, unit, required, order | `garmentId` → garments |
| `measurement_profiles` | Unlimited profiles per member+garment; values as JSON; versions via `previousId` + `archived` | `customerId`, `memberId`, `garmentId` |
| `custom_rates` | Customer- or member-specific rate per garment | `garmentId`, `customerId`, `memberId` |
| `rate_history` | Every default/custom rate change | `garmentId` |
| `services` | Extra charges (urgent, embroidery, alteration, custom) | — |
| `orders` | Booking header with stored totals (items, charges, discount, net), status, draft flag | `customerId`, `memberId` |
| `order_items` | Line items: qty, **agreed rate**, total, item status, delivery flags, and a **frozen measurement snapshot** (`measurementJson`) | `orderId`, `garmentId`, `memberId`, `profileId` |
| `status_log` | Every status change with time and note | `orderId`, `itemId` |
| `txns` | Ledger: payments, refunds, adjustments, reversals. `signedEffect` (+ owes more / − owes less); `cash` = money moved | `customerId`, `orderId`, `reversesId`, `reversedById` |
| `deliveries` | Each (partial) delivery event and its items | `orderId`, `paymentTxnId` |
| `expenses` | Business expenses (voided, never deleted) | — |
| `print_log` | Every print attempt and its honest status (OK / SENT / FAILED) | `kind` + `refId` |
| `audit_log` | Important changes (create, edit, merge, reverse, restore) | — |

## Rules enforced in code (`Repository.kt`, `Calc.kt`)

- Item total = qty × rate; Net = items + charges − discount; Outstanding = net − payments.
- Customer balance = opening balance + net of confirmed, non-cancelled orders + Σ `signedEffect` of transactions.
- Measurement snapshots are copied into `order_items` at booking time; editing a profile creates a new version.
- Changing a default or custom rate never updates existing order items.
- Financial records are never deleted: reversal creates an opposite entry.
- Multi-record operations run inside one database transaction.

## Migrations

`AppDatabase.VERSION = 1`. When an entity changes:
1. Increase `VERSION`.
2. Add a `Migration(old, new)` to `AppMigrations.all`.
3. Never enable destructive migration, so shop data is kept when the app is updated.
