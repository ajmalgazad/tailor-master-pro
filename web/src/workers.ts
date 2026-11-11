// Port of data/Workers.kt — kaarigar (workers): jobs, per-piece wages, payments and advances.
// Balance = wages of finished work − payments (not cancelled). Positive = shop owes the worker.
import { store, Tx, UserError } from './store'
import { JobStatus, OrderStatus, WorkerPayType } from './types'
import type { WorkJob, Worker, WorkerPayment } from './types'
import { orderNo } from './repo'

const S = store
const now = () => Date.now()
const sum = (a: number[]) => a.reduce((s, x) => s + x, 0)
function audit(t: Tx, id: number, action: string, detail = '') {
  t.insert('audits', { at: now(), entity: 'worker', entityId: id, action, detail })
}

export interface WorkerRow { worker: Worker; earned: number; paid: number; balance: number; pendingJobs: number; pendingAmount: number }
export interface WorkerLedgerEntry { date: number; description: string; earned: number; paid: number; balance: number; payment?: WorkerPayment; job?: WorkJob }
export interface WorkerLedger { worker: Worker; entries: WorkerLedgerEntry[]; balance: number }

export const WorkerCalc = {
  earned: (jobs: WorkJob[]) => sum(jobs.filter(j => j.status === JobStatus.DONE).map(j => j.amount)),
  pendingAmount: (jobs: WorkJob[]) => sum(jobs.filter(j => j.status === JobStatus.ASSIGNED).map(j => j.amount)),
  paid: (pays: WorkerPayment[]) => sum(pays.filter(p => !p.voided).map(p => p.amount)),
  balance: (jobs: WorkJob[], pays: WorkerPayment[]) => WorkerCalc.earned(jobs) - WorkerCalc.paid(pays),
}

const byDesc = (a: WorkJob, b: WorkJob) => b.assignedAt - a.assignedAt || b.id - a.id

/** Reads */
export const WQ = {
  worker: (id: number) => S.get('workers', id),
  workers: () => S.list('workers').sort((a, b) => a.name.localeCompare(b.name)),
  job: (id: number) => S.get('workJobs', id),
  jobs: () => S.list('workJobs').sort(byDesc),
  jobsForWorker: (id: number) => S.list('workJobs').filter(j => j.workerId === id).sort(byDesc),
  jobsForOrder: (orderId: number) => S.list('workJobs').filter(j => j.orderId === orderId).sort((a, b) => a.id - b.id),
  payment: (id: number) => S.get('workerPayments', id),
  payments: () => S.list('workerPayments').sort((a, b) => b.date - a.date || b.id - a.id),

  rows(includeInactive: boolean): WorkerRow[] {
    const jobs = S.list('workJobs'), pays = S.list('workerPayments')
    return WQ.workers().filter(w => includeInactive || w.active).map(w => {
      const j = jobs.filter(x => x.workerId === w.id), p = pays.filter(x => x.workerId === w.id)
      return { worker: w, earned: WorkerCalc.earned(j), paid: WorkerCalc.paid(p), balance: WorkerCalc.balance(j, p),
        pendingJobs: j.filter(x => x.status === JobStatus.ASSIGNED).length, pendingAmount: WorkerCalc.pendingAmount(j) }
    })
  },

  /** Wage this worker got last time for the same garment and task (to fill the form). */
  lastWage(workerId: number, garmentName: string, task: string): number | null {
    const all = WQ.jobs().filter(j => j.status !== JobStatus.CANCELLED && j.garmentName === garmentName)
    return (all.find(j => j.workerId === workerId && j.task === task) ?? all.find(j => j.task === task))?.wage ?? null
  },

  ledger(workerId: number): WorkerLedger {
    const w = WQ.worker(workerId); if (!w) throw new UserError('کاریگر نہیں ملا')
    const raws: { date: number; desc: string; earned: number; paid: number; p?: WorkerPayment; j?: WorkJob }[] = []
    WQ.jobsForWorker(workerId).filter(j => j.status === JobStatus.DONE).forEach(j =>
      raws.push({ date: j.doneAt ?? j.assignedAt, desc: `${j.task}: ${j.garmentName} × ${j.qty} / ${orderNo(j.orderId)}`, earned: j.amount, paid: 0, j }))
    S.list('workerPayments').filter(p => p.workerId === workerId).sort((a, b) => a.date - b.date || a.id - b.id).forEach(p =>
      raws.push({ date: p.date, desc: WorkerPayType.label(p.type) + (p.voided ? ' (منسوخ شدہ)' : '') + (p.notes.trim() ? ' — ' + p.notes : ''),
        earned: 0, paid: p.voided ? 0 : p.amount, p }))
    let bal = 0
    const entries = raws.sort((a, b) => a.date - b.date).map(r => { bal += r.earned - r.paid; return { date: r.date, description: r.desc, earned: r.earned, paid: r.paid, balance: bal, payment: r.p, job: r.j } })
    return { worker: w, entries, balance: bal }
  },
}

/** Writes — same rules and messages as the phone app. */
export const WR = {
  async saveWorker(w: Worker): Promise<number> {
    if (!w.name.trim()) throw new UserError('کاریگر کا نام لازمی ہے')
    const clean = { ...w, name: w.name.trim(), mobile: w.mobile.trim(), cnic: w.cnic.trim() }
    return S.tx(t => {
      if (!clean.id) { const { id: _o, ...rest } = clean; const x = t.insert('workers', { ...rest, createdAt: rest.createdAt || now() }); audit(t, x.id, 'create', clean.name); return x.id }
      t.put('workers', clean); audit(t, clean.id, 'update', clean.name); return clean.id
    })
  },

  setActive: (id: number, active: boolean) => S.tx(t => {
    const w = t.get('workers', id); if (w) t.put('workers', { ...w, active })
    audit(t, id, active ? 'activate' : 'deactivate')
  }),

  async assign(itemId: number, workerId: number, task: string, qty: number, wage: number, dueDate: number | null, notes: string): Promise<number> {
    if (!task.trim()) throw new UserError('کام کی قسم لکھیں')
    if (wage < 0) throw new UserError('مزدوری منفی نہیں ہو سکتی')
    return S.tx(t => {
      const w = t.get('workers', workerId); if (!w) throw new UserError('کاریگر منتخب کریں')
      if (!w.active) throw new UserError('یہ کاریگر غیر فعال ہے')
      const item = t.get('items', itemId); if (!item) throw new UserError('کپڑا نہیں ملا')
      const o = t.get('orders', item.orderId); if (!o) throw new UserError('آرڈر نہیں ملا')
      if (o.status === OrderStatus.CANCELLED) throw new UserError('منسوخ آرڈر کا کام نہیں دیا جا سکتا')
      if (item.delivered) throw new UserError('یہ کپڑا ڈیلیور ہو چکا ہے')
      if (!(qty >= 1 && qty <= item.qty)) throw new UserError(`تعداد 1 سے ${item.qty} تک ہو سکتی ہے`)
      const busy = t.list('workJobs').filter(j => j.itemId === itemId && j.task === task.trim() && j.status === JobStatus.ASSIGNED)
      if (sum(busy.map(j => j.qty)) + qty > item.qty) {
        const names = Array.from(new Set(busy.map(j => t.get('workers', j.workerId)?.name).filter(Boolean))).join('، ')
        throw new UserError(`یہ کپڑا اسی کام کے لیے پہلے ہی ${names} کے پاس ہے`)
      }
      const j = t.insert('workJobs', { workerId, orderId: o.id, itemId, garmentName: item.garmentName, memberName: item.memberName, task: task.trim(), qty,
        wage, amount: wage * qty, assignedAt: now(), dueDate: dueDate ?? o.deliveryDate, status: JobStatus.ASSIGNED, doneAt: null, notes: notes.trim() })
      t.insert('statusLogs', { orderId: o.id, itemId, fromStatus: item.status, toStatus: item.status, at: now(), note: `کاریگر: ${w.name} — ${task.trim()}` })
      audit(t, j.id, 'assign', `${w.name} item ${itemId} x${qty} wage ${wage}`)
      return j.id
    })
  },

  complete: (jobId: number, date = now()) => S.tx(t => {
    const j = t.get('workJobs', jobId); if (!j) throw new UserError('کام نہیں ملا')
    if (j.status !== JobStatus.ASSIGNED) throw new UserError(`یہ کام پہلے ہی ${JobStatus.label(j.status)} ہے`)
    t.put('workJobs', { ...j, status: JobStatus.DONE, doneAt: date }); audit(t, jobId, 'done', `amount ${j.amount}`)
  }),

  reopen: (jobId: number) => S.tx(t => {
    const j = t.get('workJobs', jobId); if (!j) throw new UserError('کام نہیں ملا')
    if (j.status !== JobStatus.DONE) throw new UserError('صرف مکمل کام واپس کھولا جا سکتا ہے')
    t.put('workJobs', { ...j, status: JobStatus.ASSIGNED, doneAt: null }); audit(t, jobId, 'reopen')
  }),

  cancel: (jobId: number, reason: string) => S.tx(t => {
    const j = t.get('workJobs', jobId); if (!j) throw new UserError('کام نہیں ملا')
    if (j.status === JobStatus.DONE) throw new UserError("مکمل کام منسوخ نہیں ہو سکتا۔ پہلے 'واپس کھولیں' دبائیں")
    if (j.status === JobStatus.CANCELLED) throw new UserError('یہ کام پہلے ہی منسوخ ہے')
    t.put('workJobs', { ...j, status: JobStatus.CANCELLED, notes: (j.notes + ` [منسوخ: ${reason}]`).trim() }); audit(t, jobId, 'cancel', reason)
  }),

  async pay(workerId: number, type: string, amount: number, method: string, notes: string, date: number): Promise<number> {
    if (!(amount > 0)) throw new UserError('رقم صفر سے زیادہ ہونی چاہیے')
    if (!WorkerPayType.all.includes(type)) throw new UserError('غلط قسم')
    return S.tx(t => {
      if (!t.get('workers', workerId)) throw new UserError('کاریگر نہیں ملا')
      const p = t.insert('workerPayments', { workerId, date, type, amount, method, notes: notes.trim(), voided: false, createdAt: now() })
      audit(t, workerId, 'pay', `${type} ${amount}`)
      return p.id
    })
  },

  voidPayment: (id: number, reason: string) => S.tx(t => {
    const p = t.get('workerPayments', id); if (!p) throw new UserError('اندراج نہیں ملا')
    if (p.voided) throw new UserError('یہ اندراج پہلے ہی منسوخ ہو چکا ہے')
    t.put('workerPayments', { ...p, voided: true, notes: (p.notes + ` [منسوخ: ${reason}]`).trim() }); audit(t, p.workerId, 'void_payment', `${p.id} ${reason}`)
  }),

  /** Sample workers for the demo data (flagged sample, removed with it). */
  async addSample(orderId: number) {
    const base = { id: 0, cnic: '', address: '', notes: '', active: true, sample: true, createdAt: now() }
    const w1 = await WR.saveWorker({ ...base, name: 'استاد رشید (نمونہ)', mobile: '03211234567', skills: 'شلوار قمیض، واسکٹ' })
    const w2 = await WR.saveWorker({ ...base, name: 'شکیل (نمونہ)', mobile: '03331234567', skills: 'ٹراؤزر، کڑھائی' })
    const items = S.list('items').filter(i => i.orderId === orderId).sort((a, b) => a.id - b.id)
    if (items[0]) await WR.assign(items[0].id, w1, 'سلائی', items[0].qty, 60000, null, '')
    if (items[1]) await WR.complete(await WR.assign(items[1].id, w1, 'سلائی', items[1].qty, 50000, null, ''))
    if (items[3]) await WR.assign(items[3].id, w2, 'سلائی', items[3].qty, 30000, null, '')
    await WR.pay(w1, WorkerPayType.ADVANCE, 100000, 'نقد', 'نمونہ', now())
  },

  removeSampleInTx(t: Tx, sampleOrderIds: Set<number>) {
    const ids = new Set(t.list('workers').filter(w => w.sample).map(w => w.id))
    t.list('workJobs').filter(j => ids.has(j.workerId) || sampleOrderIds.has(j.orderId)).forEach(j => t.del('workJobs', j))
    t.list('workerPayments').filter(p => ids.has(p.workerId)).forEach(p => t.del('workerPayments', p))
    t.list('workers').filter(w => ids.has(w.id)).forEach(w => t.del('workers', w))
  },
}
