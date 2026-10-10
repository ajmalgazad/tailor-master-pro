// Port of Repository.kt — the same business rules as the phone app.
import { Calc, Codes, Dates } from './calc'
import { store, Tx, UserError } from './store'
import { seedRecords } from './seed'
import { OrderStatus, TxnType, PayMethod } from './types'
import type { Charge, Customer, CustomRate, Delivery, Expense, ExtraService, FamilyMember, Garment, MeasureSnapshot, MeasureValue,
  MeasurementField, MeasurementProfile, Order, OrderItem, StatusLog, Txn } from './types'

export { UserError }

// ---------------------------------------------------------------- JSON helpers
export const decodeValues = (j: string): MeasureValue[] => { try { return j ? JSON.parse(j) : [] } catch { return [] } }
export const encodeValues = (v: MeasureValue[]) => JSON.stringify(v)
export const decodeCharges = (j: string): Charge[] => { try { return j ? JSON.parse(j) : [] } catch { return [] } }
export const decodeSnapshot = (j: string): MeasureSnapshot | null => { try { return j ? JSON.parse(j) : null } catch { return null } }
export const decodeIds = (j: string): number[] => { try { return JSON.parse(j) } catch { return [] } }
export const orderNo = (id: number) => Codes.code('B', id, 5)
export const txnNo = (id: number) => Codes.code('T', id, 5)

// ---------------------------------------------------------------- view types
export interface CustomerRow { customer: Customer; balance: number; members: number }
export interface DraftItem { key: number; existingItemId: number | null; garmentId: number; garmentName: string; memberId: number; memberName: string; qty: number; rate: number; profileId: number | null; existingSnapshot: string | null; existingProfileId: number | null; status: string; notes: string }
export interface OrderDraft { editingOrderId: number | null; customerId: number; memberId: number; bookingDate: number; deliveryDate: number; urgent: boolean; instructions: string; internalNotes: string; items: DraftItem[]; charges: Charge[]; discountIsPercent: boolean; discountInput: number; advance: number; advanceMethod: string; advanceRef: string; isDraft: boolean; rebookedFromId: number | null }
export interface OrderRow { order: Order; customerName: string; customerMobile: string; memberName: string; itemCount: number; pendingItems: number; paid: number; outstanding: number; overdue: boolean }
export interface OrderFull { order: Order; customer: Customer; member: FamilyMember | undefined; items: OrderItem[]; txns: Txn[]; paid: number; outstanding: number; deliveries: Delivery[]; log: StatusLog[]; customerBalance: number }
export interface LedgerEntry { date: number; description: string; debit: number; credit: number; balance: number; txn?: Txn; orderId?: number | null }
export interface Ledger { customer: Customer; entries: LedgerEntry[]; balance: number }

let keySeq = 1
export const newDraftItem = (p: Partial<DraftItem> & { garmentId: number; garmentName: string; memberId: number; memberName: string }): DraftItem => ({
  key: keySeq++, existingItemId: null, qty: 1, rate: 0, profileId: null, existingSnapshot: null, existingProfileId: null, status: OrderStatus.NEW, notes: '', ...p,
})
export const newDraft = (customerId = 0, memberId = 0): OrderDraft => ({
  editingOrderId: null, customerId, memberId, bookingDate: Date.now(), deliveryDate: Dates.addDays(Date.now(), 7), urgent: false,
  instructions: '', internalNotes: '', items: [], charges: [], discountIsPercent: false, discountInput: 0, advance: 0,
  advanceMethod: PayMethod.CASH, advanceRef: '', isDraft: false, rebookedFromId: null,
})

const now = () => Date.now()
function audit(t: Tx, entity: string, entityId: number, action: string, detail = '') {
  t.insert('audits', { at: now(), entity, entityId, action, detail })
}

// ---------------------------------------------------------------- reads
const S = store
export const Q = {
  customer: (id: number) => S.get('customers', id),
  member: (id: number) => S.get('members', id),
  garment: (id: number) => S.get('garments', id),
  garments: () => S.list('garments').sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id),
  fields: (gid: number) => S.list('fields').filter(f => f.garmentId === gid).sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id),
  services: () => S.list('services').sort((a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name)),
  members: (cid: number) => S.list('members').filter(m => m.customerId === cid).sort((a, b) => Number(b.isSelf) - Number(a.isSelf) || a.id - b.id),
  profilesForCustomer: (cid: number) => sortProfiles(S.list('profiles').filter(p => p.customerId === cid)),
  profilesForMember: (mid: number) => sortProfiles(S.list('profiles').filter(p => p.memberId === mid)),
  allProfiles: () => S.list('profiles').sort((a, b) => b.date - a.date),
  profile: (id: number) => S.get('profiles', id),
  customRates: () => S.list('customRates').sort((a, b) => b.updatedAt - a.updatedAt),
  rateHistory: () => S.list('rateHistory').sort((a, b) => b.at - a.at),
  txn: (id: number) => S.get('txns', id),
  allTxns: () => S.list('txns').sort((a, b) => b.date - a.date || b.id - a.id),
  expenses: () => S.list('expenses').sort((a, b) => b.date - a.date || b.id - a.id),
  printLogs: () => S.list('printLogs').sort((a, b) => b.at - a.at).slice(0, 200),
  audits: () => S.list('audits').sort((a, b) => b.at - a.at).slice(0, 500),
  deliveries: () => S.list('deliveries').sort((a, b) => b.date - a.date),
  delivery: (id: number) => S.get('deliveries', id),
  order: (id: number) => S.get('orders', id),

  balances(): Map<number, number> {
    const orders = new Map<number, number>(); const txns = new Map<number, number>()
    S.list('orders').forEach(o => { if (!o.isDraft && o.status !== OrderStatus.CANCELLED) orders.set(o.customerId, (orders.get(o.customerId) ?? 0) + o.netAmount) })
    S.list('txns').forEach(t => txns.set(t.customerId, (txns.get(t.customerId) ?? 0) + t.signedEffect))
    const m = new Map<number, number>()
    S.list('customers').forEach(c => m.set(c.id, Calc.customerBalance(c.openingBalance, [orders.get(c.id) ?? 0], [txns.get(c.id) ?? 0])))
    return m
  },
  balance: (cid: number) => Q.balances().get(cid) ?? 0,

  customerRows(includeInactive: boolean): CustomerRow[] {
    const bal = Q.balances()
    const mc = new Map<number, number>()
    S.list('members').forEach(m => { if (m.active) mc.set(m.customerId, (mc.get(m.customerId) ?? 0) + 1) })
    return S.list('customers').filter(c => c.mergedIntoId == null && (includeInactive || c.active))
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(c => ({ customer: c, balance: bal.get(c.id) ?? c.openingBalance, members: mc.get(c.id) ?? 0 }))
  },

  findDuplicates(c: Customer): Customer[] {
    const out = new Map<number, Customer>()
    S.list('customers').forEach(x => {
      if (x.mergedIntoId != null || x.id === c.id) return
      const mob = (v: string) => v !== '' && (x.mobile === v || x.whatsapp === v)
      if (mob(c.mobile.trim()) || mob(c.whatsapp.trim()) || (x.name === c.name.trim() && x.fatherName === c.fatherName.trim())) out.set(x.id, x)
    })
    return Array.from(out.values())
  },

  membersWithCustomers(): [FamilyMember, Customer][] {
    const cs = new Map(S.list('customers').filter(c => c.mergedIntoId == null).map(c => [c.id, c]))
    return S.list('members').flatMap(m => { const c = cs.get(m.customerId); return c ? [[m, c] as [FamilyMember, Customer]] : [] })
  },

  resolveRate(garmentId: number, customerId: number, memberId: number): number {
    const rates = Q.customRates().filter(r => r.active && r.garmentId === garmentId)
    return rates.find(r => r.memberId === memberId && memberId !== 0)?.rate
      ?? rates.find(r => r.customerId === customerId && r.memberId == null && customerId !== 0)?.rate
      ?? Q.garment(garmentId)?.defaultRate ?? 0
  },

  profileHistory(p: MeasurementProfile): MeasurementProfile[] {
    const out: MeasurementProfile[] = []; let prev = p.previousId; let guard = 0
    while (prev != null && guard++ < 200) { const x = Q.profile(prev); if (!x) break; out.push(x); prev = x.previousId }
    return out
  },

  orderRows(): OrderRow[] {
    const customers = new Map(S.list('customers').map(c => [c.id, c]))
    const members = new Map(S.list('members').map(m => [m.id, m]))
    const items = new Map<number, OrderItem[]>()
    S.list('items').forEach(i => { const l = items.get(i.orderId) ?? []; l.push(i); items.set(i.orderId, l) })
    const paidBy = new Map<number, number>()
    S.list('txns').forEach(t => { if (t.orderId != null) paidBy.set(t.orderId, (paidBy.get(t.orderId) ?? 0) - t.signedEffect) })
    const today = Dates.today()
    return S.list('orders').sort((a, b) => b.id - a.id).map(o => {
      const its = items.get(o.id) ?? []
      const paid = paidBy.get(o.id) ?? 0
      const net = o.status === OrderStatus.CANCELLED || o.isDraft ? 0 : o.netAmount
      const c = customers.get(o.customerId)
      return {
        order: o, customerName: c?.name ?? '-', customerMobile: c?.mobile ?? '', memberName: members.get(o.memberId)?.name ?? '-',
        itemCount: its.reduce((s, i) => s + i.qty, 0), pendingItems: its.filter(i => !i.delivered).reduce((s, i) => s + i.qty, 0),
        paid, outstanding: net - paid,
        overdue: !o.isDraft && o.status !== OrderStatus.DELIVERED && o.status !== OrderStatus.CANCELLED && o.deliveryDate < today,
      }
    })
  },

  orderFull(orderId: number): OrderFull {
    const o = Q.order(orderId); if (!o) throw new UserError('آرڈر نہیں ملا')
    const c = Q.customer(o.customerId); if (!c) throw new UserError('کسٹمر نہیں ملا')
    const txns = S.list('txns').filter(t => t.orderId === orderId).sort((a, b) => a.date - b.date || a.id - b.id)
    const paid = -txns.reduce((s, t) => s + t.signedEffect, 0)
    const net = o.status === OrderStatus.CANCELLED || o.isDraft ? 0 : o.netAmount
    return {
      order: o, customer: c, member: Q.member(o.memberId),
      items: S.list('items').filter(i => i.orderId === orderId).sort((a, b) => a.id - b.id),
      txns, paid, outstanding: Calc.outstanding(net, paid),
      deliveries: S.list('deliveries').filter(d => d.orderId === orderId).sort((a, b) => a.date - b.date),
      log: S.list('statusLogs').filter(l => l.orderId === orderId).sort((a, b) => b.at - a.at),
      customerBalance: Q.balance(c.id),
    }
  },

  ledger(customerId: number): Ledger {
    const c = Q.customer(customerId); if (!c) throw new UserError('کسٹمر نہیں ملا')
    const raws: { date: number; ord: number; desc: string; effect: number; txn?: Txn; orderId?: number | null }[] = []
    S.list('orders').filter(o => o.customerId === customerId && !o.isDraft).forEach(o => {
      const cancelled = o.status === OrderStatus.CANCELLED
      const disc = o.discountAmount > 0 ? ` (رعایت ${Math.floor(o.discountAmount / 100)})` : ''
      raws.push({ date: o.bookingDate, ord: 1, desc: `بکنگ ${orderNo(o.id)}` + disc + (cancelled ? ' — منسوخ' : ''), effect: cancelled ? 0 : o.netAmount, orderId: o.id })
    })
    S.list('txns').filter(t => t.customerId === customerId).forEach(t => {
      const label = TxnType.label(t.type) + ` ${txnNo(t.id)}` + (t.orderId != null ? ` / ${orderNo(t.orderId)}` : '') +
        (t.reversedById != null ? ' (منسوخ شدہ)' : '') + (t.notes.trim() ? ` — ${t.notes}` : '')
      raws.push({ date: t.date, ord: 2, desc: label, effect: t.signedEffect, txn: t, orderId: t.orderId })
    })
    let bal = c.openingBalance
    const entries: LedgerEntry[] = [{ date: c.regDate, description: 'ابتدائی بیلنس', debit: Math.max(c.openingBalance, 0), credit: Math.max(-c.openingBalance, 0), balance: bal }]
    raws.sort((a, b) => a.date - b.date || a.ord - b.ord).forEach(r => {
      bal += r.effect
      entries.push({ date: r.date, description: r.desc, debit: r.effect > 0 ? r.effect : 0, credit: r.effect < 0 ? -r.effect : 0, balance: bal, txn: r.txn, orderId: r.orderId })
    })
    return { customer: c, entries, balance: bal }
  },

  balanceAfterTxn(txnId: number): number {
    const t = Q.txn(txnId); if (!t) throw new UserError('اندراج نہیں ملا')
    const l = Q.ledger(t.customerId)
    const e = [...l.entries].reverse().find(x => x.txn?.id === txnId)
    return e ? e.balance : l.balance
  },

  dashboard(day: number) {
    const from = Dates.startOfDay(day), to = Dates.endOfDay(day)
    const mFrom = Dates.startOfMonth(day), mTo = Dates.endOfMonth(day)
    const rows = Q.orderRows().filter(r => !r.order.isDraft)
    const cashTx = S.list('txns').filter(t => t.cash)
    const bal = Q.balances()
    const active = S.list('customers').filter(c => c.mergedIntoId == null && c.active)
    const activeIds = new Set(active.map(c => c.id))
    const dueLimit = Dates.endOfDay(Dates.addDays(day, S.shop.dueSoonDays))
    const notClosed = (o: Order) => o.status !== OrderStatus.DELIVERED && o.status !== OrderStatus.CANCELLED
    return {
      todayBookings: rows.filter(r => r.order.bookingDate >= from && r.order.bookingDate <= to).length,
      todayDeliveries: rows.filter(r => r.order.deliveryDate >= from && r.order.deliveryDate <= to && r.order.status !== OrderStatus.CANCELLED).length,
      openOrders: rows.filter(r => OrderStatus.open.includes(r.order.status)).length,
      readyOrders: rows.filter(r => r.order.status === OrderStatus.READY).length,
      overdueOrders: rows.filter(r => notClosed(r.order) && r.order.deliveryDate < from).length,
      todayCollection: -cashTx.filter(t => t.date >= from && t.date <= to).reduce((s, t) => s + t.signedEffect, 0),
      monthCollection: -cashTx.filter(t => t.date >= mFrom && t.date <= mTo).reduce((s, t) => s + t.signedEffect, 0),
      totalOutstanding: Array.from(bal.entries()).filter(([id, v]) => activeIds.has(id) && v > 0).reduce((s, [, v]) => s + v, 0),
      totalCustomers: active.length,
      todayExpenses: S.list('expenses').filter(e => !e.voided && e.date >= from && e.date <= to).reduce((s, e) => s + e.amount, 0),
      alerts: rows.filter(r => notClosed(r.order) && r.order.deliveryDate <= dueLimit).sort((a, b) => a.order.deliveryDate - b.order.deliveryDate),
    }
  },
}

function sortProfiles(l: MeasurementProfile[]) { return l.sort((a, b) => Number(a.archived) - Number(b.archived) || b.date - a.date) }

// ---------------------------------------------------------------- writes
export const R = {
  async seedIfNeeded() {
    if (S.list('garments').length > 0) return
    const s = seedRecords()
    await S.tx(t => {
      s.garments.forEach(g => t.put('garments', g)); s.fields.forEach(f => t.put('fields', f))
      s.rateHistory.forEach(h => t.put('rateHistory', h)); s.services.forEach(x => t.put('services', x))
    })
  },

  async saveCustomer(c: Customer): Promise<number> {
    if (!c.name.trim()) throw new UserError('کسٹمر کا نام لازمی ہے')
    if (c.openingBalance < 0) throw new UserError('ابتدائی بیلنس منفی نہیں ہو سکتا')
    const clean = { ...c, name: c.name.trim(), mobile: c.mobile.trim(), whatsapp: c.whatsapp.trim() }
    return S.tx(t => {
      if (!clean.id) {
        const { id: _omit, ...rest } = clean
        const x = t.insert('customers', rest)
        t.insert('members', { customerId: x.id, name: x.name, relation: 'ذاتی استعمال', ageGroup: '', mobile: x.mobile, fittingPref: '', notes: '', isSelf: true, active: true })
        audit(t, 'customer', x.id, 'create', x.name)
        return x.id
      }
      const old = t.get('customers', clean.id)
      t.put('customers', clean)
      if (old && old.openingBalance !== clean.openingBalance) audit(t, 'customer', clean.id, 'opening_balance', `${old.openingBalance} -> ${clean.openingBalance}`)
      audit(t, 'customer', clean.id, 'update', clean.name)
      return clean.id
    })
  },

  setCustomerActive: (id: number, active: boolean) => S.tx(t => {
    const c = t.get('customers', id); if (c) t.put('customers', { ...c, active }); audit(t, 'customer', id, active ? 'activate' : 'deactivate')
  }),

  async mergeCustomers(fromId: number, toId: number) {
    if (fromId === toId) throw new UserError('ایک ہی کسٹمر کو آپس میں ضم نہیں کیا جا سکتا')
    await S.tx(t => {
      const from = t.get('customers', fromId), to = t.get('customers', toId)
      if (!from || !to) throw new UserError('کسٹمر نہیں ملا')
      t.list('members').filter(m => m.customerId === fromId).forEach(m => t.put('members', { ...m, customerId: toId, ...(m.isSelf ? { isSelf: false, relation: 'ضم شدہ: ' + from.name } : {}) }))
      t.list('profiles').filter(p => p.customerId === fromId).forEach(p => t.put('profiles', { ...p, customerId: toId }))
      t.list('orders').filter(o => o.customerId === fromId).forEach(o => t.put('orders', { ...o, customerId: toId }))
      t.list('txns').filter(x => x.customerId === fromId).forEach(x => t.put('txns', { ...x, customerId: toId }))
      t.list('customRates').filter(r => r.customerId === fromId).forEach(r => t.put('customRates', { ...r, customerId: toId }))
      t.put('customers', { ...to, openingBalance: to.openingBalance + from.openingBalance, notes: (to.notes + '\nضم شدہ ریکارڈ: ' + from.name + ' (' + from.mobile + ')').trim() })
      t.put('customers', { ...from, active: false, mergedIntoId: toId, openingBalance: 0 })
      audit(t, 'customer', toId, 'merge', `from ${fromId} (${from.name})`)
    })
  },

  async saveMember(m: FamilyMember): Promise<number> {
    if (!m.name.trim()) throw new UserError('فرد کا نام لازمی ہے')
    return S.tx(t => {
      if (!m.id) { const { id: _o, ...rest } = m; const x = t.insert('members', { ...rest, name: m.name.trim() }); audit(t, 'member', x.id, 'create', m.name); return x.id }
      t.put('members', m); audit(t, 'member', m.id, 'update', m.name); return m.id
    })
  },

  async saveGarment(g: Garment): Promise<number> {
    if (!g.name.trim()) throw new UserError('کپڑے کا نام لازمی ہے')
    if (g.defaultRate < 0) throw new UserError('ریٹ منفی نہیں ہو سکتا')
    return S.tx(t => {
      if (!g.id) {
        const sort = Math.max(0, ...t.list('garments').map(x => x.sortOrder)) + 1
        const { id: _o, ...rest } = g
        const x = t.insert('garments', { ...rest, sortOrder: sort })
        t.insert('rateHistory', { garmentId: x.id, customerId: null, memberId: null, oldRate: 0, newRate: g.defaultRate, at: now(), note: 'نیا کپڑا' })
        return x.id
      }
      const old = t.get('garments', g.id)
      t.put('garments', g)
      if (old && old.defaultRate !== g.defaultRate) t.insert('rateHistory', { garmentId: g.id, customerId: null, memberId: null, oldRate: old.defaultRate, newRate: g.defaultRate, at: now(), note: 'ڈیفالٹ ریٹ' })
      return g.id
    })
  },

  moveGarment: (id: number, up: boolean) => S.tx(t => {
    const list = t.list('garments').sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    const i = list.findIndex(g => g.id === id), j = up ? i - 1 : i + 1
    if (i >= 0 && j >= 0 && j < list.length) { [list[i], list[j]] = [list[j], list[i]]; list.forEach((g, idx) => { if (g.sortOrder !== idx) t.put('garments', { ...g, sortOrder: idx }) }) }
  }),

  async saveField(f: MeasurementField): Promise<number> {
    if (!f.label.trim()) throw new UserError('ناپ کا نام لازمی ہے')
    return S.tx(t => {
      if (!f.id) { const sort = Math.max(0, ...t.list('fields').filter(x => x.garmentId === f.garmentId).map(x => x.sortOrder)) + 1; const { id: _o, ...rest } = f; return t.insert('fields', { ...rest, sortOrder: sort }).id }
      t.put('fields', f); return f.id
    })
  },

  moveField: (garmentId: number, id: number, up: boolean) => S.tx(t => {
    const list = t.list('fields').filter(f => f.garmentId === garmentId).sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    const i = list.findIndex(f => f.id === id), j = up ? i - 1 : i + 1
    if (i >= 0 && j >= 0 && j < list.length) { [list[i], list[j]] = [list[j], list[i]]; list.forEach((f, idx) => { if (f.sortOrder !== idx) t.put('fields', { ...f, sortOrder: idx }) }) }
  }),

  async saveService(s: ExtraService): Promise<number> {
    if (!s.name.trim()) throw new UserError('سروس کا نام لازمی ہے')
    if (s.amount < 0) throw new UserError('رقم منفی نہیں ہو سکتی')
    return S.tx(t => { if (!s.id) { const { id: _o, ...rest } = s; return t.insert('services', rest).id } t.put('services', s); return s.id })
  },

  async saveCustomRate(r: CustomRate): Promise<number> {
    if (r.rate < 0) throw new UserError('ریٹ منفی نہیں ہو سکتا')
    if (r.customerId == null && r.memberId == null) throw new UserError('کسٹمر منتخب کریں')
    return S.tx(t => {
      if (!r.id) {
        const { id: _o, ...rest } = r
        const x = t.insert('customRates', { ...rest, updatedAt: now() })
        t.insert('rateHistory', { garmentId: r.garmentId, customerId: r.customerId, memberId: r.memberId, oldRate: 0, newRate: r.rate, at: now(), note: 'خصوصی ریٹ' })
        return x.id
      }
      const old = t.get('customRates', r.id)
      t.put('customRates', { ...r, updatedAt: now() })
      if (old && (old.rate !== r.rate || old.active !== r.active)) t.insert('rateHistory', { garmentId: r.garmentId, customerId: r.customerId, memberId: r.memberId, oldRate: old.rate, newRate: r.rate, at: now(), note: r.active ? 'خصوصی ریٹ' : 'خصوصی ریٹ غیر فعال' })
      return r.id
    })
  },

  // ---------------- measurement profiles
  checkRequired(t: Tx, p: Omit<MeasurementProfile, 'id'>) {
    const req = t.list('fields').filter(f => f.garmentId === p.garmentId && f.active && f.required)
    const vals = decodeValues(p.valuesJson)
    const missing = req.filter(f => !vals.some(v => v.fieldId === f.id && v.value.trim()))
    if (missing.length) throw new UserError('یہ ناپ لازمی ہیں: ' + missing.map(f => f.label).join('، '))
  },

  async saveNewProfile(p: Omit<MeasurementProfile, 'id'>): Promise<number> {
    if (!p.name.trim()) throw new UserError('پروفائل کا نام لازمی ہے')
    return S.tx(t => {
      R.checkRequired(t, p)
      const siblings = t.list('profiles').filter(x => x.memberId === p.memberId && x.garmentId === p.garmentId)
      const hasDefault = siblings.some(x => x.isDefault && !x.archived)
      const makeDefault = p.isDefault || !hasDefault
      if (makeDefault) siblings.filter(x => x.isDefault).forEach(x => t.put('profiles', { ...x, isDefault: false }))
      const x = t.insert('profiles', { ...p, isDefault: makeDefault, archived: false })
      audit(t, 'profile', x.id, 'create', p.name)
      return x.id
    })
  },

  async saveProfileVersion(old: MeasurementProfile, updated: Omit<MeasurementProfile, 'id'>): Promise<number> {
    if (!updated.name.trim()) throw new UserError('پروفائل کا نام لازمی ہے')
    return S.tx(t => {
      R.checkRequired(t, updated)
      t.put('profiles', { ...old, archived: true, isDefault: false })
      const x = t.insert('profiles', { ...updated, previousId: old.id, archived: false, isDefault: old.isDefault || updated.isDefault, date: now() })
      audit(t, 'profile', x.id, 'new_version', `previous ${old.id}`)
      return x.id
    })
  },

  duplicateProfile: (p: MeasurementProfile, newName: string, memberId: number) => S.tx(t => {
    const { id: _o, ...rest } = p
    const x = t.insert('profiles', { ...rest, name: newName, memberId, isDefault: false, archived: false, previousId: null, date: now(), lastUsed: null })
    audit(t, 'profile', x.id, 'duplicate', `from ${p.id}`)
    return x.id
  }),

  setDefaultProfile: (p: MeasurementProfile) => S.tx(t => {
    t.list('profiles').filter(x => x.memberId === p.memberId && x.garmentId === p.garmentId && x.isDefault).forEach(x => t.put('profiles', { ...x, isDefault: false }))
    t.put('profiles', { ...p, isDefault: true })
  }),

  archiveProfile: (p: MeasurementProfile, archived: boolean) => S.tx(t => t.put('profiles', { ...p, archived, isDefault: archived ? false : p.isDefault })),

  // ---------------- orders
  buildDraftFromOrder(orderId: number, forRebook: boolean): OrderDraft {
    const o = Q.order(orderId); if (!o) throw new UserError('آرڈر نہیں ملا')
    const items = S.list('items').filter(i => i.orderId === orderId).sort((a, b) => a.id - b.id)
    return {
      editingOrderId: forRebook ? null : o.id, customerId: o.customerId, memberId: o.memberId,
      bookingDate: forRebook ? Date.now() : o.bookingDate, deliveryDate: forRebook ? Dates.addDays(Date.now(), 7) : o.deliveryDate,
      urgent: o.urgent, instructions: o.instructions, internalNotes: forRebook ? '' : o.internalNotes,
      items: items.map(it => newDraftItem({
        existingItemId: forRebook ? null : it.id, garmentId: it.garmentId, garmentName: it.garmentName, memberId: it.memberId, memberName: it.memberName,
        qty: it.qty, rate: it.rate, profileId: it.profileId, existingSnapshot: forRebook ? null : it.measurementJson,
        existingProfileId: forRebook ? null : it.profileId, status: forRebook ? OrderStatus.NEW : it.status, notes: it.notes,
      })),
      charges: decodeCharges(o.chargesJson), discountIsPercent: o.discountType === 'PERCENT', discountInput: o.discountInput,
      advance: 0, advanceMethod: PayMethod.CASH, advanceRef: '', isDraft: forRebook ? false : o.isDraft, rebookedFromId: forRebook ? o.id : o.rebookedFromId,
    }
  },

  totalsOf: (d: OrderDraft) => Calc.totals(d.items.map(i => ({ qty: i.qty, rate: i.rate })), d.charges.map(c => c.amount), d.discountIsPercent, d.discountInput),

  async saveOrder(d: OrderDraft): Promise<number> {
    if (!d.customerId) throw new UserError('کسٹمر منتخب کریں')
    if (!d.memberId) throw new UserError('فیملی ممبر منتخب کریں')
    const err = Calc.validate(d.items.map(i => ({ qty: i.qty, rate: i.rate })), d.charges.map(c => c.amount), d.discountIsPercent, d.discountInput)
    if (err) throw new UserError(err)
    if (d.advance < 0) throw new UserError('ایڈوانس منفی نہیں ہو سکتا')
    if (d.deliveryDate < Dates.startOfDay(d.bookingDate)) throw new UserError('ڈیلیوری کی تاریخ بکنگ سے پہلے نہیں ہو سکتی')
    const tot = R.totalsOf(d)
    if (d.advance > tot.net) throw new UserError('ایڈوانس کل رقم سے زیادہ ہے')
    const ts = now()
    return S.tx(t => {
      let orderId: number
      const common = {
        customerId: d.customerId, memberId: d.memberId, bookingDate: d.bookingDate, deliveryDate: d.deliveryDate, urgent: d.urgent,
        instructions: d.instructions, internalNotes: d.internalNotes, isDraft: d.isDraft, chargesJson: JSON.stringify(d.charges),
        discountType: d.discountIsPercent ? 'PERCENT' : 'AMOUNT', discountInput: d.discountInput, itemsTotal: tot.itemsTotal,
        chargesTotal: tot.chargesTotal, discountAmount: tot.discount, netAmount: tot.net, updatedAt: ts,
      }
      if (d.editingOrderId == null) {
        const o = t.insert('orders', { ...common, status: OrderStatus.NEW, createdAt: ts, rebookedFromId: d.rebookedFromId })
        orderId = o.id
        t.insert('statusLogs', { orderId, itemId: null, fromStatus: '', toStatus: OrderStatus.NEW, at: ts, note: d.isDraft ? 'ڈرافٹ بنایا' : d.rebookedFromId != null ? 'دوبارہ بکنگ' : 'بکنگ' })
        audit(t, 'order', orderId, 'create', `net ${tot.net}`)
      } else {
        const old = t.get('orders', d.editingOrderId); if (!old) throw new UserError('آرڈر نہیں ملا')
        if (old.status === OrderStatus.CANCELLED) throw new UserError('منسوخ آرڈر میں ترمیم نہیں ہو سکتی')
        const oldItems = t.list('items').filter(i => i.orderId === old.id)
        if (oldItems.some(i => i.delivered)) throw new UserError('جس آرڈر کے کپڑے ڈیلیور ہو چکے ہوں اس میں ترمیم نہیں ہو سکتی')
        if (!d.isDraft) {
          const paid = -t.list('txns').filter(x => x.orderId === old.id).reduce((s, x) => s + x.signedEffect, 0)
          if (tot.net < paid) throw new UserError('نئی کل رقم وصول شدہ رقم سے کم ہے۔ پہلے ریفنڈ درج کریں')
        }
        orderId = old.id
        t.put('orders', { ...old, ...common })
        oldItems.forEach(i => t.del('items', i))
        t.insert('statusLogs', { orderId, itemId: null, fromStatus: old.status, toStatus: old.status, at: ts,
          note: `ترمیم: پرانی رقم ${Math.floor(old.netAmount / 100)} → نئی رقم ${Math.floor(tot.net / 100)}` + (old.isDraft && !d.isDraft ? ' (ڈرافٹ کنفرم)' : '') })
        audit(t, 'order', orderId, 'edit', `old net ${old.netAmount}, new net ${tot.net}`)
      }
      for (const it of d.items) {
        const keep = it.existingSnapshot != null && it.existingProfileId === it.profileId
        let snap = ''
        if (keep) snap = it.existingSnapshot!
        else if (it.profileId != null) {
          const p = t.get('profiles', it.profileId)
          if (p) snap = JSON.stringify({ profileId: p.id, profileName: p.name, unit: p.unit, date: p.date, fitting: p.fitting, instructions: p.instructions, values: decodeValues(p.valuesJson) })
        }
        t.insert('items', { orderId, garmentId: it.garmentId, garmentName: it.garmentName, memberId: it.memberId, memberName: it.memberName, qty: it.qty, rate: it.rate,
          total: Calc.lineTotal(it.qty, it.rate), profileId: it.profileId, measurementJson: snap, status: it.status, delivered: false, deliveredAt: null, deliveryId: null, notes: it.notes })
        if (it.profileId != null) { const p = t.get('profiles', it.profileId); if (p) t.put('profiles', { ...p, lastUsed: ts }) }
      }
      if (d.advance > 0) {
        const x = t.insert('txns', { customerId: d.customerId, orderId, date: ts, type: TxnType.PAYMENT, amount: d.advance, signedEffect: TxnType.effect(TxnType.PAYMENT, d.advance),
          cash: true, method: d.advanceMethod, reference: d.advanceRef, notes: 'ایڈوانس', reversedById: null, reversesId: null, createdAt: ts })
        audit(t, 'txn', x.id, 'create', `advance ${d.advance}`)
      }
      return orderId
    })
  },

  setOrderStatus: (orderId: number, status: string, applyToItems: boolean, note = '') => S.tx(t => {
    const o = t.get('orders', orderId); if (!o) throw new UserError('آرڈر نہیں ملا')
    if (o.isDraft) throw new UserError('پہلے ڈرافٹ کو کنفرم کریں')
    if (o.status === OrderStatus.CANCELLED) throw new UserError('منسوخ آرڈر کا اسٹیٹس تبدیل نہیں ہو سکتا')
    if (status === OrderStatus.DELIVERED) throw new UserError('ڈیلیوری کے لیے ڈیلیوری اسکرین استعمال کریں')
    if (status === OrderStatus.CANCELLED) throw new UserError('منسوخی کے لیے منسوخ بٹن استعمال کریں')
    const ts = now()
    t.put('orders', { ...o, status, updatedAt: ts })
    t.insert('statusLogs', { orderId, itemId: null, fromStatus: o.status, toStatus: status, at: ts, note })
    if (applyToItems) t.list('items').filter(i => i.orderId === orderId && !i.delivered && i.status !== status).forEach(i => {
      t.put('items', { ...i, status }); t.insert('statusLogs', { orderId, itemId: i.id, fromStatus: i.status, toStatus: status, at: ts, note: '' })
    })
  }),

  setItemStatus: (itemId: number, status: string) => S.tx(t => {
    const it = t.get('items', itemId); if (!it) throw new UserError('آئٹم نہیں ملا')
    if (it.delivered) throw new UserError('یہ کپڑا ڈیلیور ہو چکا ہے')
    if (status === OrderStatus.DELIVERED || status === OrderStatus.CANCELLED) throw new UserError('یہ اسٹیٹس یہاں سے نہیں لگ سکتا')
    const ts = now()
    t.put('items', { ...it, status })
    t.insert('statusLogs', { orderId: it.orderId, itemId: it.id, fromStatus: it.status, toStatus: status, at: ts, note: '' })
    const o = t.get('orders', it.orderId)
    const pending = t.list('items').filter(x => x.orderId === it.orderId && !x.delivered)
    if (o && !o.isDraft && o.status !== OrderStatus.CANCELLED && pending.length && pending.every(x => x.status === status) && o.status !== status) {
      t.put('orders', { ...o, status, updatedAt: ts })
      t.insert('statusLogs', { orderId: o.id, itemId: null, fromStatus: o.status, toStatus: status, at: ts, note: 'تمام کپڑے' })
    }
  }),

  saveInternalNotes: (orderId: number, notes: string) => S.tx(t => { const o = t.get('orders', orderId); if (o) t.put('orders', { ...o, internalNotes: notes, updatedAt: now() }) }),

  cancelOrder: (orderId: number, reason: string) => S.tx(t => {
    const o = t.get('orders', orderId); if (!o) throw new UserError('آرڈر نہیں ملا')
    if (o.status === OrderStatus.CANCELLED) throw new UserError('آرڈر پہلے ہی منسوخ ہے')
    if (t.list('items').some(i => i.orderId === orderId && i.delivered)) throw new UserError('ڈیلیور شدہ آرڈر منسوخ نہیں ہو سکتا')
    t.put('orders', { ...o, status: OrderStatus.CANCELLED, updatedAt: now() })
    t.insert('statusLogs', { orderId, itemId: null, fromStatus: o.status, toStatus: OrderStatus.CANCELLED, at: now(), note: reason })
    audit(t, 'order', orderId, 'cancel', reason)
  }),

  async deliver(orderId: number, itemIds: number[], notes: string, payment: number, method: string, forceComplete: boolean): Promise<number> {
    if (payment < 0) throw new UserError('رقم منفی نہیں ہو سکتی')
    return S.tx(t => {
      const o = t.get('orders', orderId); if (!o) throw new UserError('آرڈر نہیں ملا')
      if (o.isDraft) throw new UserError('ڈرافٹ آرڈر ڈیلیور نہیں ہو سکتا')
      if (o.status === OrderStatus.CANCELLED) throw new UserError('منسوخ آرڈر ڈیلیور نہیں ہو سکتا')
      const items = t.list('items').filter(i => i.orderId === orderId)
      const selected = items.filter(i => itemIds.includes(i.id) && !i.delivered)
      const remaining = items.filter(i => !i.delivered && !itemIds.includes(i.id))
      if (!selected.length && !(forceComplete && remaining.length)) throw new UserError('ڈیلیور کرنے کے لیے کپڑے منتخب کریں')
      const ts = now()
      let txnId: number | null = null
      if (payment > 0) {
        const paid = -t.list('txns').filter(x => x.orderId === orderId).reduce((s, x) => s + x.signedEffect, 0)
        if (payment > o.netAmount - paid) throw new UserError('وصولی بقایا رقم سے زیادہ ہے')
        txnId = t.insert('txns', { customerId: o.customerId, orderId, date: ts, type: TxnType.PAYMENT, amount: payment, signedEffect: -payment, cash: true,
          method, reference: '', notes: 'ڈیلیوری پر وصولی', reversedById: null, reversesId: null, createdAt: ts }).id
      }
      const toMark = forceComplete ? [...selected, ...remaining] : selected
      const dl = t.insert('deliveries', { orderId, date: ts, itemIdsJson: JSON.stringify(toMark.map(i => i.id)), notes, paymentTxnId: txnId, forcedComplete: forceComplete && remaining.length > 0 })
      toMark.forEach(i => {
        t.put('items', { ...i, delivered: true, deliveredAt: ts, deliveryId: dl.id, status: OrderStatus.DELIVERED })
        t.insert('statusLogs', { orderId, itemId: i.id, fromStatus: i.status, toStatus: OrderStatus.DELIVERED, at: ts, note: remaining.includes(i) ? 'مالک کی تصدیق سے مکمل' : '' })
      })
      const allDone = t.list('items').filter(i => i.orderId === orderId).every(i => i.delivered)
      if (allDone && o.status !== OrderStatus.DELIVERED) {
        t.put('orders', { ...o, status: OrderStatus.DELIVERED, updatedAt: ts })
        t.insert('statusLogs', { orderId, itemId: null, fromStatus: o.status, toStatus: OrderStatus.DELIVERED, at: ts, note: forceComplete ? 'مالک کی تصدیق سے مکمل ڈیلیوری' : 'مکمل ڈیلیوری' })
      } else if (!allDone) {
        t.insert('statusLogs', { orderId, itemId: null, fromStatus: o.status, toStatus: o.status, at: ts, note: 'جزوی ڈیلیوری' })
      }
      audit(t, 'delivery', dl.id, 'create', `order ${orderId} items ${toMark.length}`)
      return dl.id
    })
  },

  // ---------------- money
  async recordTxn(customerId: number, orderId: number | null, type: string, amount: number, method: string, reference: string, notes: string, date: number): Promise<number> {
    if (!(amount > 0)) throw new UserError('رقم صفر سے زیادہ ہونی چاہیے')
    if (type === TxnType.REVERSAL) throw new UserError('غلط قسم')
    return S.tx(t => {
      if (!t.get('customers', customerId)) throw new UserError('کسٹمر نہیں ملا')
      if (orderId != null) { const o = t.get('orders', orderId); if (!o) throw new UserError('آرڈر نہیں ملا'); if (o.customerId !== customerId) throw new UserError('یہ آرڈر اس کسٹمر کا نہیں') }
      const x = t.insert('txns', { customerId, orderId, date, type, amount, signedEffect: TxnType.effect(type, amount), cash: TxnType.isCash(type), method, reference, notes, reversedById: null, reversesId: null, createdAt: now() })
      audit(t, 'txn', x.id, 'create', `${type} ${amount}`)
      return x.id
    })
  },

  reverseTxn: (txnId: number, reason: string) => S.tx(t => {
    const x = t.get('txns', txnId); if (!x) throw new UserError('اندراج نہیں ملا')
    if (x.type === TxnType.REVERSAL) throw new UserError('ریورسل کو دوبارہ ریورس نہیں کیا جا سکتا')
    if (x.reversedById != null) throw new UserError('یہ اندراج پہلے ہی منسوخ ہو چکا ہے')
    const r = t.insert('txns', { customerId: x.customerId, orderId: x.orderId, date: now(), type: TxnType.REVERSAL, amount: x.amount, signedEffect: -x.signedEffect,
      cash: x.cash, method: x.method, reference: txnNo(x.id), notes: reason, reversedById: null, reversesId: x.id, createdAt: now() })
    t.put('txns', { ...x, reversedById: r.id })
    audit(t, 'txn', x.id, 'reverse', reason)
    return r.id
  }),

  async saveExpense(e: Expense): Promise<number> {
    if (!(e.amount > 0)) throw new UserError('رقم صفر سے زیادہ ہونی چاہیے')
    if (!e.category.trim()) throw new UserError('قسم منتخب کریں')
    return S.tx(t => {
      if (!e.id) { const { id: _o, ...rest } = e; const x = t.insert('expenses', rest); audit(t, 'expense', x.id, 'create', `${e.category} ${e.amount}`); return x.id }
      t.put('expenses', e); audit(t, 'expense', e.id, 'update', `${e.category} ${e.amount}`); return e.id
    })
  },

  voidExpense: (e: Expense, reason: string) => S.tx(t => { t.put('expenses', { ...e, voided: true, notes: (e.notes + ` [منسوخ: ${reason}]`).trim() }); audit(t, 'expense', e.id, 'void', reason) }),

  logPrint: (kind: string, refId: number, title: string, status: string, message: string) => S.tx(t => { t.insert('printLogs', { kind, refId, title, at: now(), status, message }) }),
}
