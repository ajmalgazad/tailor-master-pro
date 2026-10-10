# Test results

## Automated (every build, GitHub Actions)

| Suite | Where | Result (run 3) |
|---|---|---|
| Money calculation unit tests (`CalcTest`, 8 tests) | JVM | ✅ all passed |
| Business rules on real SQLite (`BusinessRulesTest`, 8 tests) | Android 10 emulator (x86_64) | ✅ all passed |
| Screenshot walkthrough of 28 screens (`ScreenshotTest`) | Android 10 emulator | ✅ no crash |
| Receipt rendering, 58mm / 80mm, Naskh / Nastaliq, all receipt types + PDF/CSV export | Android 10 emulator | ✅ rendered |

Business rules covered (spec section 22):

- [x] Multiple family members under one customer
- [x] Multiple measurement profiles per member, measurements not mixed
- [x] Old measurements unchanged when a new version is saved
- [x] Different garments in one order use different profiles
- [x] Custom rates (customer, member) and % / fixed discounts calculate correctly
- [x] Changing default rates does not alter existing orders
- [x] Payments update balances; reversals restore them; double reversal blocked
- [x] Building / printing receipts creates no orders or payments
- [x] Partial delivery, then complete delivery
- [x] Backup → restore into empty database preserves all records and balances; broken files rejected
- [x] Data survives closing and reopening the database (app restart)
- [x] Works fully offline (no network code in the app)

Screenshots and receipt images of the latest run: branch `ci-device`, folder `shots/`.

## Still to be done on the real phone and printer (cannot be automated)

- [ ] APK installs and opens on the shop's phone
- [ ] Bluetooth printer pairs and connects
- [ ] Test print: Urdu letters, digits, punctuation, mixed Urdu/English (see `docs/PRINTER_GUIDE_URDU.md`)
- [ ] Booking, payment, delivery receipts and measurement slip print correctly on 58mm / 80mm paper
- [ ] Backup file saves to Downloads / Google Drive and restores on the phone
