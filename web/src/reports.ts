import { tr } from './i18n'
// Port of reports/Reports.kt
import { Calc, Dates, DAY } from './calc'
import { Fmt } from './fmt'
import { Q, decodeValues } from './repo'
import type { OrderRow } from './repo'
import { store } from './store'
import { OrderStatus, TxnType } from './types'

export interface ReportTable { title: string; subtitle: string; headers: string[]; rows: string[][]; totals?: string[]; csvRows?: string[][]; csvTotals?: string[] }
export interface ReportFilter { from: number; to: number; customerId: number | null; garmentId: number | null; status: string | null }

export const REPORTS = [
  { key: 'DAILY_BOOKING', title: 'روزانہ بکنگ رپورٹ', dates: true, customer: true, garment: true, status: true },
  { key: 'MONTHLY_BOOKING', title: 'ماہانہ بکنگ رپورٹ', dates: true, customer: true, garment: true, status: true },
  { key: 'CUSTOMER_HISTORY', title: 'کسٹمر ہسٹری رپورٹ', dates: true, customer: true, garment: false, status: false },
  { key: 'MEMBER_MEASUREMENTS', title: 'فیملی ممبر ناپ رپورٹ', dates: false, customer: true, garment: true, status: false },
  { key: 'GARMENT_WISE', title: 'کپڑوں کے لحاظ سے آرڈر رپورٹ', dates: true, customer: true, garment: true, status: true },
  { key: 'PENDING', title: 'زیر التوا آرڈرز رپورٹ', dates: false, customer: true, garment: true, status: true },
  { key: 'OVERDUE', title: 'تاخیر والی ڈیلیوری رپورٹ', dates: false, customer: true, garment: true, status: false },
  { key: 'DAILY_COLLECTION', title: 'روزانہ وصولی رپورٹ', dates: true, customer: true, garment: false, status: false },
  { key: 'OUTSTANDING', title: 'کسٹمر بقایا رپورٹ', dates: false, customer: true, garment: false, status: false },
  { key: 'EXPENSES', title: 'اخراجات رپورٹ', dates: true, customer: false, garment: false, status: false },
  { key: 'INCOME_EXPENSE', title: 'آمدن اور اخراجات کا خلاصہ', dates: true, customer: false, garment: false, status: false },
  { key: 'RATE_HISTORY', title: 'ریٹ ہسٹری رپورٹ', dates: true, customer: true, garment: true, status: false },
] as const
export type ReportKey = typeof REPORTS[number]['key']

export function defaultFilter(k: ReportKey): ReportFilter {
  const n = Date.now()
  if (k === 'DAILY_BOOKING' || k === 'DAILY_COLLECTION') return { from: Dates.today(), to: Dates.endOfDay(n), customerId: null, garmentId: null, status: null }
  if (k === 'RATE_HISTORY' || k === 'CUSTOMER_HISTORY') return { from: 0, to: Dates.endOfDay(n), customerId: null, garmentId: null, status: null }
  return { from: Dates.startOfMonth(n), to: Dates.endOfDay(n), customerId: null, garmentId: null, status: null }
}

const m = (p: number) => Fmt.moneyPlain(p)
const n = (p: number) => Calc.moneyInput(p)
const iso = (ms: number, withTime = false) => Dates.format(ms, withTime ? 'yyyy-MM-dd HH:mm' : 'yyyy-MM-dd')

export function buildReport(k: ReportKey, f: ReportFilter): ReportTable {
  const title = REPORTS.find(r => r.key === k)!.title
  const customers = new Map(store.list('customers').map(c => [c.id, c]))
  const members = new Map(store.list('members').map(x => [x.id, x]))
  const garments = new Map(store.list('garments').map(g => [g.id, g]))
  const range = `${Fmt.date(f.from)} تا ${Fmt.date(f.to)}`
  const filterText = [
    f.customerId != null ? 'کسٹمر: ' + (customers.get(f.customerId)?.name ?? '-') : '',
    f.garmentId != null ? 'کپڑا: ' + (garments.get(f.garmentId)?.name ?? '-') : '',
    f.status ? 'اسٹیٹس: ' + OrderStatus.label(f.status) : '',
  ].filter(Boolean).join(' • ')
  const sub = (withRange: boolean) => [withRange ? range : '', filterText].filter(Boolean).join(' • ')
  const allItems = store.list('items')
  const byOrder = new Map<number, typeof allItems>()
  allItems.forEach(i => { const l = byOrder.get(i.orderId) ?? []; l.push(i); byOrder.set(i.orderId, l) })
  const rows = Q.orderRows().filter(r => !r.order.isDraft)
  const matches = (r: OrderRow) => (f.customerId == null || r.order.customerId === f.customerId) && (f.status == null || r.order.status === f.status) &&
    (f.garmentId == null || (byOrder.get(r.order.id) ?? []).some(i => i.garmentId === f.garmentId))
  const inRange = (ms: number) => ms >= f.from && ms <= f.to
  const sum = (a: number[]) => a.reduce((s, x) => s + x, 0)

  switch (k) {
    case 'DAILY_BOOKING': case 'MONTHLY_BOOKING': {
      const list = rows.filter(r => inRange(r.order.bookingDate) && matches(r)).sort((a, b) => a.order.bookingDate - b.order.bookingDate)
      const net = list.filter(r => r.order.status !== OrderStatus.CANCELLED)
      return { title, subtitle: sub(true), headers: ['آرڈر نمبر', 'تاریخ', 'کسٹمر', 'ممبر', 'کپڑے', 'کل رقم', 'وصول', 'بقایا', 'اسٹیٹس'],
        rows: list.map(r => [Fmt.orderNo(r.order.id), Fmt.date(r.order.bookingDate), r.customerName, r.memberName, Fmt.num(r.itemCount), m(r.order.netAmount), m(r.paid), m(r.outstanding), OrderStatus.label(r.order.status)]),
        totals: ['کل: ' + Fmt.num(list.length), '', '', '', Fmt.num(sum(net.map(r => r.itemCount))), m(sum(net.map(r => r.order.netAmount))), m(sum(list.map(r => r.paid))), m(sum(list.map(r => r.outstanding))), ''],
        csvRows: list.map(r => ['B-' + r.order.id, iso(r.order.bookingDate), r.customerName, r.memberName, String(r.itemCount), n(r.order.netAmount), n(r.paid), n(r.outstanding), OrderStatus.label(r.order.status)]),
        csvTotals: ['TOTAL', '', '', '', String(sum(net.map(r => r.itemCount))), n(sum(net.map(r => r.order.netAmount))), n(sum(list.map(r => r.paid))), n(sum(list.map(r => r.outstanding))), ''] }
    }
    case 'CUSTOMER_HISTORY': {
      if (f.customerId == null) return { title, subtitle: 'براہ کرم کسٹمر منتخب کریں', headers: ['پیغام'], rows: [['کسٹمر منتخب نہیں کیا گیا']] }
      const l = Q.ledger(f.customerId)
      const list = l.entries.filter(e => inRange(e.date) || e.description === 'ابتدائی بیلنس')
      return { title: title + ' — ' + l.customer.name, subtitle: sub(true), headers: ['تاریخ', 'تفصیل', 'بنام (واجب)', 'جمع (وصول)', 'بیلنس'],
        rows: list.map(e => [Fmt.date(e.date), e.description, m(e.debit), m(e.credit), m(e.balance)]), totals: ['', 'موجودہ بیلنس', '', '', m(l.balance)],
        csvRows: list.map(e => [iso(e.date), e.description, n(e.debit), n(e.credit), n(e.balance)]), csvTotals: ['', 'BALANCE', '', '', n(l.balance)] }
    }
    case 'MEMBER_MEASUREMENTS': {
      const list = store.list('profiles').filter(p => !p.archived && (f.customerId == null || p.customerId === f.customerId) && (f.garmentId == null || p.garmentId === f.garmentId))
        .sort((a, b) => (customers.get(a.customerId)?.name ?? '').localeCompare(customers.get(b.customerId)?.name ?? '') || (members.get(a.memberId)?.name ?? '').localeCompare(members.get(b.memberId)?.name ?? ''))
      return { title, subtitle: sub(false), headers: ['کسٹمر', 'ممبر', 'کپڑا', 'پروفائل', 'تاریخ', 'اکائی', 'ناپ'],
        rows: list.map(p => [customers.get(p.customerId)?.name ?? '-', members.get(p.memberId)?.name ?? '-', garments.get(p.garmentId)?.name ?? '-', p.name + (p.isDefault ? ' ★' : ''), Fmt.date(p.date), p.unit,
          decodeValues(p.valuesJson).filter(v => v.value.trim()).map(v => `${v.label}: ${Fmt.d(v.value)}`).join('، ')]) }
    }
    case 'GARMENT_WISE': {
      const ids = new Set(rows.filter(r => inRange(r.order.bookingDate) && r.order.status !== OrderStatus.CANCELLED && (f.customerId == null || r.order.customerId === f.customerId) && (f.status == null || r.order.status === f.status)).map(r => r.order.id))
      const g = new Map<string, [number, number]>()
      allItems.filter(i => ids.has(i.orderId) && (f.garmentId == null || i.garmentId === f.garmentId)).forEach(i => { const v = g.get(i.garmentName) ?? [0, 0]; g.set(i.garmentName, [v[0] + i.qty, v[1] + i.total]) })
      const list = Array.from(g.entries()).sort((a, b) => b[1][0] - a[1][0])
      return { title, subtitle: sub(true), headers: ['کپڑا', 'تعداد', 'رقم'], rows: list.map(([k2, v]) => [k2, Fmt.num(v[0]), m(v[1])]),
        totals: ['کل', Fmt.num(sum(list.map(x => x[1][0]))), m(sum(list.map(x => x[1][1])))],
        csvRows: list.map(([k2, v]) => [k2, String(v[0]), n(v[1])]), csvTotals: ['TOTAL', String(sum(list.map(x => x[1][0]))), n(sum(list.map(x => x[1][1])))] }
    }
    case 'PENDING': case 'OVERDUE': {
      const today = Dates.today()
      const list = rows.filter(r => r.order.status !== OrderStatus.DELIVERED && r.order.status !== OrderStatus.CANCELLED && matches(r) && (k === 'PENDING' || r.order.deliveryDate < today))
        .sort((a, b) => a.order.deliveryDate - b.order.deliveryDate)
      return { title, subtitle: sub(false), headers: ['آرڈر نمبر', 'کسٹمر', 'موبائل', 'ممبر', 'ڈیلیوری تاریخ', 'تاخیر (دن)', 'باقی کپڑے', 'بقایا', 'اسٹیٹس'],
        rows: list.map(r => [Fmt.orderNo(r.order.id) + (r.order.urgent ? ' ⚡' : ''), r.customerName, Fmt.d(r.customerMobile), r.memberName, Fmt.date(r.order.deliveryDate),
          Fmt.num(Math.max(0, Math.floor((today - Dates.startOfDay(r.order.deliveryDate)) / DAY))), Fmt.num(r.pendingItems), m(r.outstanding), OrderStatus.label(r.order.status)]),
        totals: ['کل: ' + Fmt.num(list.length), '', '', '', '', '', Fmt.num(sum(list.map(r => r.pendingItems))), m(sum(list.map(r => r.outstanding))), ''] }
    }
    case 'DAILY_COLLECTION': {
      const list = store.list('txns').filter(t => t.cash && inRange(t.date) && (f.customerId == null || t.customerId === f.customerId)).sort((a, b) => a.date - b.date)
      const byMethod = new Map<string, number>(); list.forEach(t => byMethod.set(t.method, (byMethod.get(t.method) ?? 0) - t.signedEffect))
      return { title, subtitle: [sub(true), Array.from(byMethod.entries()).map(([k2, v]) => `${k2}: ${m(v)}`).join(' • ')].filter(Boolean).join('\n'),
        headers: ['تاریخ', 'ٹرانزیکشن', 'کسٹمر', 'آرڈر', 'قسم', 'طریقہ', 'رقم'],
        rows: list.map(t => [Fmt.dateTime(t.date), Fmt.txnNo(t.id), customers.get(t.customerId)?.name ?? '-', t.orderId != null ? Fmt.orderNo(t.orderId) : '-', TxnType.label(t.type), t.method, m(-t.signedEffect)]),
        totals: ['کل وصولی', '', '', '', '', '', m(-sum(list.map(t => t.signedEffect)))],
        csvRows: list.map(t => [iso(t.date, true), 'T-' + t.id, customers.get(t.customerId)?.name ?? '-', t.orderId != null ? 'B-' + t.orderId : '', TxnType.label(t.type), t.method, n(-t.signedEffect)]),
        csvTotals: ['TOTAL', '', '', '', '', '', n(-sum(list.map(t => t.signedEffect)))] }
    }
    case 'OUTSTANDING': {
      const bal = Q.balances()
      const list = Array.from(customers.values()).filter(c => c.mergedIntoId == null && (bal.get(c.id) ?? 0) !== 0 && (f.customerId == null || c.id === f.customerId)).sort((a, b) => (bal.get(b.id) ?? 0) - (bal.get(a.id) ?? 0))
      const pos = sum(list.map(c => Math.max(0, bal.get(c.id) ?? 0)))
      return { title, subtitle: 'مثبت = کسٹمر کے ذمے، منفی = کسٹمر کی پیشگی رقم', headers: ['کوڈ', 'کسٹمر', 'موبائل', 'علاقہ', 'بقایا'],
        rows: list.map(c => [Fmt.customerCode(c.id), c.name, Fmt.d(c.mobile), [c.area, c.city].filter(Boolean).join('، '), m(bal.get(c.id) ?? 0)]),
        totals: ['کل بقایا', '', '', '', m(pos)], csvRows: list.map(c => ['C-' + c.id, c.name, c.mobile, c.area, n(bal.get(c.id) ?? 0)]), csvTotals: ['TOTAL', '', '', '', n(pos)] }
    }
    case 'EXPENSES': {
      const list = store.list('expenses').filter(e => !e.voided && inRange(e.date)).sort((a, b) => a.date - b.date)
      const byCat = new Map<string, number>(); list.forEach(e => byCat.set(e.category, (byCat.get(e.category) ?? 0) + e.amount))
      return { title, subtitle: [sub(true), Array.from(byCat.entries()).map(([k2, v]) => `${k2}: ${m(v)}`).join(' • ')].filter(Boolean).join('\n'),
        headers: ['تاریخ', 'قسم', 'طریقہ', 'تفصیل', 'رقم'], rows: list.map(e => [Fmt.date(e.date), e.category, e.method, e.notes, m(e.amount)]),
        totals: ['کل اخراجات', '', '', '', m(sum(list.map(e => e.amount)))], csvRows: list.map(e => [iso(e.date), e.category, e.method, e.notes, n(e.amount)]), csvTotals: ['TOTAL', '', '', '', n(sum(list.map(e => e.amount)))] }
    }
    case 'INCOME_EXPENSE': {
      const revenue = sum(rows.filter(r => inRange(r.order.bookingDate) && r.order.status !== OrderStatus.CANCELLED).map(r => r.order.netAmount))
      const cash = -sum(store.list('txns').filter(t => t.cash && inRange(t.date)).map(t => t.signedEffect))
      const exp = sum(store.list('expenses').filter(e => !e.voided && inRange(e.date)).map(e => e.amount))
      const out = sum(Array.from(Q.balances().values()).filter(v => v > 0))
      const lines: [string, number][] = [['آرڈرز کی آمدن (بک شدہ کام)', revenue], ['نقد / رقم وصول ہوئی', cash], ['کاروباری اخراجات', exp], ['خالص نقد (وصولی − اخراجات)', cash - exp], ['کل بقایا (آج تک، تمام کسٹمرز)', out]]
      return { title, subtitle: sub(true), headers: ['مد', 'رقم'], rows: lines.map(([a, b]) => [a, m(b)]), csvRows: lines.map(([a, b]) => [a, n(b)]) }
    }
    case 'RATE_HISTORY': {
      const list = store.list('rateHistory').filter(h => inRange(h.at) && (f.garmentId == null || h.garmentId === f.garmentId) && (f.customerId == null || h.customerId === f.customerId)).sort((a, b) => b.at - a.at)
      const who = (h: typeof list[number]) => [h.customerId != null ? customers.get(h.customerId)?.name : undefined, h.memberId != null ? members.get(h.memberId)?.name : undefined].filter(Boolean).join(' / ') || 'ڈیفالٹ'
      return { title, subtitle: sub(true), headers: ['تاریخ', 'کپڑا', 'کسٹمر / ممبر', 'پرانا ریٹ', 'نیا ریٹ', 'نوٹ'],
        rows: list.map(h => [Fmt.dateTime(h.at), garments.get(h.garmentId)?.name ?? '-', who(h), m(h.oldRate), m(h.newRate), h.note]),
        csvRows: list.map(h => [iso(h.at, true), garments.get(h.garmentId)?.name ?? '-', who(h), n(h.oldRate), n(h.newRate), h.note]) }
    }
  }
}

export function toCsv(t: ReportTable): string {
  const esc = (x: string) => { const s = tr(x); return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s }
  const lines = [esc(t.title)]
  if (t.subtitle) lines.push(esc(t.subtitle.replace(/\n/g, ' ')))
  lines.push(t.headers.map(esc).join(','))
  ;(t.csvRows ?? t.rows).forEach(r => lines.push(r.map(esc).join(',')))
  const tot = t.csvTotals ?? t.totals
  if (tot) lines.push(tot.map(esc).join(','))
  return '﻿' + lines.join('\n') + '\n'
}
