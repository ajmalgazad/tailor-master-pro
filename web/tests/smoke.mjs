// Drives the web app in demo mode through the main flows and saves screenshots.
import { chromium } from 'playwright-core'
const out = process.argv[2]
const browser = await chromium.launch({ executablePath: process.env.CHROME || '/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell', args: ['--no-sandbox'] })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const errors = []
page.on('pageerror', e => errors.push('pageerror: ' + e.message))
page.on('console', m => { if (m.type() === 'error' && !m.text().includes('fonts.g')) errors.push('console: ' + m.text()) })
const shot = async n => { await page.waitForTimeout(400); await page.screenshot({ path: `${out}/${n}.png`, fullPage: false }) }
const click = async (t, opts = {}) => { await page.locator('.main').getByRole('button', { name: t, exact: opts.exact ?? false }).first().click(); await page.waitForTimeout(250) }

await page.goto('http://localhost:4173/')
await page.waitForSelector('text=ڈیمو موڈ')
await shot('01_home_empty')
// add sample data
await page.locator('.side button', { hasText: 'بیک اپ اور سیٹنگز' }).click()
await click('نمونہ ڈیٹا شامل کریں')
await page.waitForSelector('text=نمونہ ڈیٹا شامل ہو گیا')
await page.locator('.side button', { hasText: 'ڈیش بورڈ' }).click()
await shot('02_home_sample')
// customers
await page.locator('.side button', { hasText: 'کسٹمر ماسٹر' }).click()
await shot('03_customers')
await page.getByText('محمد اسلم (نمونہ)').first().click()
await shot('04_customer_profile')
// new booking for this customer
await click('نئی بکنگ', { exact: true })
await click('+ کپڑا شامل کریں')
await page.locator('.modal select').nth(2).selectOption({ label: 'کوٹ' })
await page.locator('.modal').getByRole('button', { name: 'شامل کریں' }).click()
await page.waitForTimeout(300)
await page.getByLabel('ایڈوانس').fill('2000')
await shot('05_booking')
await click('بکنگ کنفرم کریں')
await page.waitForSelector('text=بکنگ محفوظ ہو گئی')
await shot('06_order_detail')
const net = await page.locator('.kv', { hasText: 'کل رقم' }).first().innerText()
const outstanding = await page.locator('.kv', { hasText: 'بقایا' }).first().innerText()
console.log('ORDER', JSON.stringify({ net, outstanding }))
// payment
await click('رقم وصول کریں')
await shot('07_payment')
await click('محفوظ کریں')
await page.waitForSelector('text=وصولی رسید')
await shot('08_payment_receipt')
// orders list & delivery
await page.locator('.side button', { hasText: 'ڈیلیوری مینجمنٹ' }).click()
await shot('09_deliveries')
await page.getByRole('button', { name: 'ڈیلیور کریں' }).first().click()
await shot('10_deliver')
await click('ڈیلیوری محفوظ کریں')
await page.waitForSelector('text=ڈیلیوری رسید')
await shot('11_delivery_receipt')
// measurements, rates, reports, expenses, designer
for (const [menu, n] of [['ناپ ماسٹر', '12_measurements'], ['ریٹ ماسٹر', '13_rates'], ['اخراجات', '14_expenses'], ['رپورٹس', '15_reports'], ['رسید پرنٹنگ', '16_print']]) {
  await page.locator('.side button', { hasText: menu }).click(); await shot(n)
}
await page.locator('.side button', { hasText: 'رپورٹس' }).click()
await click('روزانہ وصولی رپورٹ')
await shot('17_report')
await page.locator('.side button', { hasText: 'رسید پرنٹنگ' }).click()
await click('رسید ڈیزائن')
await shot('18_designer')
// mobile layout
await page.setViewportSize({ width: 390, height: 844 })
await page.locator('.side button', { hasText: 'ڈیش بورڈ' }).evaluate(b => b.click())
await shot('19_mobile_home')
await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
await shot('20_mobile_dark')
console.log('ERRORS', JSON.stringify(errors))
await browser.close()
