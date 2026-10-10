import { useState } from 'react'
import { Calc, Codes, Dates, Digits } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, decodeCharges, decodeIds, decodeSnapshot, UserError } from '../../repo'
import type { OrderRow } from '../../repo'
import { OrderStatus, PayMethod, ReceiptKind, TxnType } from '../../types'
import { act, msg, Btn, Chip, Confirm, DateInput, Empty, Input, KV, MoneyInput, Page, Prompt, Row, SearchBox, Section, Select, statusTone, Switch, Tabs, useNav, useStoreVersion } from '../kit'
import { OrderRowCard } from './Customers'

const quick: [string, string][] = [['all', 'تمام'], ['today', 'آج کی بکنگ'], ['due_today', 'آج ڈیلیوری'], ['open', 'جاری'], ['ready', 'تیار'], ['overdue', 'تاخیر'],
  ['urgent', 'ارجنٹ'], ['draft', 'ڈرافٹ'], ['delivered', 'ڈیلیور شدہ'], ['cancelled', 'منسوخ']]

export function matchesOrder(r: OrderRow, q: string) {
  if (!q.trim()) return true
  const t = Digits.toLatin(q.trim()).toLowerCase()
  return Codes.code('B', r.order.id, 5).toLowerCase().includes(t) || Codes.parse(t) === r.order.id || r.customerName.toLowerCase().includes(t) || r.customerMobile.includes(t) || r.memberName.includes(t)
}

export function Orders({ filter }: { filter?: string }) {
  useStoreVersion()
  const nav = useNav()
  const [q, setQ] = useState('')
  const [qf, setQf] = useState(filter ?? 'all')
  const [status, setStatus] = useState<string | null>(null)
  const [useDates, setUseDates] = useState(false)
  const [byDelivery, setByDelivery] = useState(false)
  const [from, setFrom] = useState(Dates.startOfMonth(Date.now()))
  const [to, setTo] = useState(Dates.today())
  const today = Dates.today(), endToday = Dates.endOfDay(today)
  const list = Q.orderRows().filter(r => {
    const o = r.order
    const ok = qf === 'today' ? !o.isDraft && o.bookingDate >= today && o.bookingDate <= endToday
      : qf === 'due_today' ? !o.isDraft && o.deliveryDate >= today && o.deliveryDate <= endToday && o.status !== 'CANCELLED' && o.status !== 'DELIVERED'
      : qf === 'open' ? !o.isDraft && OrderStatus.open.includes(o.status)
      : qf === 'ready' ? !o.isDraft && o.status === 'READY'
      : qf === 'overdue' ? r.overdue
      : qf === 'urgent' ? o.urgent && o.status !== 'DELIVERED' && o.status !== 'CANCELLED'
      : qf === 'draft' ? o.isDraft
      : qf === 'delivered' ? o.status === 'DELIVERED'
      : qf === 'cancelled' ? o.status === 'CANCELLED' : true
    const d = byDelivery ? o.deliveryDate : o.bookingDate
    return ok && (!status || o.status === status) && (!useDates || (d >= Dates.startOfDay(from) && d <= Dates.endOfDay(to))) && matchesOrder(r, q)
  }).sort((a, b) => Number(b.order.urgent && b.order.status !== 'DELIVERED') - Number(a.order.urgent && a.order.status !== 'DELIVERED') || b.order.id - a.order.id)
  return <Page title="آرڈر مینجمنٹ" actions={<Btn small kind="ok" onClick={() => nav.go({ name: 'booking' })}>+ نئی بکنگ</Btn>}>
    <SearchBox value={q} onChange={setQ} placeholder="آرڈر نمبر، کسٹمر یا موبائل" />
    <Tabs tabs={quick.map(x => x[1])} value={Math.max(0, quick.findIndex(x => x[0] === qf))} onChange={i => setQf(quick[i][0])} />
    <div className="grid2">
      <Select label="اسٹیٹس" options={[null, ...OrderStatus.all]} value={status} keyOf={x => x ?? ''} labelOf={x => (x ? OrderStatus.label(x) : 'تمام اسٹیٹس')} onChange={setStatus} />
      <Switch label="تاریخ سے فلٹر" checked={useDates} onChange={setUseDates} />
    </div>
    {useDates && <div className="grid3">
      <Select label="تاریخ کی قسم" options={[false, true]} value={byDelivery} labelOf={x => (x ? 'ڈیلیوری تاریخ' : 'بکنگ تاریخ')} onChange={setByDelivery} />
      <DateInput label="سے" value={from} onChange={setFrom} /><DateInput label="تک" value={to} onChange={setTo} /></div>}
    <p className="muted">کل: {Fmt.num(list.length)}</p>
    <div className="list">{list.map(r => <OrderRowCard key={r.order.id} r={r} onClick={() => nav.go({ name: 'order', id: r.order.id })} />)}
      {list.length === 0 && <Empty text="کوئی آرڈر نہیں ملا" />}</div>
  </Page>
}

export function OrderDetail({ id }: { id: number }) {
  useStoreVersion()
  const nav = useNav()
  const [cancel, setCancel] = useState(false)
  const [notes, setNotes] = useState(false)
  const [ask, setAsk] = useState<string | null>(null)
  let f
  try { f = Q.orderFull(id) } catch { return <Page title={'آرڈر ' + Fmt.orderNo(id)}><Empty text="آرڈر نہیں ملا" /></Page> }
  const o = f.order
  const anyDelivered = f.items.some(i => i.delivered)
  const closed = o.status === 'CANCELLED' || o.status === 'DELIVERED'
  return <Page title={'آرڈر ' + Fmt.orderNo(id)}>
    <div className="two-col">
      <div className="col">
        <Section>
          <Row><h2 style={{ flex: 1 }}>{Fmt.orderNo(o.id)}{o.urgent ? '  ⚡ارجنٹ' : ''}</h2><Chip text={o.isDraft ? 'ڈرافٹ' : OrderStatus.label(o.status)} tone={o.isDraft ? 'warn' : statusTone(o.status)} /></Row>
          <button className="linkish" onClick={() => nav.go({ name: 'customer', id: f.customer.id })}><b>{f.customer.name} ({Fmt.customerCode(f.customer.id)})</b></button>
          {f.member && <KV k="فیملی ممبر" v={f.member.name + ' — ' + f.member.relation} />}
          <KV k="موبائل" v={Fmt.d(f.customer.mobile)} />
          <KV k="بکنگ" v={Fmt.date(o.bookingDate)} />
          <KV k="ڈیلیوری" v={Fmt.date(o.deliveryDate)} bold tone={!closed && o.deliveryDate < Dates.today() ? 'bad' : undefined} />
          {o.rebookedFromId != null && <KV k="دوبارہ بکنگ از" v={Fmt.orderNo(o.rebookedFromId)} />}
          {o.instructions && <p>ہدایات: {o.instructions}</p>}
        </Section>
        {o.isDraft && <Btn wide kind="ok" onClick={() => nav.go({ name: 'booking', draft: R.buildDraftFromOrder(id, false) })}>ڈرافٹ کو کنفرم کریں / ترمیم</Btn>}
        <Section title="کپڑے">
          {f.items.map(it => { const snap = decodeSnapshot(it.measurementJson)
            return <div key={it.id} className="item">
              <Row><div style={{ flex: 1 }}><b>{it.garmentName} × {Fmt.num(it.qty)}</b><br />
                <small>فرد: {it.memberName} • {Fmt.moneyPlain(it.rate)} = {Fmt.money(it.total)}</small><br />
                <small className={snap ? 'muted' : 'warn'}>{snap ? 'ناپ: ' + snap.profileName + ' (' + Fmt.date(snap.date) + ')' : 'ناپ منسلک نہیں'}</small></div>
                <Chip text={OrderStatus.label(it.status)} tone={statusTone(it.status)} /></Row>
              {!it.delivered && !closed && !o.isDraft && <Select label="اس کپڑے کا اسٹیٹس" options={OrderStatus.workflow.filter(s => s !== 'DELIVERED')} value={it.status} labelOf={OrderStatus.label}
                onChange={s => act(() => R.setItemStatus(it.id, s), 'اسٹیٹس تبدیل')} />}
              {it.delivered && <p className="ok small">ڈیلیور: {Fmt.dateTime(it.deliveredAt)}</p>}
            </div> })}
        </Section>
      </div>
      <div className="col">
        <Section title="حساب">
          <KV k="کپڑوں کی رقم" v={Fmt.money(o.itemsTotal)} />
          {decodeCharges(o.chargesJson).map((c, i) => <KV key={i} k={c.name} v={Fmt.money(c.amount)} />)}
          {o.discountAmount > 0 && <KV k="رعایت" v={'- ' + Fmt.money(o.discountAmount)} />}
          <KV k="کل رقم" v={Fmt.money(o.netAmount)} bold />
          <KV k="وصول شدہ" v={Fmt.money(f.paid)} tone="ok" />
          <KV k="بقایا" v={Fmt.money(f.outstanding)} bold tone={f.outstanding > 0 ? 'bad' : 'ok'} />
          {o.status === 'CANCELLED' && <p className="warn small">منسوخ آرڈر کی رقم کھاتے میں شامل نہیں۔{f.paid > 0 ? ' وصول شدہ رقم کسٹمر کے کھاتے میں جمع ہے، چاہیں تو ریفنڈ درج کریں۔' : ''}</p>}
          <KV k="کسٹمر کا کل بقایا" v={Fmt.money(f.customerBalance)} />
        </Section>
        {!o.isDraft && <Row>
          <Btn disabled={o.status === 'CANCELLED'} onClick={() => nav.go({ name: 'payments', customerId: f.customer.id, orderId: id })}>رقم وصول کریں</Btn>
          <Btn kind="ok" disabled={closed} onClick={() => nav.go({ name: 'deliver', orderId: id })}>ڈیلیوری</Btn>
        </Row>}
        <Row>
          <Btn kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.BOOKING, refId: id })}>بکنگ رسید</Btn>
          <Btn kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.MEASUREMENT, refId: id })}>ناپ کی پرچی</Btn>
        </Row>
        {!o.isDraft && !closed && <Section title="آرڈر کا اسٹیٹس">
          <Select label="نیا اسٹیٹس (تمام باقی کپڑوں پر)" options={[...OrderStatus.open, 'READY']} value={o.status} labelOf={OrderStatus.label} onChange={setAsk} />
        </Section>}
        <Row>
          <Btn kind="ghost" disabled={anyDelivered || o.status === 'CANCELLED'} onClick={() => nav.go({ name: 'booking', draft: R.buildDraftFromOrder(id, false) })}>ترمیم</Btn>
          <Btn kind="ghost" onClick={() => nav.go({ name: 'booking', draft: R.buildDraftFromOrder(id, true) })}>دوبارہ بکنگ</Btn>
          <Btn kind="danger" disabled={anyDelivered || o.status === 'CANCELLED'} onClick={() => setCancel(true)}>منسوخ</Btn>
        </Row>
        <Section title="اندرونی نوٹس (ورکشاپ)"><p>{o.internalNotes || 'کوئی نوٹ نہیں'}</p><Btn small kind="ghost" onClick={() => setNotes(true)}>نوٹ لکھیں / تبدیل کریں</Btn></Section>
        {f.txns.length > 0 && <Section title="ادائیگیاں">{f.txns.map(t => <button key={t.id} className="list-row" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.PAYMENT, refId: t.id })}>
          <div><b>{TxnType.label(t.type)} • {Fmt.txnNo(t.id)}{t.reversedById != null ? ' (منسوخ شدہ)' : ''}</b><small>{Fmt.dateTime(t.date)} • {t.method}</small></div><div className="end"><b>{Fmt.money(t.amount)}</b></div></button>)}</Section>}
        {f.deliveries.length > 0 && <Section title="ڈیلیوریاں">{f.deliveries.map(dl => <button key={dl.id} className="list-row" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.DELIVERY, refId: dl.id })}>
          <div><b>{Fmt.deliveryNo(dl.id)} • {Fmt.dateTime(dl.date)}</b></div><div className="end">{Fmt.num(decodeIds(dl.itemIdsJson).length)} آئٹم</div></button>)}</Section>}
        <Section title="اسٹیٹس ہسٹری">{f.log.map(l => { const nm = l.itemId != null ? f.items.find(i => i.id === l.itemId)?.garmentName : undefined
          return <p key={l.id} className="small">{Fmt.dateTime(l.at)}: {l.fromStatus && l.fromStatus !== l.toStatus ? OrderStatus.label(l.fromStatus) + ' ← ' : ''}{OrderStatus.label(l.toStatus)}{nm ? ' (' + nm + ')' : ''}{l.note ? ' — ' + l.note : ''}</p> })}</Section>
      </div>
    </div>
    {ask && <Confirm title="اسٹیٹس تبدیل کریں؟" text={`آرڈر اور تمام باقی کپڑوں کا اسٹیٹس '${OrderStatus.label(ask)}' ہو جائے گا۔`} okText="تبدیل کریں" onClose={() => setAsk(null)}
      onOk={() => act(() => R.setOrderStatus(id, ask, true), 'اسٹیٹس تبدیل ہو گیا')} />}
    {cancel && <Prompt title="آرڈر منسوخ کریں" label="منسوخی کی وجہ" onClose={() => setCancel(false)} onOk={r => act(() => R.cancelOrder(id, r.trim() || 'منسوخ'), 'آرڈر منسوخ ہو گیا')} />}
    {notes && <Prompt title="اندرونی نوٹس" label="نوٹ" initial={o.internalNotes} onClose={() => setNotes(false)} onOk={t => act(() => R.saveInternalNotes(id, t), 'نوٹ محفوظ')} />}
  </Page>
}

export function Deliveries() {
  useStoreVersion()
  const nav = useNav()
  const [q, setQ] = useState('')
  const [tab, setTab] = useState(0)
  const all = Q.orderRows()
  const byId = new Map(all.map(r => [r.order.id, r]))
  const pending = all.filter(r => !r.order.isDraft && r.order.status !== 'DELIVERED' && r.order.status !== 'CANCELLED' && matchesOrder(r, q))
    .sort((a, b) => Number(b.order.status === 'READY') - Number(a.order.status === 'READY') || a.order.deliveryDate - b.order.deliveryDate)
  return <Page title="ڈیلیوری مینجمنٹ">
    <Tabs tabs={[`ڈیلیوری کے لیے (${Fmt.num(pending.length)})`, 'ہو چکی ڈیلیوریاں']} value={tab} onChange={setTab} />
    {tab === 0 ? <>
      <SearchBox value={q} onChange={setQ} placeholder="آرڈر نمبر، کسٹمر یا موبائل" />
      <div className="list">{pending.map(r => <div key={r.order.id}><OrderRowCard r={r} onClick={() => nav.go({ name: 'order', id: r.order.id })} />
        <Btn wide small kind="ghost" onClick={() => nav.go({ name: 'deliver', orderId: r.order.id })}>ڈیلیور کریں</Btn></div>)}
        {pending.length === 0 && <Empty text="کوئی آرڈر ڈیلیوری کے لیے باقی نہیں" />}</div>
    </> : <div className="list">{Q.deliveries().slice(0, 100).map(d => <button key={d.id} className="list-row" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.DELIVERY, refId: d.id })}>
      <div><b>{Fmt.deliveryNo(d.id)} • {Fmt.orderNo(d.orderId)}</b><small>{byId.get(d.orderId)?.customerName ?? '-'} • {Fmt.dateTime(d.date)} • {Fmt.num(decodeIds(d.itemIdsJson).length)} آئٹم</small>
        <small>رسید دوبارہ پرنٹ کرنے کے لیے دبائیں</small></div></button>)}</div>}
  </Page>
}

export function Deliver({ orderId }: { orderId: number }) {
  useStoreVersion()
  const nav = useNav()
  const st = nav.state(() => {
    const f = Q.orderFull(orderId)
    const pend = f.items.filter(i => !i.delivered)
    let sel = pend.filter(i => i.status === 'READY').map(i => i.id)
    if (!sel.length) sel = pend.map(i => i.id)
    return { sel, pay: f.outstanding > 0 ? Calc.moneyInput(f.outstanding) : '', method: PayMethod.CASH, notes: '' }
  })
  const [, force] = useState(0)
  const set = (p: Partial<typeof st>) => { Object.assign(st, p); force(x => x + 1) }
  const [askForce, setAskForce] = useState(false)
  const f = Q.orderFull(orderId)
  const pending = f.items.filter(i => !i.delivered)
  const submit = (forceAll: boolean) => act(async () => {
    const pay = Calc.parseMoney(st.pay); if (pay == null) throw new UserError('رقم درست نہیں')
    const did = await R.deliver(orderId, st.sel, st.notes, pay, st.method, forceAll)
    msg.ok('ڈیلیوری محفوظ ہو گئی')
    nav.replace({ name: 'preview', kind: ReceiptKind.DELIVERY, refId: did })
  })
  const remaining = pending.filter(i => !st.sel.includes(i.id)).length
  return <Page title={'ڈیلیوری ' + Fmt.orderNo(orderId)}>
    <Section><KV k="کسٹمر" v={f.customer.name} bold />{f.member && <KV k="فیملی ممبر" v={f.member.name} />}<KV k="ڈیلیوری تاریخ" v={Fmt.date(f.order.deliveryDate)} /></Section>
    <Section title="کون سے کپڑے دیے جا رہے ہیں؟">
      {pending.map(i => <Switch key={i.id} label={`${i.garmentName} × ${Fmt.num(i.qty)} — ${i.memberName} (${OrderStatus.label(i.status)})`} checked={st.sel.includes(i.id)}
        onChange={on => set({ sel: on ? [...st.sel, i.id] : st.sel.filter(x => x !== i.id) })} />)}
      {f.items.filter(i => i.delivered).map(i => <p key={i.id} className="ok small">✓ {i.garmentName} — پہلے ڈیلیور ہو چکا</p>)}
      {remaining > 0 && <p className="warn">ابھی {Fmt.num(remaining)} کپڑے باقی رہیں گے (جزوی ڈیلیوری)</p>}
    </Section>
    <Section title="وصولی">
      <KV k="کل رقم" v={Fmt.money(f.order.netAmount)} /><KV k="پہلے وصول" v={Fmt.money(f.paid)} /><KV k="بقایا" v={Fmt.money(f.outstanding)} bold tone="bad" />
      <MoneyInput label="ابھی وصول ہونے والی رقم" value={st.pay} onChange={v => set({ pay: v })} />
      <Select label="طریقہ" options={PayMethod.all} value={st.method} labelOf={x => x} onChange={v => set({ method: v })} />
      <Input label="ڈیلیوری نوٹ" value={st.notes} onChange={v => set({ notes: v })} />
    </Section>
    <Btn wide kind="ok" disabled={!st.sel.length} onClick={() => submit(false)}>ڈیلیوری محفوظ کریں</Btn>
    {remaining > 0 && <Btn wide kind="ghost" onClick={() => setAskForce(true)}>باقی کپڑے بھی مکمل مان کر پورا آرڈر بند کریں</Btn>}
    {askForce && <Confirm title="پورا آرڈر ڈیلیور مانیں؟" text="جو کپڑے منتخب نہیں، وہ بھی ڈیلیور شدہ درج ہو جائیں گے۔ صرف تب کریں جب واقعی کسٹمر کو سب کچھ مل چکا ہو۔"
      okText="ہاں، مکمل کریں" danger onClose={() => setAskForce(false)} onOk={() => submit(true)} />}
  </Page>
}
