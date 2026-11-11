// Kaarigar (workers) screens — same as ui/screens/WorkerScreens.kt on the phone.
import { useEffect, useState } from 'react'
import { Calc, Codes, Digits } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, UserError } from '../../repo'
import { JobStatus, OrderStatus, PayMethod, ReceiptKind, WorkTasks, WorkerPayType } from '../../types'
import type { OrderItem, WorkJob, Worker } from '../../types'
import { WQ, WR } from '../../workers'
import { act, msg, Btn, Chip, Confirm, DateInput, Empty, Input, KV, Modal, MoneyInput, Page, Prompt, Row, SearchBox, Section, Select, Switch, Tabs, useNav, useStoreVersion } from '../kit'

const jobTone = (s: string) => (s === JobStatus.DONE ? 'ok' : s === JobStatus.CANCELLED ? 'muted' : 'warn')
const balanceText = (b: number) => (b > 0 ? 'باقی مزدوری: ' + Fmt.money(b) : b < 0 ? 'ایڈوانس (کاریگر کے ذمے): ' + Fmt.money(-b) : 'حساب برابر')
const balanceTone = (b: number) => (b > 0 ? 'bad' : b < 0 ? 'warn' : 'ok')

export function Workers() {
  useStoreVersion()
  const nav = useNav()
  const [q, setQ] = useState('')
  const [inactive, setInactive] = useState(false)
  const rows = WQ.rows(inactive)
  const t = Digits.toLatin(q.trim()).toLowerCase()
  const list = rows.filter(r => !t || r.worker.name.toLowerCase().includes(t) || r.worker.mobile.includes(t) || r.worker.skills.includes(t) ||
    Codes.code('K', r.worker.id, 3).toLowerCase().includes(t))
  const sum = (a: number[]) => a.reduce((s, x) => s + x, 0)
  return <Page title="کاریگر" actions={<Btn small kind="ok" onClick={() => nav.go({ name: 'workerEdit' })}>+ نیا کاریگر</Btn>}>
    <SearchBox value={q} onChange={setQ} placeholder="نام، موبائل یا کام" />
    <Switch label="غیر فعال کاریگر بھی دکھائیں" checked={inactive} onChange={setInactive} />
    <Section title="خلاصہ">
      <KV k="کاریگروں کے پاس کام" v={Fmt.num(sum(rows.map(r => r.pendingJobs)))} />
      <KV k="کل باقی مزدوری (دکان کے ذمے)" v={Fmt.money(sum(rows.map(r => Math.max(0, r.balance))))} bold tone="bad" />
      <KV k="کل ایڈوانس (کاریگروں کے ذمے)" v={Fmt.money(sum(rows.map(r => Math.max(0, -r.balance))))} />
      <Row><Btn small kind="ghost" onClick={() => nav.go({ name: 'report', key: 'WORKERS' })}>کاریگر حساب رپورٹ</Btn>
        <Btn small kind="ghost" onClick={() => nav.go({ name: 'report', key: 'WORKER_JOBS' })}>دیا گیا کام</Btn></Row>
    </Section>
    <div className="list">{list.map(r => <button key={r.worker.id} className={'list-row' + (r.worker.active ? '' : ' dim')} onClick={() => nav.go({ name: 'worker', id: r.worker.id })}>
      <div><b>{r.worker.name}{r.worker.active ? '' : ' (غیر فعال)'}</b>
        <small>{Fmt.workerCode(r.worker.id)}{r.worker.mobile ? ' • ' + Fmt.d(r.worker.mobile) : ''}{r.worker.skills ? ' • ' + r.worker.skills : ''}</small>
        <small className={balanceTone(r.balance)}>{balanceText(r.balance)}</small></div>
      <div className="end">{r.pendingJobs > 0 && <Chip text={'پاس کام: ' + Fmt.num(r.pendingJobs)} tone="warn" />}</div></button>)}
      {list.length === 0 && <Empty text="کوئی کاریگر نہیں۔ 'نیا کاریگر' دبا کر شامل کریں" />}</div>
  </Page>
}

const blankWorker = (): Worker => ({ id: 0, name: '', mobile: '', cnic: '', skills: '', address: '', notes: '', active: true, sample: false, createdAt: Date.now() })

export function WorkerEdit({ id }: { id?: number }) {
  useStoreVersion()
  const nav = useNav()
  const existing = id ? WQ.worker(id) : undefined
  const [w, setW] = useState<Worker>(() => existing ?? blankWorker())
  const set = (p: Partial<Worker>) => setW(x => ({ ...x, ...p }))
  const chips = Array.from(new Set([...Q.garments().filter(g => g.active).map(g => g.name), ...WorkTasks]))
  const parts = w.skills.split(/[،,]/).map(s => s.trim()).filter(Boolean)
  const toggle = (c: string) => set({ skills: (parts.includes(c) ? parts.filter(p => p !== c) : [...parts, c]).join('، ') })
  return <Page title={id ? 'کاریگر میں ترمیم' : 'نیا کاریگر'}>
    <Section>
      {id ? <p className="small muted">کاریگر کوڈ: {Fmt.workerCode(id)}</p> : null}
      <Input label="کاریگر کا نام *" value={w.name} onChange={v => set({ name: v })} />
      <Input label="موبائل نمبر" value={w.mobile} onChange={v => set({ mobile: v })} inputMode="tel" />
      <Input label="شناختی کارڈ نمبر (CNIC)" value={w.cnic} onChange={v => set({ cnic: v })} inputMode="numeric" />
      <Input label="کیا بناتا ہے (کام کی قسم)" value={w.skills} onChange={v => set({ skills: v })} />
      <p className="small">جلدی شامل کریں:</p>
      <Row>{chips.map(c => <Btn key={c} small kind={parts.includes(c) ? 'primary' : 'ghost'} onClick={() => toggle(c)}>{(parts.includes(c) ? '✓ ' : '+ ') + c}</Btn>)}</Row>
      <Input label="پتہ" value={w.address} onChange={v => set({ address: v })} />
      <Input label="نوٹس" value={w.notes} onChange={v => set({ notes: v })} multiline />
      <Switch label="فعال" checked={w.active} onChange={v => set({ active: v })} />
    </Section>
    <Btn wide kind="ok" onClick={() => act(async () => {
      const newId = await WR.saveWorker(w)
      if (!id) nav.replace({ name: 'worker', id: newId }); else nav.back()
    }, 'کاریگر محفوظ ہو گیا')}>محفوظ کریں</Btn>
  </Page>
}

export function WorkerDetail({ id, tab: initialTab }: { id: number; tab?: number }) {
  useStoreVersion()
  const nav = useNav()
  const [tab, setTab] = useState(initialTab ?? 0)
  const [pay, setPay] = useState(false)
  const [cancelJob, setCancelJob] = useState<WorkJob | null>(null)
  const [voidPay, setVoidPay] = useState<number | null>(null)
  const [deact, setDeact] = useState(false)
  const w = WQ.worker(id)
  if (!w) return <Page title="کاریگر"><Empty text="کاریگر نہیں ملا" /></Page>
  const lg = WQ.ledger(id)
  const all = WQ.jobsForWorker(id)
  const pending = all.filter(j => j.status === JobStatus.ASSIGNED)
  const sum = (a: number[]) => a.reduce((s, x) => s + x, 0)
  return <Page title={w.name} actions={<Btn small kind="ghost" onClick={() => nav.go({ name: 'workerEdit', id })}>ترمیم</Btn>}>
    <div className="two-col">
      <div className="col">
        <Section>
          <KV k="کوڈ" v={Fmt.workerCode(w.id)} />
          {w.mobile && <KV k="موبائل" v={Fmt.d(w.mobile)} />}
          {w.cnic && <KV k="شناختی کارڈ" v={Fmt.d(w.cnic)} />}
          {w.skills && <KV k="کام" v={w.skills} />}
          {w.address && <KV k="پتہ" v={w.address} />}
          {w.notes && <p className="small">نوٹس: {w.notes}</p>}
          {!w.active && <Chip text="غیر فعال" tone="bad" />}
        </Section>
        <Section title="حساب">
          <KV k="مکمل کام کی کمائی" v={Fmt.money(sum(lg.entries.map(e => e.earned)))} />
          <KV k="ادائیگی + ایڈوانس" v={Fmt.money(sum(lg.entries.map(e => e.paid)))} />
          <KV k="ابھی پاس کام کی مزدوری" v={Fmt.money(sum(pending.map(j => j.amount)))} />
          <KV k={lg.balance >= 0 ? 'باقی مزدوری (دکان کے ذمے)' : 'ایڈوانس (کاریگر کے ذمے)'} v={Fmt.money(Math.abs(lg.balance))} bold tone={balanceTone(lg.balance) as 'ok' | 'bad' | 'warn'} />
          <Row>
            <Btn kind="ok" disabled={!w.active} onClick={() => setPay(true)}>ادائیگی / ایڈوانس دیں</Btn>
            <Btn kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.WORKER_LEDGER, refId: id })}>حساب پرنٹ</Btn>
            <Btn kind="ghost" onClick={() => w.active ? setDeact(true) : act(() => WR.setActive(id, true), 'کاریگر فعال ہو گیا')}>{w.active ? 'غیر فعال کریں' : 'فعال کریں'}</Btn>
          </Row>
        </Section>
      </div>
      <div className="col">
        <Tabs tabs={[`پاس کام (${Fmt.num(pending.length)})`, 'مکمل کام', 'حساب']} value={tab} onChange={setTab} />
        {tab === 0 && <>{pending.map(j => <JobCard key={j.id} j={j} onCancel={() => setCancelJob(j)} />)}
          {pending.length === 0 && <Empty text="اس کاریگر کے پاس ابھی کوئی کام نہیں۔ آرڈر کھول کر کپڑا دیں۔" />}</>}
        {tab === 1 && <>{all.filter(j => j.status !== JobStatus.ASSIGNED).map(j => <JobCard key={j.id} j={j} />)}
          {all.every(j => j.status === JobStatus.ASSIGNED) && <Empty text="ابھی کوئی مکمل کام نہیں" />}</>}
        {tab === 2 && <Section>{[...lg.entries].reverse().map((e, i) => <div key={i} className="item">
          <Row><div style={{ flex: 1 }}><b>{e.description}</b><br /><small>{Fmt.date(e.date)} • بیلنس {Fmt.moneyPlain(e.balance)}</small></div>
            <b className={e.earned > 0 ? 'ok' : 'bad'}>{e.earned > 0 ? '+' + Fmt.moneyPlain(e.earned) : e.paid > 0 ? '−' + Fmt.moneyPlain(e.paid) : '0'}</b></Row>
          {e.payment && <Row><Btn small kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.WORKER_PAYMENT, refId: e.payment!.id })}>پرچی</Btn>
            {!e.payment.voided && <Btn small kind="danger" onClick={() => setVoidPay(e.payment!.id)}>منسوخ کریں</Btn>}</Row>}
        </div>)}{lg.entries.length === 0 && <Empty text="ابھی کوئی اندراج نہیں" />}</Section>}
      </div>
    </div>
    {pay && <WorkerPayDialog workerId={id} balance={lg.balance} onClose={() => setPay(false)} onSaved={pid => nav.go({ name: 'preview', kind: ReceiptKind.WORKER_PAYMENT, refId: pid })} />}
    {cancelJob && <Prompt title="کام منسوخ کریں" label="وجہ (مثلاً کپڑا واپس لے لیا)" onClose={() => setCancelJob(null)} onOk={r => act(() => WR.cancel(cancelJob.id, r.trim() || 'منسوخ'), 'کام منسوخ ہو گیا')} />}
    {voidPay != null && <Prompt title="ادائیگی منسوخ کریں" label="وجہ (لازمی)" onClose={() => setVoidPay(null)} onOk={r => r.trim() ? act(() => WR.voidPayment(voidPay, r.trim()), 'اندراج منسوخ ہو گیا (ریکارڈ محفوظ ہے)') : msg.err('وجہ لکھنا لازمی ہے')} />}
    {deact && <Confirm title="کاریگر غیر فعال کریں؟" text="اس کا سارا حساب محفوظ رہے گا، صرف نیا کام نہیں دیا جا سکے گا۔" onClose={() => setDeact(false)} onOk={() => act(() => WR.setActive(id, false), 'کاریگر غیر فعال ہو گیا')} />}
  </Page>
}

/** One job with its actions (used on the worker page and on the order page). */
export function JobCard({ j, worker, onCancel }: { j: WorkJob; worker?: string; onCancel?: () => void }) {
  const nav = useNav()
  return <div className="card item job">
    <Row><div style={{ flex: 1 }}><b>{worker ? worker + ' — ' : ''}{j.task}: {j.garmentName} × {Fmt.num(j.qty)}</b><br />
      <button className="linkish small" onClick={() => nav.go({ name: 'order', id: j.orderId })}>{Fmt.jobNo(j.id)} • {Fmt.orderNo(j.orderId)}{j.memberName ? ' • ' + j.memberName : ''}</button><br />
      <small>دیا: {Fmt.date(j.assignedAt)} • واپسی: {Fmt.date(j.dueDate)}{j.doneAt ? ' • مکمل: ' + Fmt.date(j.doneAt) : ''}</small><br />
      <small>مزدوری: {Fmt.moneyPlain(j.wage)} × {Fmt.num(j.qty)} = {Fmt.money(j.amount)}</small>
      {j.notes && <><br /><small>نوٹ: {j.notes}</small></>}</div>
      <Chip text={JobStatus.label(j.status)} tone={jobTone(j.status)} /></Row>
    <Row>
      <Btn small kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.WORKER_SLIP, refId: j.id })}>پرچی</Btn>
      {j.status === JobStatus.ASSIGNED && <Btn small kind="ok" onClick={() => act(() => WR.complete(j.id), 'کام مکمل — مزدوری حساب میں شامل')}>✓ واپس آ گیا</Btn>}
      {j.status === JobStatus.ASSIGNED && onCancel && <Btn small kind="danger" onClick={onCancel}>منسوخ</Btn>}
      {j.status === JobStatus.DONE && <Btn small kind="ghost" onClick={() => act(() => WR.reopen(j.id), 'کام دوبارہ کھل گیا')}>واپس کھولیں</Btn>}
    </Row>
  </div>
}

export function WorkerPayDialog({ workerId, balance, onClose, onSaved }: { workerId: number; balance: number; onClose: () => void; onSaved: (id: number) => void }) {
  const [type, setType] = useState(balance > 0 ? WorkerPayType.PAYMENT : WorkerPayType.ADVANCE)
  const [amount, setAmount] = useState(balance > 0 ? Calc.moneyInput(balance) : '')
  const [method, setMethod] = useState(PayMethod.CASH)
  const [notes, setNotes] = useState('')
  const [date, setDate] = useState(Date.now())
  return <Modal title="کاریگر کو ادائیگی" onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn>
    <Btn onClick={() => act(async () => {
      const a = Calc.parseMoney(amount); if (a == null) throw new UserError('رقم درست نہیں')
      const pid = await WR.pay(workerId, type, a, method, notes, date); onClose(); onSaved(pid)
    }, 'ادائیگی محفوظ ہو گئی')}>محفوظ</Btn></>}>
    <p className={balanceTone(balance)}>{balanceText(balance)}</p>
    <Select label="قسم" options={WorkerPayType.all} value={type} labelOf={WorkerPayType.label} onChange={setType} />
    <MoneyInput label="رقم (روپے)" value={amount} onChange={setAmount} />
    <Select label="طریقہ" options={PayMethod.all} value={method} labelOf={x => x} onChange={setMethod} />
    <DateInput label="تاریخ" value={date} onChange={setDate} />
    <Input label="تفصیل" value={notes} onChange={setNotes} />
  </Modal>
}

const EARLY = [OrderStatus.NEW, OrderStatus.MEASURED, OrderStatus.CUTTING]

/** "Give to kaarigar" for one garment of an order. */
export function AssignJobDialog({ item, onClose, onSaved }: { item: OrderItem; onClose: () => void; onSaved: (jobId: number) => void }) {
  const rows = WQ.rows(false)
  const [workerId, setWorkerId] = useState<number | null>(null)
  const [task, setTask] = useState(WorkTasks[0])
  const [qty, setQty] = useState(String(item.qty))
  const [wage, setWage] = useState('')
  const [touched, setTouched] = useState(false)
  const [notes, setNotes] = useState('')
  const [stitching, setStitching] = useState(true)
  useEffect(() => {
    if (workerId == null || touched) return
    const last = WQ.lastWage(workerId, item.garmentName, task)
    if (last != null) setWage(Calc.moneyInput(last))
  }, [workerId, task]) // eslint-disable-line react-hooks/exhaustive-deps
  const canStitch = task === 'سلائی' && EARLY.includes(item.status)
  return <Modal title={'کاریگر کو دیں: ' + item.garmentName} onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn>
    <Btn kind="ok" onClick={() => act(async () => {
      if (workerId == null) throw new UserError('کاریگر منتخب کریں')
      const q = Number(Digits.toLatin(qty)); if (!Number.isInteger(q)) throw new UserError('تعداد درست نہیں')
      const a = wage.trim() ? Calc.parseMoney(wage) : 0; if (a == null) throw new UserError('مزدوری درست نہیں')
      const jid = await WR.assign(item.id, workerId, task, q, a, null, notes)
      if (stitching && canStitch) { try { await R.setItemStatus(item.id, OrderStatus.STITCHING) } catch { /* not allowed: ignore */ } }
      onClose(); onSaved(jid)
    }, 'کام کاریگر کو دے دیا گیا')}>دیں اور پرچی</Btn></>}>
    {rows.length === 0 && <p className="bad">پہلے کاریگر شامل کریں (مین مینو ← کاریگر)</p>}
    <Select label="کاریگر" options={rows.map(r => r.worker.id)} value={workerId} keyOf={x => String(x)}
      labelOf={wid => { const r = rows.find(x => x.worker.id === wid); return r ? r.worker.name + (r.pendingJobs ? ` (پاس کام: ${Fmt.num(r.pendingJobs)})` : '') : '-' }} onChange={setWorkerId} />
    <Select label="کام" options={WorkTasks} value={task} labelOf={x => x} onChange={setTask} />
    {item.qty > 1 && <Input label={`تعداد (کل ${Fmt.num(item.qty)})`} value={qty} onChange={v => setQty(v.replace(/[^\d۰-۹]/g, ''))} inputMode="numeric" />}
    <MoneyInput label="مزدوری فی پیس (روپے)" value={wage} onChange={v => { setWage(v); setTouched(true) }} />
    <Input label="کاریگر کے لیے نوٹ" value={notes} onChange={setNotes} />
    {canStitch && <Switch label="کپڑے کا اسٹیٹس 'سلائی جاری' کریں" checked={stitching} onChange={setStitching} />}
  </Modal>
}
