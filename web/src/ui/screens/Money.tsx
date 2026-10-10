import { useState } from 'react'
import { Calc, Dates } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, UserError } from '../../repo'
import type { OrderRow } from '../../repo'
import { ExpenseCategories, PayMethod, ReceiptKind, TxnType } from '../../types'
import type { Customer, Expense, Txn } from '../../types'
import { act, msg, Btn, CustomerPicker, DateInput, Empty, Input, KV, Modal, MoneyInput, Page, Prompt, Row, Section, Select, Tabs, useNav, useStoreVersion } from '../kit'

export function Payments({ customerId, orderId }: { customerId?: number; orderId?: number }) {
  useStoreVersion()
  const nav = useNav()
  const st = nav.state(() => {
    const o = orderId ? Q.orderRows().find(r => r.order.id === orderId) : undefined
    return { cust: customerId ? Q.customer(customerId) ?? null : null, orderId: orderId ?? null, type: TxnType.PAYMENT, amount: o && o.outstanding > 0 ? Calc.moneyInput(o.outstanding) : '',
      method: PayMethod.CASH, ref: '', notes: '', date: Date.now(), tab: 0, from: Dates.today(), to: Dates.today() }
  })
  const [, force] = useState(0)
  const set = (p: Partial<typeof st>) => { Object.assign(st, p); force(x => x + 1) }
  const [picking, setPicking] = useState(false)
  const cust = st.cust as Customer | null
  const custOrders = cust ? Q.orderRows().filter(r => r.order.customerId === cust.id && !r.order.isDraft && (r.order.status !== 'CANCELLED' || r.paid > 0)) : []
  const order = custOrders.find(r => r.order.id === st.orderId) ?? null
  const bal = cust ? Q.balance(cust.id) : 0
  const rows = Q.customerRows(false)
  const names = new Map(rows.map(r => [r.customer.id, r.customer.name]))
  return <Page title="پیمنٹ اور کھاتہ">
    <Tabs tabs={['رقم وصول / درج کریں', 'بقایا کسٹمرز', 'تمام لین دین']} value={st.tab} onChange={i => set({ tab: i })} />
    {st.tab === 0 && <div className="two-col"><div className="col">
      <Section title="کسٹمر">
        <p><b>{cust ? cust.name + ' • ' + Fmt.customerCode(cust.id) : 'منتخب نہیں'}</b></p>
        {cust && <KV k="موجودہ بقایا" v={Fmt.money(bal)} bold tone={bal > 0 ? 'bad' : 'ok'} />}
        <Row><Btn small kind="ghost" onClick={() => setPicking(true)}>کسٹمر منتخب کریں</Btn>{cust && <Btn small kind="ghost" onClick={() => nav.go({ name: 'ledger', customerId: cust.id })}>کھاتہ دیکھیں</Btn>}</Row>
      </Section></div>
      {cust && <div className="col"><Section title="اندراج">
        <Select label="قسم" options={[TxnType.PAYMENT, TxnType.REFUND, TxnType.ADJ_CREDIT, TxnType.ADJ_DEBIT]} value={st.type} labelOf={TxnType.label} onChange={v => set({ type: v })} />
        <Select label="آرڈر (اختیاری)" options={[null, ...custOrders]} value={order} keyOf={r => String(r?.order.id ?? '')}
          labelOf={(r: OrderRow | null) => (r ? Fmt.orderNo(r.order.id) + ' • بقایا ' + Fmt.money(r.outstanding) : 'عام کھاتہ (کسی آرڈر کے بغیر)')}
          onChange={r => set({ orderId: r?.order.id ?? null, ...(r && r.outstanding > 0 && st.type === TxnType.PAYMENT ? { amount: Calc.moneyInput(r.outstanding) } : {}) })} />
        <MoneyInput label="رقم (روپے)" value={st.amount} onChange={v => set({ amount: v })} />
        {TxnType.isCash(st.type) && <><Select label="طریقہ ادائیگی" options={PayMethod.all} value={st.method} labelOf={x => x} onChange={v => set({ method: v })} />
          <Input label="حوالہ / ٹرانزیکشن آئی ڈی" value={st.ref} onChange={v => set({ ref: v })} /></>}
        <DateInput label="تاریخ" value={st.date} onChange={v => set({ date: v })} />
        <Input label="تفصیل" value={st.notes} onChange={v => set({ notes: v })} />
        <Btn wide kind="ok" onClick={() => act(async () => {
          const a = Calc.parseMoney(st.amount); if (a == null) throw new UserError('رقم درست نہیں')
          if (st.type === TxnType.PAYMENT && order && a > order.outstanding && order.outstanding >= 0) msg.warn('رقم آرڈر کے بقایا سے زیادہ ہے؛ اضافی رقم کسٹمر کے کھاتے میں جمع ہوگی')
          const when = Dates.startOfDay(st.date) === Dates.today() ? Date.now() : st.date
          const tid = await R.recordTxn(cust.id, st.orderId, st.type, a, TxnType.isCash(st.type) ? st.method : '', st.ref, st.notes, when)
          msg.ok('محفوظ ہو گیا — ' + Fmt.txnNo(tid))
          nav.replace({ name: 'preview', kind: ReceiptKind.PAYMENT, refId: tid })
        })}>محفوظ کریں</Btn>
        <p className="muted small">رسید پرنٹ کرنے سے کوئی نئی وصولی درج نہیں ہوتی۔</p>
      </Section></div>}
    </div>}
    {st.tab === 1 && (() => { const list = rows.filter(r => r.balance > 0).sort((a, b) => b.balance - a.balance)
      return <><KV k="کل بقایا" v={Fmt.money(list.reduce((s, r) => s + r.balance, 0))} bold tone="bad" />
        <div className="list">{list.map(r => <button key={r.customer.id} className="list-row" onClick={() => nav.go({ name: 'ledger', customerId: r.customer.id })}>
          <div><b>{r.customer.name}</b><small>{Fmt.d(r.customer.mobile)}</small></div><div className="end"><b className="bad">{Fmt.money(r.balance)}</b></div></button>)}
          {list.length === 0 && <Empty text="کسی کسٹمر کا بقایا نہیں" />}</div></> })()}
    {st.tab === 2 && (() => { const list = Q.allTxns().filter(t => t.date >= st.from && t.date <= Dates.endOfDay(st.to))
      return <><div className="grid2"><DateInput label="سے" value={st.from} onChange={v => set({ from: Dates.startOfDay(v) })} /><DateInput label="تک" value={st.to} onChange={v => set({ to: Dates.startOfDay(v) })} /></div>
        <KV k="اس مدت میں نقد وصولی" v={Fmt.money(-list.filter(t => t.cash).reduce((s, t) => s + t.signedEffect, 0))} bold tone="ok" />
        <div className="list">{list.map(t => <TxnRow key={t.id} t={t} name={names.get(t.customerId) ?? '-'} onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.PAYMENT, refId: t.id })} />)}
          {list.length === 0 && <Empty text="اس مدت میں کوئی لین دین نہیں" />}</div>
        <Btn wide kind="ghost" onClick={() => nav.go({ name: 'report', key: 'DAILY_COLLECTION' })}>روزانہ وصولی رپورٹ (PDF / CSV)</Btn></> })()}
    {picking && <CustomerPicker onClose={() => setPicking(false)} onPick={c => { setPicking(false); set({ cust: c, orderId: null }) }} />}
  </Page>
}

export function TxnRow({ t, name, onClick }: { t: Txn; name: string; onClick: () => void }) {
  return <button className={'list-row' + (t.reversedById != null || t.type === TxnType.REVERSAL ? ' dim' : '')} onClick={onClick}>
    <div><b>{TxnType.label(t.type)} • {Fmt.txnNo(t.id)}{t.reversedById != null ? ' (منسوخ شدہ)' : ''}</b>
      <small>{name}{t.orderId != null ? ' • ' + Fmt.orderNo(t.orderId) : ''} • {Fmt.dateTime(t.date)}</small>
      {(t.method || t.reference) && <small>{t.method}{t.reference ? ' • ' + t.reference : ''}</small>}</div>
    <div className="end"><b className={t.signedEffect < 0 ? 'ok' : 'bad'}>{Fmt.money(t.amount)}</b></div>
  </button>
}

export function Ledger({ customerId }: { customerId: number }) {
  useStoreVersion()
  const nav = useNav()
  const [sel, setSel] = useState<Txn | null>(null)
  const [rev, setRev] = useState<Txn | null>(null)
  let l
  try { l = Q.ledger(customerId) } catch { return <Page title="کھاتہ"><Empty text="کسٹمر نہیں ملا" /></Page> }
  return <Page title="کھاتہ">
    <Section><h2>{l.customer.name} • {Fmt.customerCode(l.customer.id)}</h2>
      <KV k="موجودہ بقایا" v={Fmt.money(l.balance)} bold tone={l.balance > 0 ? 'bad' : 'ok'} />
      <p className="muted small">مثبت بقایا = کسٹمر کے ذمے رقم۔ منفی = کسٹمر کی پیشگی رقم دکان کے پاس۔</p></Section>
    <Row><Btn kind="ok" onClick={() => nav.go({ name: 'payments', customerId })}>رقم وصول</Btn><Btn onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.LEDGER, refId: customerId })}>اسٹیٹمنٹ پرنٹ</Btn></Row>
    <div className="table-wrap"><table className="table">
      <thead><tr><th>تاریخ</th><th>تفصیل</th><th>واجب</th><th>جمع</th><th>بیلنس</th></tr></thead>
      <tbody>{l.entries.map((e, i) => <tr key={i} className={e.txn || e.orderId ? 'click' : ''} onClick={() => { if (e.txn) setSel(e.txn); else if (e.orderId != null) nav.go({ name: 'order', id: e.orderId }) }}>
        <td>{Fmt.date(e.date)}</td><td>{e.description}</td><td className="bad">{e.debit ? Fmt.moneyPlain(e.debit) : ''}</td><td className="ok">{e.credit ? Fmt.moneyPlain(e.credit) : ''}</td><td><b>{Fmt.moneyPlain(e.balance)}</b></td></tr>)}</tbody>
    </table></div>
    {sel && <Modal title={TxnType.label(sel.type) + ' ' + Fmt.txnNo(sel.id)} onClose={() => setSel(null)} actions={<>
      {sel.reversedById == null && sel.type !== TxnType.REVERSAL && <Btn kind="danger" onClick={() => { setRev(sel); setSel(null) }}>منسوخ (ریورس) کریں</Btn>}
      <Btn onClick={() => { const id = sel.id; setSel(null); nav.go({ name: 'preview', kind: ReceiptKind.PAYMENT, refId: id }) }}>رسید</Btn></>}>
      <KV k="رقم" v={Fmt.money(sel.amount)} bold /><KV k="تاریخ" v={Fmt.dateTime(sel.date)} />{sel.method && <KV k="طریقہ" v={sel.method} />}{sel.reference && <KV k="حوالہ" v={sel.reference} />}
      {sel.notes && <p>{sel.notes}</p>}{sel.reversedById != null && <p className="warn">یہ اندراج منسوخ ہو چکا ہے ({Fmt.txnNo(sel.reversedById)})</p>}
    </Modal>}
    {rev && <Prompt title="اندراج منسوخ کریں" label="وجہ (لازمی)" onClose={() => setRev(null)} onOk={r => act(async () => {
      if (!r.trim()) throw new UserError('وجہ لکھنا ضروری ہے'); await R.reverseTxn(rev.id, r)
    }, 'اندراج منسوخ ہو گیا۔ مخالف اندراج ریکارڈ میں محفوظ ہے')} />}
  </Page>
}

export function Expenses() {
  useStoreVersion()
  const nav = useNav()
  const [month, setMonth] = useState(Dates.startOfMonth(Date.now()))
  const [edit, setEdit] = useState<Expense | null>(null)
  const [voidOf, setVoidOf] = useState<Expense | null>(null)
  const all = Q.expenses()
  const list = all.filter(e => e.date >= month && e.date <= Dates.endOfMonth(month))
  const active = list.filter(e => !e.voided)
  const today = Dates.today()
  const byCat = new Map<string, number>(); active.forEach(e => byCat.set(e.category, (byCat.get(e.category) ?? 0) + e.amount))
  return <Page title="اخراجات" actions={<Btn small onClick={() => setEdit({ id: 0, date: Date.now(), category: ExpenseCategories[0], amount: 0, method: PayMethod.CASH, notes: '', voided: false, sample: false })}>+ نیا خرچ</Btn>}>
    <DateInput label="مہینہ" value={month} onChange={v => setMonth(Dates.startOfMonth(v))} />
    <Section title="خلاصہ">
      <KV k="آج کے اخراجات" v={Fmt.money(all.filter(e => !e.voided && e.date >= today && e.date <= Dates.endOfDay(today)).reduce((s, e) => s + e.amount, 0))} />
      <KV k="اس مہینے کے اخراجات" v={Fmt.money(active.reduce((s, e) => s + e.amount, 0))} bold tone="bad" />
      {Array.from(byCat.entries()).map(([k, v]) => <KV key={k} k={k} v={Fmt.money(v)} />)}
      <Row><Btn small kind="ghost" onClick={() => nav.go({ name: 'report', key: 'EXPENSES' })}>اخراجات رپورٹ</Btn><Btn small kind="ghost" onClick={() => nav.go({ name: 'report', key: 'INCOME_EXPENSE' })}>آمدن بمقابلہ خرچ</Btn></Row>
    </Section>
    <div className="list">{list.map(e => <button key={e.id} className={'list-row' + (e.voided ? ' dim' : '')} disabled={e.voided} onClick={() => setEdit(e)}>
      <div><b>{e.category}{e.voided ? ' (منسوخ)' : ''}</b><small>{Fmt.date(e.date)} • {e.method}{e.notes ? ' • ' + e.notes : ''}</small></div><div className="end"><b>{Fmt.money(e.amount)}</b></div></button>)}
      {list.length === 0 && <Empty text="اس مہینے کوئی خرچ درج نہیں" />}</div>
    {edit && <ExpenseDialog e={edit} onClose={() => setEdit(null)} onVoid={() => { setVoidOf(edit); setEdit(null) }} />}
    {voidOf && <Prompt title="خرچ منسوخ کریں" label="وجہ" onClose={() => setVoidOf(null)} onOk={r => act(() => R.voidExpense(voidOf, r.trim() || 'منسوخ'), 'خرچ منسوخ ہو گیا (ریکارڈ محفوظ ہے)')} />}
  </Page>
}

function ExpenseDialog({ e, onClose, onVoid }: { e: Expense; onClose: () => void; onVoid: () => void }) {
  const isDefault = ExpenseCategories.includes(e.category)
  const [cat, setCat] = useState(isDefault ? e.category : 'دیگر اخراجات')
  const [custom, setCustom] = useState(isDefault ? '' : e.category)
  const [amount, setAmount] = useState(e.amount ? Calc.moneyInput(e.amount) : '')
  const [method, setMethod] = useState(e.method)
  const [notes, setNotes] = useState(e.notes)
  const [date, setDate] = useState(e.date)
  return <Modal title={e.id ? 'خرچ میں ترمیم' : 'نیا خرچ'} onClose={onClose} actions={<>
    {e.id ? <Btn kind="danger" onClick={onVoid}>منسوخ کریں</Btn> : null}<Btn kind="ghost" onClick={onClose}>بند</Btn>
    <Btn onClick={() => act(async () => {
      const a = Calc.parseMoney(amount); if (a == null) throw new UserError('رقم درست نہیں')
      const c = cat === 'دیگر اخراجات' && custom.trim() ? custom.trim() : cat
      await R.saveExpense({ ...e, category: c, amount: a, method, notes, date }); onClose()
    }, 'خرچ محفوظ ہو گیا')}>محفوظ</Btn></>}>
    <DateInput label="تاریخ" value={date} onChange={setDate} />
    <Select label="قسم" options={ExpenseCategories} value={cat} labelOf={x => x} onChange={setCat} />
    {cat === 'دیگر اخراجات' && <Input label="قسم کا نام (اختیاری)" value={custom} onChange={setCustom} />}
    <MoneyInput label="رقم (روپے)" value={amount} onChange={setAmount} />
    <Select label="طریقہ" options={PayMethod.all} value={method} labelOf={x => x} onChange={setMethod} />
    <Input label="تفصیل" value={notes} onChange={setNotes} />
  </Modal>
}
