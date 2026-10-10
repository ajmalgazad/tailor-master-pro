// Same file format as the phone app's backup (data/Backup.kt), so a phone backup can be restored on the web and vice versa.
import { COLLS, DEFAULT_SHOP } from './types'
import type { Coll } from './types'
import { store, normalize } from './store'

const KEYS: Record<Coll, string> = { customers: 'customers', members: 'members', garments: 'garments', fields: 'fields', profiles: 'profiles',
  customRates: 'customRates', rateHistory: 'rateHistory', services: 'services', orders: 'orders', items: 'items', statusLogs: 'statusLogs',
  txns: 'txns', deliveries: 'deliveries', expenses: 'expenses', printLogs: 'printLogs', audits: 'audits' }

export function exportBackup(): string {
  const out: Record<string, unknown> = { app: 'TailorMasterPro', formatVersion: 1, dbVersion: 1, exportedAt: Date.now(), settings: { ...store.shop } }
  COLLS.forEach(c => { out[KEYS[c]] = store.list(c) })
  out.logoBase64 = store.logo ? store.logo.replace(/^data:image\/\w+;base64,/, '') : ''
  return JSON.stringify(out)
}

export interface BackupCheck { ok: boolean; errors: string[]; summary: [string, number][]; data?: Record<string, unknown>; exportedAt?: number }

export function checkBackup(text: string): BackupCheck {
  let d: Record<string, unknown>
  try { d = JSON.parse(text) } catch { return { ok: false, errors: ['فائل درست بیک اپ نہیں ہے یا خراب ہے'], summary: [] } }
  const errors: string[] = []
  if (d.app !== 'TailorMasterPro') errors.push('یہ ٹیلر ماسٹر پرو کا بیک اپ نہیں ہے')
  if (Number(d.formatVersion ?? 1) > 1 || Number(d.dbVersion ?? 1) > 1) errors.push('یہ بیک اپ ایپ کے نئے ورژن کا ہے۔ پہلے ایپ اپ ڈیٹ کریں')
  const arr = (k: string) => (Array.isArray(d[k]) ? (d[k] as Record<string, unknown>[]) : [])
  const ids = (k: string) => new Set(arr(k).map(x => Number(x.id)))
  for (const [k, n] of [['customers', 'کسٹمرز'], ['members', 'فیملی ممبرز'], ['garments', 'کپڑے'], ['profiles', 'ناپ پروفائلز'], ['orders', 'آرڈرز'], ['items', 'آرڈر آئٹمز'], ['txns', 'لین دین']]) {
    if (ids(k).size !== arr(k).length) errors.push(n + ' میں ایک جیسے نمبر ملے')
  }
  const c = ids('customers'), m = ids('members'), g = ids('garments'), o = ids('orders')
  if (arr('members').some(x => !c.has(Number(x.customerId)))) errors.push('کچھ فیملی ممبرز کا کسٹمر موجود نہیں')
  if (arr('profiles').some(x => !m.has(Number(x.memberId)) || !c.has(Number(x.customerId)) || !g.has(Number(x.garmentId)))) errors.push('کچھ ناپ پروفائلز کا ریکارڈ نامکمل ہے')
  if (arr('fields').some(x => !g.has(Number(x.garmentId)))) errors.push('کچھ ناپ کے خانوں کا کپڑا موجود نہیں')
  if (arr('orders').some(x => !c.has(Number(x.customerId)))) errors.push('کچھ آرڈرز کا کسٹمر موجود نہیں')
  if (arr('items').some(x => !o.has(Number(x.orderId)))) errors.push('کچھ آرڈر آئٹمز کا آرڈر موجود نہیں')
  if (arr('txns').some(x => !c.has(Number(x.customerId)) || (x.orderId != null && !o.has(Number(x.orderId))))) errors.push('کچھ لین دین کا ریکارڈ نامکمل ہے')
  if (arr('deliveries').some(x => !o.has(Number(x.orderId)))) errors.push('کچھ ڈیلیوریاں کا آرڈر موجود نہیں')
  const summary: [string, number][] = [['کسٹمرز', arr('customers').length], ['فیملی ممبرز', arr('members').length], ['ناپ پروفائلز', arr('profiles').length],
    ['کپڑوں کی اقسام', arr('garments').length], ['آرڈرز', arr('orders').length], ['لین دین', arr('txns').length], ['اخراجات', arr('expenses').length]]
  return { ok: errors.length === 0, errors, summary, data: d, exportedAt: Number(d.exportedAt ?? 0) }
}

/** Replaces all data with the backup in one batch (also replaces the cloud copy when signed in). */
export async function restoreBackup(d: Record<string, unknown>) {
  await store.tx(t => {
    COLLS.forEach(c => t.list(c).forEach(r => t.del(c, r)))
    COLLS.forEach(c => ((d[KEYS[c]] as Record<string, unknown>[]) ?? []).forEach(r => t.put(c, normalize(c, r))))
  })
  const s = (d.settings ?? {}) as Record<string, unknown>
  const shop: Record<string, unknown> = { ...DEFAULT_SHOP }
  Object.keys(DEFAULT_SHOP).forEach(k => { if (k in s) shop[k] = s[k] })
  await store.backend!.saveShop(shop as unknown as typeof DEFAULT_SHOP)
  const logo = String(d.logoBase64 ?? '')
  await store.backend!.saveLogo(logo ? 'data:image/png;base64,' + logo : '')
}
