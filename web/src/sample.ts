// Port of data/SampleData.kt — flagged sample records that can be removed before real use.
import { Dates } from './calc'
import { Q, R, encodeValues, newDraft, newDraftItem } from './repo'
import { store } from './store'
import type { Garment } from './types'

export async function addSample() {
  const gs = Q.garments()
  const g = (n: string): Garment => gs.find(x => x.name === n) ?? gs[0]
  const sk = g('شلوار قمیض'), wc = g('واسکٹ'), tr = g('ٹراؤزر'), fr = g('فراک')
  const base = { whatsapp: '', address: '', notes: '', active: true, mergedIntoId: null, sample: true, id: 0, regDate: Date.now(), fatherName: '', category: '', openingBalance: 0, city: 'لاہور' }
  const c1 = await R.saveCustomer({ ...base, name: 'محمد اسلم (نمونہ)', fatherName: 'عبدالرشید', mobile: '03001234567', whatsapp: '03001234567', area: 'اقبال ٹاؤن', category: 'مستقل', openingBalance: 50000 })
  const c2 = await R.saveCustomer({ ...base, name: 'عائشہ بی بی (نمونہ)', mobile: '03111234567', area: 'ماڈل ٹاؤن' })
  const self1 = Q.members(c1).find(m => m.isSelf)!, self2 = Q.members(c2).find(m => m.isSelf)!
  const mem = { ageGroup: '', mobile: '', fittingPref: '', notes: '', isSelf: false, active: true, id: 0 }
  const son = await R.saveMember({ ...mem, customerId: c1, name: 'علی', relation: 'بیٹا', ageGroup: 'بچہ (۱۰ سال)' })
  const father = await R.saveMember({ ...mem, customerId: c1, name: 'عبدالرشید', relation: 'والد', fittingPref: 'کھلی فٹنگ' })
  const prof = (cid: number, mid: number, gar: Garment, name: string, b: number) => R.saveNewProfile({ customerId: cid, memberId: mid, garmentId: gar.id, name, date: Date.now(), unit: 'انچ',
    valuesJson: encodeValues(Q.fields(gar.id).map((f, i) => ({ fieldId: f.id, label: f.label, value: (b + i * 0.5).toFixed(1) }))), fitting: 'عام', instructions: '', notes: '', isDefault: true, archived: false, previousId: null, lastUsed: null })
  const p1 = await prof(c1, self1.id, sk, 'شلوار قمیض — عام', 14), p2 = await prof(c1, self1.id, wc, 'واسکٹ', 16)
  const p3 = await prof(c1, son, sk, 'علی کا سوٹ', 9), p4 = await prof(c1, father, tr, 'ٹراؤزر', 18), p5 = await prof(c2, self2.id, fr, 'فراک', 13)
  const now = Date.now()
  const o1 = await R.saveOrder({ ...newDraft(c1, self1.id), deliveryDate: Dates.addDays(now, 5), items: [
    newDraftItem({ garmentId: sk.id, garmentName: sk.name, memberId: self1.id, memberName: self1.name, qty: 2, rate: sk.defaultRate, profileId: p1 }),
    newDraftItem({ garmentId: wc.id, garmentName: wc.name, memberId: self1.id, memberName: self1.name, qty: 1, rate: wc.defaultRate, profileId: p2 }),
    newDraftItem({ garmentId: sk.id, garmentName: sk.name, memberId: son, memberName: 'علی', qty: 1, rate: 90000, profileId: p3 }),
    newDraftItem({ garmentId: tr.id, garmentName: tr.name, memberId: father, memberName: 'عبدالرشید', qty: 1, rate: tr.defaultRate, profileId: p4 })],
    charges: [{ name: 'کڑھائی', amount: 50000 }], discountInput: 200, advance: 200000 })
  await R.setOrderStatus(o1, 'STITCHING', true, 'نمونہ')
  await R.saveOrder({ ...newDraft(c2, self2.id), bookingDate: Dates.addDays(now, -10), deliveryDate: Dates.addDays(now, -2), urgent: true,
    items: [newDraftItem({ garmentId: fr.id, garmentName: fr.name, memberId: self2.id, memberName: self2.name, qty: 1, rate: fr.defaultRate, profileId: p5 })],
    charges: [{ name: 'ارجنٹ سلائی', amount: 50000 }], discountIsPercent: true, discountInput: 5, advance: 100000 })
  const ex = { id: 0, date: now, method: 'نقد', notes: 'نمونہ', voided: false, sample: true }
  await R.saveExpense({ ...ex, category: 'بجلی کا بل', amount: 450000 })
  await R.saveExpense({ ...ex, category: 'دھاگہ اور دیگر سامان', amount: 80000 })
}

export async function removeSample() {
  await store.tx(t => {
    const ids = new Set(t.list('customers').filter(c => c.sample).map(c => c.id))
    const orderIds = new Set(t.list('orders').filter(o => ids.has(o.customerId)).map(o => o.id))
    t.list('items').filter(x => orderIds.has(x.orderId)).forEach(x => t.del('items', x))
    t.list('statusLogs').filter(x => orderIds.has(x.orderId)).forEach(x => t.del('statusLogs', x))
    t.list('deliveries').filter(x => orderIds.has(x.orderId)).forEach(x => t.del('deliveries', x))
    t.list('orders').filter(x => orderIds.has(x.id)).forEach(x => t.del('orders', x))
    t.list('txns').filter(x => ids.has(x.customerId)).forEach(x => t.del('txns', x))
    t.list('profiles').filter(x => ids.has(x.customerId)).forEach(x => t.del('profiles', x))
    t.list('customRates').filter(x => x.customerId != null && ids.has(x.customerId)).forEach(x => t.del('customRates', x))
    t.list('members').filter(x => ids.has(x.customerId)).forEach(x => t.del('members', x))
    t.list('customers').filter(x => ids.has(x.id)).forEach(x => t.del('customers', x))
    t.list('expenses').filter(x => x.sample).forEach(x => t.del('expenses', x))
  })
}
