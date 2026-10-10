# ٹیلر ماسٹر پرو — Tailor Master Pro

درزی کی دکان کے لیے مکمل اردو اینڈرائیڈ ایپ: کسٹمر، فیملی ممبرز، ناپ، ریٹ، بکنگ، ڈیلیوری، کھاتہ، اخراجات، بلوٹوتھ رسید پرنٹنگ، رپورٹس اور بیک اپ۔ مکمل آف لائن۔

Urdu (RTL), offline-first Android app for a Pakistani tailoring shop. Kotlin, Jetpack Compose, Room (SQLite), ESC/POS Bluetooth printing.

## ⬇ APK ڈاؤن لوڈ

**https://github.com/ajmalgazad/tailor-master-pro/releases/latest/download/TailorMasterPro.apk**

ہر نیا ورژن اسی لنک پر ملتا ہے اور پرانے ورژن پر انسٹال ہو جاتا ہے۔ ڈیٹا محفوظ رہتا ہے۔

## رہنمائی

- [انسٹال کرنے کا طریقہ](docs/INSTALL_URDU.md)
- [استعمال کی ہدایات](docs/USER_MANUAL_URDU.md)
- [پرنٹر سیٹ اپ اور ٹیسٹ](docs/PRINTER_GUIDE_URDU.md)
- [Database schema & migrations](docs/DATABASE.md)
- [Test results](docs/TEST_RESULTS.md)

## Build

The APK is built automatically by GitHub Actions (`.github/workflows/build.yml`) on every push to `main`:

1. JVM unit tests for all money calculations (`app/src/test`).
2. Signed release APK → published as a GitHub Release.
3. On an Android emulator (API 29): business-rule tests against real SQLite, a screenshot walkthrough of every screen, and every receipt type rendered as it would print (`app/src/androidTest`). Results go to the `ci-device` branch.

Local build: Android Studio (Ladybug or newer) → open the folder → Run. Or `./gradlew assembleRelease`.

The signing key in `keystore/` is committed on purpose so that every build installs as an update over the previous one. Keep the repository private if you don't want others to be able to sign APKs with it.

## Code map

| Path | Contents |
|---|---|
| `data/Entities.kt` | All tables |
| `data/Repository.kt` | Business rules, transactions, ledger, snapshots |
| `domain/Calc.kt` | All money calculations (unit tested) |
| `print/` | Receipt builder, Urdu bitmap renderer, ESC/POS Bluetooth printer |
| `reports/` | 12 reports, PDF and CSV export |
| `data/Backup.kt` | Validated JSON backup / restore |
| `ui/screens/` | All Urdu screens |

Fonts: Noto Nastaliq Urdu and Noto Naskh Arabic (SIL Open Font License, see `keystore/FONT_LICENSE_OFL.txt`).
