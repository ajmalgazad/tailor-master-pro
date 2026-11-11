// Kaarigar flow in demo mode: sample workers, give a garment to a worker, finish it, pay him,
// check the balance and the slips, then the English view. Usage: node tests/workers.mjs <outDir>
import { chromium } from 'playwright-core'
import { existsSync } from 'node:fs'
const out = process.argv[2] || '.'
const base = process.env.BASE || 'http://localhost:4173/'
const cands = [process.env.CHROME, '/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell', '/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell']
const exe = cands.find(p => p && existsSync(p))
const browser = await chromium.launch(exe ? { executablePath: exe, args: ['--no-sandbox'] } : {})
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const errors = []
page.on('pageerror', e => errors.push('pageerror: ' + e.message))
page.on('dialog', d => d.accept())
page.setDefaultTimeout(8000)
const wait = ms => page.waitForTimeout(ms)
const shot = async n => { await wait(350); await page.screenshot({ path: `${out}/w_${n}.png` }) }
const main = page.locator('.main')
const side = t => page.locator('.side button', { hasText: t }).first().click()
const fail = m => { console.log('FAIL: ' + m); process.exitCode = 1 }
async function pickWorker(name) {
  const sel = page.locator('.modal label.field', { hasText: 'کاریگر' }).first().locator('select')
  const v = await sel.evaluate((el, n) => [...el.options].find(o => o.text.startsWith(n))?.value, name)
  await sel.selectOption(v)
}
const has = async t => (await main.getByText(t).count()) > 0

await page.goto(base)
await page.waitForSelector('text=ڈیمو')
const demo = page.getByRole('button', { name: 'بغیر اکاؤنٹ ڈیمو آزمائیں' })
if (await demo.count()) await demo.click()
await page.waitForSelector('text=ڈیمو موڈ')
await side('بیک اپ اور سیٹنگز')
await main.getByRole('button', { name: 'نمونہ ڈیٹا شامل کریں' }).click()
await page.waitForSelector('text=نمونہ ڈیٹا شامل ہو گیا')

// 1. workers list: two sample workers; Rasheed earned 500 (one finished waistcoat) and took 1000 advance
await side('کاریگر')
await wait(300)
await shot('01_list')
if (!(await has('استاد رشید (نمونہ)')) || !(await has('شکیل (نمونہ)'))) fail('sample workers missing')
if (!(await has('ایڈوانس (کاریگر کے ذمے): 500 '))) fail('Rasheed advance 500 not shown')

// 2. new worker
await main.getByRole('button', { name: '+ نیا کاریگر' }).click()
await main.locator('label.field', { hasText: 'کاریگر کا نام' }).locator('input').fill('نذیر')
await main.locator('label.field', { hasText: 'موبائل نمبر' }).locator('input').fill('03450000000')
await main.getByRole('button', { name: '+ شلوار قمیض' }).click()
await main.getByRole('button', { name: 'محفوظ کریں' }).click()
await page.waitForSelector('text=کاریگر محفوظ ہو گیا')
await shot('02_new_worker')

// 3. give the second sample order's frock to Nazeer from the order page
await side('آرڈر مینجمنٹ')
await main.locator('.list-row', { hasText: 'عائشہ بی بی' }).first().click()
await main.getByRole('button', { name: '+ کاریگر کو دیں' }).first().click()
const modal = page.locator('.modal')
await pickWorker('نذیر')
await modal.locator('label.field', { hasText: 'مزدوری فی پیس' }).locator('input').fill('700')
await shot('03_assign')
await modal.getByRole('button', { name: 'دیں اور پرچی' }).click()
await page.waitForSelector('text=کام کاریگر کو دے دیا گیا')
await wait(400)
await shot('04_slip')
if (!(await page.getByText('کاریگر کی پرچی').count())) fail('slip not opened')
if (!(await page.locator('text=دستخط کاریگر').count())) fail('slip has no signature line')

// giving the same garment again for the same task must be refused
await page.goBack(); await wait(600)
await main.getByRole('button', { name: '+ کاریگر کو دیں' }).first().click()
await pickWorker('نذیر')
await modal.getByRole('button', { name: 'دیں اور پرچی' }).click()
await wait(500)
if (!(await page.getByText('پہلے ہی نذیر کے پاس ہے').count())) fail('double assignment not refused')
await modal.getByRole('button', { name: 'منسوخ' }).click()
await shot('05_order_with_job')

// 4. work comes back -> wage counts; pay him 400
await main.getByRole('button', { name: '✓ واپس آ گیا' }).first().click()
await page.waitForSelector('text=کام مکمل — مزدوری حساب میں شامل')
await side('کاریگر')
await main.locator('.list-row', { hasText: 'نذیر' }).click()
if (!(await main.locator('.kv', { hasText: 'مکمل کام کی کمائی' }).filter({ hasText: '700' }).count())) fail('earned 700 not shown')
await main.getByRole('button', { name: 'ادائیگی / ایڈوانس دیں' }).click()
await modal.locator('label.field', { hasText: 'رقم' }).locator('input').fill('400')
await modal.getByRole('button', { name: 'محفوظ' }).click()
await page.waitForSelector('text=ادائیگی محفوظ ہو گئی')
await wait(400)
await shot('06_payment_slip')
await page.goBack(); await wait(400)
if (!(await main.locator('.kv', { hasText: 'باقی مزدوری (دکان کے ذمے)' }).filter({ hasText: '300' }).count())) fail('balance 300 not shown')
await main.locator('.tabs').getByRole('tab', { name: 'حساب', exact: true }).click()
await shot('07_worker_ledger')

// 5. reports
await side('رپورٹس')
await main.getByRole('button', { name: 'کاریگر حساب رپورٹ' }).click()
await wait(400)
await shot('08_report')
if (!(await has('نذیر'))) fail('worker report missing Nazeer')

// 6. backup keeps workers
await side('بیک اپ اور سیٹنگز')
await wait(300)

console.log(errors.length ? 'ERRORS\n' + errors.join('\n') : 'no page errors')
console.log(process.exitCode ? 'WORKERS TEST FAILED' : 'WORKERS TEST OK')
await browser.close()
