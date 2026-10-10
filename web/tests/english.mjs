// English mode check: adds sample data (Urdu), switches to English, opens every screen and
// reports any Urdu text still visible. Usage: node tests/english.mjs <outDir>
import { chromium } from 'playwright-core'
import fs from 'node:fs'
const out = process.argv[2] || '.'
const base = process.env.BASE || 'http://localhost:4173/'
import { existsSync } from 'node:fs'
const local = '/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell'
const exe = process.env.CHROME || (existsSync(local) ? local : undefined)
const browser = await chromium.launch(exe ? { executablePath: exe, args: ['--no-sandbox'] } : {})
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const errors = []
page.on('pageerror', e => errors.push('pageerror: ' + e.message))
page.on('dialog', d => d.accept())
const left = new Map()
const wait = ms => page.waitForTimeout(ms)
async function collect(where) {
  const texts = await page.evaluate(() => {
    const r = []
    const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT)
    while (w.nextNode()) {
      const n = w.currentNode
      const el = n.parentElement
      if (!el || el.closest('input,textarea,select,.receipt,.receipt-frame') || !el.getClientRects().length) continue
      if (/[؀-ۿ]/.test(n.nodeValue)) r.push(n.nodeValue.trim())
    }
    document.querySelectorAll('input[placeholder]').forEach(i => { if (/[؀-ۿ]/.test(i.placeholder)) r.push('placeholder: ' + i.placeholder) })
    return r
  })
  for (const t of texts) if (!left.has(t)) left.set(t, where)
}
page.setDefaultTimeout(5000)
async function closeModal() {
  for (let i = 0; i < 3 && await page.locator('.modal-bg').count(); i++) {
    await page.keyboard.press('Escape'); await wait(150)
    if (await page.locator('.modal-bg').count()) await page.locator('.modal-bg').first().click({ position: { x: 5, y: 5 }, force: true }).catch(() => {})
    await wait(150)
  }
}
const safe = async f => { try { await f() } catch (e) { errors.push('step: ' + e.message.split('\n')[0]) } finally { await closeModal() } }
const shot = async n => { await wait(350); await collect(n); await page.screenshot({ path: `${out}/en_${n}.png` }) }

await page.goto(base)
await page.waitForSelector('text=ڈیمو')
const demoLink = page.getByRole('button', { name: 'بغیر اکاؤنٹ ڈیمو آزمائیں' })
if (await demoLink.count()) await demoLink.click()
await page.waitForSelector('text=ڈیمو موڈ')
await page.locator('.side button', { hasText: 'بیک اپ اور سیٹنگز' }).click()
await page.locator('.main').getByRole('button', { name: 'نمونہ ڈیٹا شامل کریں' }).click()
await page.waitForSelector('text=نمونہ ڈیٹا شامل ہو گیا')
// switch to English
await page.locator('.side').getByRole('button', { name: 'English' }).click()
await page.waitForSelector('text=Try demo without an account')
await shot('login')
await page.getByRole('button', { name: 'Try demo without an account' }).click()
await page.waitForSelector('text=Demo mode')
const dir = await page.evaluate(() => getComputedStyle(document.body).direction)
console.log('direction', dir)

const sideCount = await page.locator('.side > button').count()
for (let i = 0; i < sideCount; i++) {
  const id = String(i).padStart(2, '0')
  await closeModal()
  const b = page.locator('.side > button').nth(i)
  const name = (await b.innerText()).trim()
  await b.click(); await shot(`side${id}_${name.replace(/\W+/g, '_')}`)
  const rows = '.main .list-row:not(.static) .link-area, .main button.list-row, .main .list-row:not(.static)'
  await safe(async () => {
    if (await page.locator(rows).count()) {
      await page.locator(rows).first().click(); await shot(`side${id}_detail`)
      await closeModal()
      if (await page.locator(rows).count()) { await page.locator(rows).first().click(); await shot(`side${id}_detail2`) }
    }
  })
  await safe(async () => {
    await page.locator('.side > button').nth(i).click(); await wait(200)
    const tabs = page.locator('.main .tabs button')
    for (let k = 1; k < Math.min(await tabs.count(), 5); k++) { await tabs.nth(k).click(); await shot(`side${id}_tab${k}`) }
  })
}
await safe(async () => {
  await page.locator('.side > button').nth(0).click()
  await page.locator('.main').getByRole('button', { name: /New booking/ }).first().click(); await shot('booking')
  await page.locator('.main').getByRole('button', { name: /Add garment/ }).first().click(); await shot('booking_add')
})

const lines = [...left.entries()].map(([t, w]) => `${w}\t${t}`)
fs.writeFileSync(`${out}/en_leftover.txt`, lines.join('\n') + '\n')
console.log('LEFTOVER', left.size)
console.log(lines.slice(0, 80).join('\n'))
if (errors.length) console.log('ERRORS\n' + errors.join('\n'))
await browser.close()
