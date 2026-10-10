// End-to-end sync test against the Firebase emulator (CI).
//   node tests/cloud.mjs setup  <file>  -> creates a shop account on the web, adds sample data, writes credentials to <file>
//   node tests/cloud.mjs verify <file>  -> logs in again on the web and checks that the payment made by the phone test is visible
import { chromium } from 'playwright-core'
import fs from 'node:fs'
const [mode, file] = process.argv.slice(2)
const APP = process.env.APP_URL || 'http://localhost:4173/?emulator=localhost'
const exe = process.env.CHROME_PATH
const browser = await chromium.launch(exe ? { executablePath: exe, args: ['--no-sandbox'] } : {})
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const errors = []
page.on('pageerror', e => errors.push(e.message))
const fail = async m => { console.error('FAIL: ' + m); await page.screenshot({ path: `web-${mode}-fail.png` }); await browser.close(); process.exit(1) }
const main = () => page.locator('.main')

async function login(email, password) {
  await page.goto(APP)
  await page.getByLabel('ای میل').fill(email)
  await page.getByLabel('پاس ورڈ').fill(password)
  await page.getByRole('button', { name: 'لاگ اِن' }).click()
}

if (mode === 'setup') {
  const email = `web${Date.now()}@test.pk`, password = 'secret123'
  const r = await fetch('http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, password, returnSecureToken: true }) })
  const { localId: uid } = await r.json()
  await login(email, password)
  await page.getByText('اس اکاؤنٹ میں ابھی کوئی دکان نہیں').waitFor({ timeout: 20000 }).catch(() => fail('needs-shop screen not shown'))
  await page.getByRole('button', { name: 'فون کے بغیر نئی دکان یہاں سے شروع کریں' }).click()
  await page.getByRole('button', { name: 'نئی دکان شروع کریں' }).click()
  await page.locator('.side').waitFor({ timeout: 20000 }).catch(() => fail('app did not open'))
  await page.waitForTimeout(1500)
  await page.locator('.side button', { hasText: 'بیک اپ اور سیٹنگز' }).click()
  await main().getByRole('button', { name: 'نمونہ ڈیٹا شامل کریں' }).click()
  await page.getByText('نمونہ ڈیٹا شامل ہو گیا').waitFor({ timeout: 20000 }).catch(() => fail('sample data not added'))
  await page.waitForTimeout(3000)
  // Check the cloud really has the records (emulator admin access)
  const q = await fetch(`http://localhost:8080/v1/projects/demo-tailor/databases/(default)/documents/shops/${uid}/customers`, { headers: { Authorization: 'Bearer owner' } })
  const docs = (await q.json()).documents ?? []
  if (docs.length !== 2) await fail('expected 2 customers in Firestore, got ' + docs.length)
  const g = await fetch(`http://localhost:8080/v1/projects/demo-tailor/databases/(default)/documents/shops/${uid}/garments?pageSize=100`, { headers: { Authorization: 'Bearer owner' } })
  const gd = (await g.json()).documents ?? []
  if (gd.length !== 26) await fail('expected 26 seeded garments, got ' + gd.length)
  fs.writeFileSync(file, JSON.stringify({ email, password, uid }))
  console.log('WEB SETUP OK', uid)
} else {
  const { email, password } = JSON.parse(fs.readFileSync(file, 'utf8'))
  await login(email, password)
  await page.locator('.side').waitFor({ timeout: 30000 }).catch(() => fail('app did not open on second login'))
  await page.waitForTimeout(2000)
  await page.locator('.side button', { hasText: 'کسٹمر ماسٹر' }).click()
  await main().getByText('محمد اسلم (نمونہ)').first().click()
  await main().getByRole('button', { name: 'کھاتہ' }).click()
  await page.getByText('از فون').first().waitFor({ timeout: 30000 }).catch(() => fail("phone's payment not visible on the web"))
  const bal = await main().locator('.kv', { hasText: 'موجودہ بقایا' }).first().innerText()
  console.log('WEB VERIFY OK', bal.replace(/\n/g, ' '))
  if (!bal.includes('5,900')) await fail('balance after phone payment should be 5,900, got ' + bal)
}
if (errors.length) console.log('PAGE ERRORS', errors)
await browser.close()
