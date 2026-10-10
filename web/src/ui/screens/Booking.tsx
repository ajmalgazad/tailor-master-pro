import { useEffect, useState } from 'react'
import { Calc, Dates } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, newDraft, newDraftItem, UserError } from '../../repo'
import type { DraftItem, OrderDraft } from '../../repo'
import { Gender, OrderStatus, PayMethod } from '../../types'
import type { Charge, ExtraService, FamilyMember, Garment, MeasurementProfile } from '../../types'
import { act, msg, Btn, CustomerPicker, DateInput, Input, KV, Modal, MoneyInput, Page, Row, Section, Select, Switch, useNav, useStoreVersion } from '../kit'

interface BookingState { d: OrderDraft; rates: Record<number, string>; discount: string; advance: string }

export function Booking({ draft, customerId, memberId }: { draft?: OrderDraft; customerId?: number; memberId?: number }) {
  useStoreVersion()
  const nav = useNav()
  const st = nav.state<BookingState>(() => {
    const d = draft ?? newDraft(customerId ?? 0, memberId ?? 0)
    const rates: Record<number, string> = {}
    d.items.forEach(i => { rates[i.key] = Calc.moneyInput(i.rate) })
    return { d, rates, discount: d.discountInput ? String(d.discountInput) : '', advance: '' }
  })
  const [, force] = useState(0)
  const set = (p: Partial<OrderDraft>) => { st.d = { ...st.d, ...p }; force(x => x + 1) }
  const d = st.d
  const [pickCustomer, setPickCustomer] = useState(false)
  const [addItem, setAddItem] = useState(false)
  const [addCharge, setAddCharge] = useState(false)
  const customer = d.customerId ? Q.customer(d.customerId) : undefined
  const members = d.customerId ? Q.members(d.customerId).filter(m => m.active) : []
  const profiles = d.customerId ? Q.profilesForCustomer(d.customerId).filter(p => !p.archived) : []
  const services = Q.services().filter(s => s.active)
  const prevBalance = d.customerId ? Q.balance(d.customerId) : 0

  useEffect(() => {
    if (members.length && !members.some(m => m.id === d.memberId)) set({ memberId: (members.find(m => m.isSelf) ?? members[0]).id })
  }) // eslint-disable-line

  const discountValue = Calc.parseDouble(st.discount) ?? -1
  const lineRate = (i: DraftItem) => Calc.parseMoney(st.rates[i.key] ?? '') ?? 0
  const totals = Calc.totals(d.items.map(i => ({ qty: i.qty, rate: lineRate(i) })), d.charges.map(c => c.amount), d.discountIsPercent, Math.max(0, discountValue))
  const advance = Calc.parseMoney(st.advance)
  const editing = d.editingOrderId != null

  const save = (asDraft: boolean) => act(async () => {
    const disc = Calc.parseDouble(st.discount); if (disc == null) throw new UserError('رعایت درست نہیں')
    const adv = Calc.parseMoney(st.advance); if (adv == null) throw new UserError('ایڈوانس کی رقم درست نہیں')
    if (d.items.some(i => Calc.parseMoney(st.rates[i.key] ?? '') == null)) throw new UserError('کسی کپڑے کا ریٹ درست نہیں')
    const items = d.items.map(i => ({ ...i, rate: lineRate(i) }))
    const id = await R.saveOrder({ ...d, items, isDraft: asDraft, discountInput: disc, advance: editing ? 0 : adv })
    msg.ok(asDraft ? 'ڈرافٹ محفوظ ہو گیا' : editing ? 'آرڈر اپ ڈیٹ ہو گیا' : 'بکنگ محفوظ ہو گئی — ' + Fmt.orderNo(id))
    nav.replace({ name: 'order', id })
  })

  const title = editing && d.isDraft ? 'ڈرافٹ آرڈر' : editing ? 'آرڈر میں ترمیم ' + Fmt.orderNo(d.editingOrderId!) : d.rebookedFromId != null ? 'دوبارہ بکنگ' : 'نئی بکنگ'
  return <Page title={title}>
    <div className="booking">
      <div className="col">
        <Section title="کسٹمر">
          {!customer ? <><Btn wide onClick={() => setPickCustomer(true)}>کسٹمر منتخب کریں</Btn><Btn wide kind="ghost" onClick={() => nav.go({ name: 'customerEdit' })}>+ نیا کسٹمر رجسٹر کریں</Btn></> : <>
            <Row><div style={{ flex: 1 }}><b>{customer.name}</b><br /><small>{Fmt.customerCode(customer.id)} • {Fmt.d(customer.mobile)}</small>
              {prevBalance !== 0 && <><br /><small className="bad">پچھلا بقایا: {Fmt.money(prevBalance)}</small></>}</div>
              {!editing && <Btn small kind="ghost" onClick={() => setPickCustomer(true)}>تبدیل</Btn>}</Row>
            <Select label="آرڈر کس کے لیے (فیملی ممبر)" options={members} value={members.find(m => m.id === d.memberId) ?? null} keyOf={m => String(m.id)} labelOf={m => m.name + ' — ' + m.relation} onChange={m => set({ memberId: m.id })} />
            <Btn small kind="ghost" onClick={() => nav.go({ name: 'memberEdit', customerId: customer.id })}>+ نیا فیملی ممبر</Btn>
          </>}
        </Section>
        <Section title="تاریخیں">
          <div className="grid2"><DateInput label="بکنگ کی تاریخ" value={d.bookingDate} onChange={v => set({ bookingDate: v })} />
            <DateInput label="ڈیلیوری کی تاریخ" value={d.deliveryDate} onChange={v => set({ deliveryDate: v })} /></div>
          <Row>{[3, 5, 7, 10, 15].map(n => <Btn key={n} small kind="ghost" onClick={() => set({ deliveryDate: Dates.addDays(d.bookingDate, n) })}>{Fmt.num(n)} دن</Btn>)}</Row>
          <Switch label="⚡ ارجنٹ آرڈر" checked={d.urgent} onChange={on => {
            const svc = services.find(s => s.kind === 'URGENT')
            if (on && svc && !d.charges.some(c => c.name === svc.name)) { set({ urgent: on, charges: [...d.charges, { name: svc.name, amount: svc.amount }] }); msg.ok('ارجنٹ چارجز شامل کر دیے گئے (چاہیں تو ہٹا سکتے ہیں)') }
            else set({ urgent: on })
          }} />
        </Section>
        <Section title="کپڑے">
          {d.items.length === 0 && <p className="muted">ابھی کوئی کپڑا شامل نہیں</p>}
          {d.items.map((it, idx) => <ItemEditor key={it.key} item={it} rateText={st.rates[it.key] ?? ''}
            onRate={t => { st.rates[it.key] = t; force(x => x + 1) }}
            profiles={profiles.filter(p => p.memberId === it.memberId && p.garmentId === it.garmentId)}
            onChange={n => set({ items: d.items.map((x, i) => (i === idx ? n : x)) })}
            onRemove={() => set({ items: d.items.filter((_, i) => i !== idx) })}
            onNewProfile={() => nav.go({ name: 'profileEdit', customerId: d.customerId, memberId: it.memberId, garmentId: it.garmentId, returnAfterSave: true })} />)}
          <Btn wide disabled={!customer} onClick={() => setAddItem(true)}>+ کپڑا شامل کریں</Btn>
        </Section>
      </div>
      <div className="col">
        <Section title="اضافی چارجز اور رعایت">
          {d.charges.map((c, i) => <div key={i} className="kv"><span>{c.name}</span><b>{Fmt.money(c.amount)} <button className="icon small" aria-label="ہٹائیں" onClick={() => set({ charges: d.charges.filter((_, j) => j !== i) })}>✕</button></b></div>)}
          <Btn small kind="ghost" onClick={() => setAddCharge(true)}>+ اضافی چارج (ارجنٹ، کڑھائی، ڈیزائن...)</Btn>
          <div className="grid2">
            <Select label="رعایت کی قسم" options={[false, true]} value={d.discountIsPercent} labelOf={x => (x ? 'فیصد (%)' : 'رقم (روپے)')} onChange={v => set({ discountIsPercent: v })} />
            <Input label="رعایت" value={st.discount} inputMode="decimal" onChange={v => { st.discount = v; force(x => x + 1) }}
              error={st.discount.trim() && (discountValue < 0 || (d.discountIsPercent && discountValue > 100)) ? 'غلط' : null} />
          </div>
        </Section>
        <Section title="حساب" className="totals">
          <KV k="کپڑوں کی رقم" v={Fmt.money(totals.itemsTotal)} />
          <KV k="اضافی چارجز" v={Fmt.money(totals.chargesTotal)} />
          <KV k="رعایت" v={'- ' + Fmt.money(totals.discount)} />
          <hr />
          <KV k="کل رقم" v={Fmt.money(totals.net)} bold />
          {!editing ? <>
            <div className="grid2"><MoneyInput label="ایڈوانس" value={st.advance} onChange={v => { st.advance = v; force(x => x + 1) }} />
              <Select label="طریقہ" options={PayMethod.all} value={d.advanceMethod} labelOf={x => x} onChange={v => set({ advanceMethod: v })} /></div>
            {d.advanceMethod !== PayMethod.CASH && <Input label="حوالہ / ٹرانزیکشن آئی ڈی" value={d.advanceRef} onChange={v => set({ advanceRef: v })} />}
            <KV k="بقایا" v={Fmt.money(totals.net - (advance ?? 0))} bold tone="bad" />
            {advance != null && advance > totals.net && <p className="bad">ایڈوانس کل رقم سے زیادہ ہے</p>}
          </> : <p className="muted small">ادائیگیاں آرڈر کی تفصیل والی اسکرین سے درج کریں۔</p>}
        </Section>
        <Section title="ہدایات">
          <Input label="خصوصی ہدایات (رسید پر چھپیں گی)" value={d.instructions} onChange={v => set({ instructions: v })} multiline />
          <Input label="اندرونی نوٹس (صرف دکان کے لیے)" value={d.internalNotes} onChange={v => set({ internalNotes: v })} multiline />
        </Section>
        <Row>
          {(!editing || d.isDraft) && <Btn kind="ghost" onClick={() => save(true)}>ڈرافٹ محفوظ کریں</Btn>}
          <Btn kind="ok" onClick={() => save(false)}>{editing && !d.isDraft ? 'تبدیلیاں محفوظ کریں' : 'بکنگ کنفرم کریں'}</Btn>
        </Row>
      </div>
    </div>
    {pickCustomer && <CustomerPicker onClose={() => setPickCustomer(false)} onPick={c => { setPickCustomer(false); st.rates = {}; set({ customerId: c.id, memberId: 0, items: [] }) }} />}
    {addItem && <AddItem members={members} defaultMember={d.memberId} garments={Q.garments().filter(g => g.active)} onClose={() => setAddItem(false)}
      onAdd={(m, g, qty) => {
        setAddItem(false)
        const rate = Q.resolveRate(g.id, d.customerId, m.id)
        const ps = profiles.filter(p => p.memberId === m.id && p.garmentId === g.id)
        const prof = ps.find(p => p.isDefault) ?? ps.sort((a, b) => b.date - a.date)[0]
        const item = newDraftItem({ garmentId: g.id, garmentName: g.receiptDesc || g.name, memberId: m.id, memberName: m.name, qty, rate, profileId: prof?.id ?? null })
        st.rates[item.key] = Calc.moneyInput(rate)
        set({ items: [...d.items, item] })
        if (!prof) msg.warn(`اس فرد کا ${g.name} کا ناپ محفوظ نہیں۔ 'نیا ناپ لیں' دبا کر ناپ لیں۔`)
      }} />}
    {addCharge && <AddCharge services={services} onClose={() => setAddCharge(false)} onAdd={c => { setAddCharge(false); set({ charges: [...d.charges, c] }) }} />}
  </Page>
}

function ItemEditor({ item, rateText, onRate, profiles, onChange, onRemove, onNewProfile }:
  { item: DraftItem; rateText: string; onRate: (t: string) => void; profiles: MeasurementProfile[]; onChange: (i: DraftItem) => void; onRemove: () => void; onNewProfile: () => void }) {
  const r = Calc.parseMoney(rateText) ?? 0
  return <div className="item">
    <Row><div style={{ flex: 1 }}><b>{item.garmentName}</b><br /><small>فرد: {item.memberName}{item.status !== OrderStatus.NEW ? ' • ' + OrderStatus.label(item.status) : ''}</small></div>
      <button className="icon small" aria-label="ہٹائیں" onClick={onRemove}>✕</button></Row>
    <div className="qty-row">
      <button className="icon" aria-label="کم" onClick={() => item.qty > 1 && onChange({ ...item, qty: item.qty - 1 })}>−</button>
      <b className="qty">{Fmt.num(item.qty)}</b>
      <button className="icon" aria-label="زیادہ" onClick={() => onChange({ ...item, qty: item.qty + 1 })}>+</button>
      <div style={{ flex: 1 }}><MoneyInput label="ریٹ" value={rateText} onChange={onRate} /></div>
    </div>
    <p className="small">رقم: {Fmt.num(item.qty)} × {Fmt.moneyPlain(r)} = <b>{Fmt.money(Calc.lineTotal(item.qty, r))}</b></p>
    <Select label="ناپ" options={[null, ...profiles]} value={profiles.find(p => p.id === item.profileId) ?? null} keyOf={p => String(p?.id ?? '')}
      labelOf={p => (p ? p.name + ' • ' + Fmt.date(p.date) + (p.isDefault ? ' ★' : '') : 'بغیر ناپ')} onChange={p => onChange({ ...item, profileId: p?.id ?? null })} />
    {item.existingSnapshot != null && item.profileId === item.existingProfileId && <p className="ok small">بکنگ کے وقت کا محفوظ شدہ ناپ استعمال ہوگا</p>}
    <Btn small kind="ghost" onClick={onNewProfile}>نیا ناپ لیں</Btn>
  </div>
}

function AddItem({ members, defaultMember, garments, onClose, onAdd }: { members: FamilyMember[]; defaultMember: number; garments: Garment[]; onClose: () => void; onAdd: (m: FamilyMember, g: Garment, qty: number) => void }) {
  const [m, setM] = useState<FamilyMember | null>(members.find(x => x.id === defaultMember) ?? members[0] ?? null)
  const [gender, setGender] = useState<string | null>(null)
  const [g, setG] = useState<Garment | null>(null)
  const [qty, setQty] = useState('1')
  return <Modal title="کپڑا شامل کریں" onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn><Btn onClick={() => {
    const q = Calc.parseInt(qty) ?? 0
    if (!m) msg.err('فرد منتخب کریں'); else if (!g) msg.err('کپڑا منتخب کریں'); else if (q <= 0) msg.err('تعداد کم از کم ۱ ہونی چاہیے'); else onAdd(m, g, q)
  }}>شامل کریں</Btn></>}>
    <Select label="فرد" options={members} value={m} keyOf={x => String(x.id)} labelOf={x => x.name + ' — ' + x.relation} onChange={setM} />
    <Select label="گروپ" options={[null, ...Gender.all]} value={gender} keyOf={x => x ?? ''} labelOf={x => (x ? Gender.label(x) : 'تمام')} onChange={setGender} />
    <Select label="کپڑا" options={garments.filter(x => !gender || x.gender === gender)} value={g} keyOf={x => String(x.id)} labelOf={x => x.name} onChange={setG} />
    <Input label="تعداد" value={qty} onChange={setQty} inputMode="numeric" />
  </Modal>
}

function AddCharge({ services, onClose, onAdd }: { services: ExtraService[]; onClose: () => void; onAdd: (c: Charge) => void }) {
  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  return <Modal title="اضافی چارج" onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn><Btn onClick={() => {
    const a = Calc.parseMoney(amount)
    if (!name.trim()) msg.err('چارج کا نام لکھیں'); else if (a == null || a <= 0) msg.err('درست رقم لکھیں'); else onAdd({ name: name.trim(), amount: a })
  }}>شامل کریں</Btn></>}>
    <Select label="سروس منتخب کریں" options={services} value={null} keyOf={s => String(s.id)} labelOf={s => s.name + ' — ' + Fmt.money(s.amount)} onChange={s => { setName(s.name); setAmount(Calc.moneyInput(s.amount)) }} />
    <Input label="نام" value={name} onChange={setName} />
    <MoneyInput label="رقم (روپے)" value={amount} onChange={setAmount} />
  </Modal>
}
