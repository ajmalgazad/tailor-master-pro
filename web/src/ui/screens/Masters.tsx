import { useState } from 'react'
import { Calc } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, UserError } from '../../repo'
import { Gender, ServiceKind } from '../../types'
import type { Customer, CustomRate, ExtraService, FamilyMember, Garment, MeasurementField } from '../../types'
import { act, Btn, CustomerPicker, Empty, Input, Modal, MoneyInput, Page, Section, Select, Switch, Tabs, useNav, useStoreVersion } from '../kit'

export function Garments() {
  useStoreVersion()
  const nav = useNav()
  const [gender, setGender] = useState<string | null>(null)
  const list = Q.garments().filter(g => !gender || g.gender === gender)
  return <Page title="کپڑوں کی اقسام" actions={<Btn small onClick={() => nav.go({ name: 'garmentEdit' })}>+ نئی قسم</Btn>}>
    <Select label="گروپ" options={[null, ...Gender.all]} value={gender} keyOf={x => x ?? ''} labelOf={x => (x ? Gender.label(x) : 'تمام')} onChange={setGender} />
    <p className="muted">ترتیب بدلنے کے لیے تیر دبائیں۔ کسی قسم پر دبا کر نام، ریٹ اور ناپ کے خانے تبدیل کریں۔</p>
    <div className="list">{list.map(g => <div key={g.id} className="list-row static">
      <button className="link-area" onClick={() => nav.go({ name: 'garmentEdit', id: g.id })}><b>{g.name}{!g.active ? ' (غیر فعال)' : ''}</b><small>{Gender.label(g.gender)} • ریٹ: {Fmt.money(g.defaultRate)}</small></button>
      <div className="end row"><button className="icon small" aria-label="اوپر" onClick={() => act(() => R.moveGarment(g.id, true))}>▲</button>
        <button className="icon small" aria-label="نیچے" onClick={() => act(() => R.moveGarment(g.id, false))}>▼</button></div>
    </div>)}</div>
  </Page>
}

export function GarmentEdit({ id }: { id?: number }) {
  useStoreVersion()
  const init = id ? Q.garment(id) : undefined
  const [g, setG] = useState<Garment>(init ? { ...init } : { id: 0, name: '', gender: 'MEN', sortOrder: 0, defaultRate: 0, receiptDesc: '', active: true })
  const [rate, setRate] = useState(init ? Calc.moneyInput(init.defaultRate) : '')
  const [cur, setCur] = useState<number | undefined>(id)
  const [edit, setEdit] = useState<MeasurementField | null>(null)
  const up = (p: Partial<Garment>) => setG({ ...g, ...p })
  const fields = cur ? Q.fields(cur) : []
  return <Page title={id ? 'کپڑے کی قسم' : 'نئی کپڑے کی قسم'}>
    <Section>
      <Input label="نام *" value={g.name} onChange={v => up({ name: v })} />
      <Select label="گروپ" options={Gender.all} value={g.gender} labelOf={Gender.label} onChange={v => up({ gender: v })} />
      <MoneyInput label="معیاری سلائی ریٹ (روپے)" value={rate} onChange={setRate} />
      <Input label="رسید پر تفصیل" value={g.receiptDesc} onChange={v => up({ receiptDesc: v })} />
      <Switch label="فعال" checked={g.active} onChange={v => up({ active: v })} />
      <p className="muted small">ریٹ بدلنے سے پرانے آرڈرز کی رقم نہیں بدلے گی۔</p>
      <Btn wide onClick={() => act(async () => {
        const r = Calc.parseMoney(rate); if (r == null) throw new UserError('ریٹ درست نہیں')
        const saved = await R.saveGarment({ ...g, defaultRate: r, receiptDesc: g.receiptDesc.trim() || g.name })
        if (!cur) { setCur(saved); setG({ ...g, id: saved }) }
      }, 'محفوظ ہو گیا')}>محفوظ کریں</Btn>
    </Section>
    {cur && <Section title="ناپ کے خانے (فارم)">
      <p className="muted small">یہ خانے اس کپڑے کا ناپ لیتے وقت دکھائی دیں گے۔</p>
      {fields.map(f => <div key={f.id} className="list-row static">
        <button className="link-area" onClick={() => setEdit(f)}><b>{f.label}{f.required ? ' *' : ''}{!f.active ? ' (بند)' : ''}</b>{f.unit && <small>اکائی: {f.unit}</small>}</button>
        <div className="end row"><button className="icon small" aria-label="اوپر" onClick={() => act(() => R.moveField(cur, f.id, true))}>▲</button>
          <button className="icon small" aria-label="نیچے" onClick={() => act(() => R.moveField(cur, f.id, false))}>▼</button></div>
      </div>)}
      <Btn small kind="ghost" onClick={() => setEdit({ id: 0, garmentId: cur, label: '', unit: '', required: false, sortOrder: 0, active: true })}>+ نیا ناپ کا خانہ</Btn>
    </Section>}
    {edit && <FieldDialog f={edit} onClose={() => setEdit(null)} />}
  </Page>
}

function FieldDialog({ f, onClose }: { f: MeasurementField; onClose: () => void }) {
  const [x, setX] = useState(f)
  return <Modal title={f.id ? 'ناپ کا خانہ' : 'نیا ناپ کا خانہ'} onClose={onClose}
    actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn><Btn onClick={() => act(async () => { await R.saveField({ ...x, label: x.label.trim(), unit: x.unit.trim() }); onClose() }, 'محفوظ')}>محفوظ</Btn></>}>
    <Input label="نام (مثلاً لمبائی)" value={x.label} onChange={v => setX({ ...x, label: v })} />
    <Input label="الگ اکائی (اختیاری)" value={x.unit} onChange={v => setX({ ...x, unit: v })} />
    <Switch label="لازمی خانہ" checked={x.required} onChange={v => setX({ ...x, required: v })} />
    <Switch label="فعال" checked={x.active} onChange={v => setX({ ...x, active: v })} />
  </Modal>
}

export function Rates() {
  useStoreVersion()
  const [tab, setTab] = useState(0)
  const [rateFor, setRateFor] = useState<Garment | null>(null)
  const [custom, setCustom] = useState<CustomRate | null>(null)
  const [service, setService] = useState<ExtraService | null>(null)
  const garments = Q.garments()
  const gmap = new Map(garments.map(g => [g.id, g]))
  const pairs = Q.membersWithCustomers()
  const members = new Map(pairs.map(([m, c]) => [m.id, [m, c] as const]))
  const customers = new Map(pairs.map(([, c]) => [c.id, c]))
  return <Page title="ریٹ ماسٹر">
    <Tabs tabs={['معیاری ریٹ', 'خصوصی ریٹ', 'اضافی سروسز', 'ریٹ ہسٹری']} value={tab} onChange={setTab} />
    {tab === 0 && <>
      <p className="muted">ہر کپڑے کا معیاری ریٹ۔ تبدیلی صرف نئی بکنگ پر لاگو ہوگی۔</p>
      {Gender.all.map(gen => { const gl = garments.filter(g => g.gender === gen); if (!gl.length) return null
        return <Section key={gen} title={Gender.label(gen)}>{gl.map(g => <button key={g.id} className="list-row" onClick={() => setRateFor(g)}>
          <div><b>{g.name}{!g.active ? ' (غیر فعال)' : ''}</b></div><div className="end"><b>{Fmt.money(g.defaultRate)}</b></div></button>)}</Section> })}
    </>}
    {tab === 1 && <>
      <p className="muted">کسی خاص کسٹمر یا فیملی ممبر کے لیے الگ ریٹ۔ ترتیب: ممبر کا ریٹ ← کسٹمر کا ریٹ ← معیاری ریٹ۔</p>
      <Btn wide onClick={() => setCustom({ id: 0, garmentId: 0, customerId: null, memberId: null, rate: 0, active: true, note: '', updatedAt: 0 })}>+ نیا خصوصی ریٹ</Btn>
      <div className="list">{Q.customRates().map(r => <button key={r.id} className="list-row" onClick={() => setCustom(r)}>
        <div><b>{gmap.get(r.garmentId)?.name ?? '-'}</b>
          <small>{r.memberId != null ? 'ممبر: ' + (members.get(r.memberId)?.[0].name ?? '-') + ' (' + (members.get(r.memberId)?.[1].name ?? '-') + ')' : 'کسٹمر: ' + (customers.get(r.customerId ?? 0)?.name ?? '-')}{!r.active ? ' • غیر فعال' : ''}</small>
          {r.note && <small>{r.note}</small>}</div><div className="end"><b>{Fmt.money(r.rate)}</b></div></button>)}</div>
    </>}
    {tab === 2 && <>
      <p className="muted">ارجنٹ، کڑھائی، آلٹریشن وغیرہ کے چارجز۔ بکنگ میں ایک بٹن سے شامل ہوتے ہیں۔</p>
      <Btn wide onClick={() => setService({ id: 0, name: '', kind: 'OTHER', amount: 0, active: true })}>+ نئی سروس</Btn>
      <div className="list">{Q.services().map(s => <button key={s.id} className="list-row" onClick={() => setService(s)}>
        <div><b>{s.name}{!s.active ? ' (غیر فعال)' : ''}</b><small>{ServiceKind.label(s.kind)}</small></div><div className="end"><b>{Fmt.money(s.amount)}</b></div></button>)}</div>
    </>}
    {tab === 3 && <div className="list">{Q.rateHistory().map(h => <div key={h.id} className="list-row static"><div>
      <b>{gmap.get(h.garmentId)?.name ?? '-'} • {Fmt.dateTime(h.at)}</b>
      <small>{(h.memberId != null ? members.get(h.memberId)?.[0].name : h.customerId != null ? customers.get(h.customerId)?.name : 'معیاری') ?? '-'}: {Fmt.money(h.oldRate)} ← {Fmt.money(h.newRate)} • {h.note}</small></div></div>)}
      {Q.rateHistory().length === 0 && <Empty text="کوئی تبدیلی نہیں" />}</div>}
    {rateFor && <RateDialog g={rateFor} onClose={() => setRateFor(null)} />}
    {custom && <CustomRateDialog r={custom} garments={garments} pairs={pairs} onClose={() => setCustom(null)} />}
    {service && <ServiceDialog s={service} onClose={() => setService(null)} />}
  </Page>
}

function RateDialog({ g, onClose }: { g: Garment; onClose: () => void }) {
  const [t, setT] = useState(Calc.moneyInput(g.defaultRate))
  return <Modal title={'ریٹ: ' + g.name} onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn>
    <Btn onClick={() => act(async () => { const r = Calc.parseMoney(t); if (r == null) throw new UserError('ریٹ درست نہیں'); await R.saveGarment({ ...g, defaultRate: r }); onClose() }, 'ریٹ تبدیل ہو گیا')}>محفوظ</Btn></>}>
    <MoneyInput label="نیا ریٹ (روپے)" value={t} onChange={setT} />
  </Modal>
}

function CustomRateDialog({ r, garments, pairs, onClose }: { r: CustomRate; garments: Garment[]; pairs: [FamilyMember, Customer][]; onClose: () => void }) {
  const [garment, setGarment] = useState<Garment | null>(garments.find(g => g.id === r.garmentId) ?? null)
  const [cust, setCust] = useState<Customer | null>(pairs.find(([m, c]) => c.id === r.customerId || m.id === r.memberId)?.[1] ?? null)
  const [mem, setMem] = useState<FamilyMember | null>(pairs.find(([m]) => m.id === r.memberId)?.[0] ?? null)
  const [amount, setAmount] = useState(r.id ? Calc.moneyInput(r.rate) : '')
  const [note, setNote] = useState(r.note)
  const [active, setActive] = useState(r.active)
  const [picking, setPicking] = useState(false)
  const ms = pairs.filter(([, c]) => c.id === cust?.id).map(([m]) => m)
  return <Modal title="خصوصی ریٹ" onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn><Btn onClick={() => act(async () => {
    if (!garment) throw new UserError('کپڑا منتخب کریں'); if (!cust) throw new UserError('کسٹمر منتخب کریں')
    const a = Calc.parseMoney(amount); if (a == null) throw new UserError('ریٹ درست نہیں')
    await R.saveCustomRate({ ...r, garmentId: garment.id, customerId: cust.id, memberId: mem?.id ?? null, rate: a, note, active }); onClose()
  }, 'خصوصی ریٹ محفوظ')}>محفوظ</Btn></>}>
    <Btn kind="ghost" wide onClick={() => setPicking(true)}>کسٹمر: {cust?.name ?? 'منتخب کریں'}</Btn>
    <Select label="فیملی ممبر (اختیاری)" options={[null, ...ms]} value={mem} keyOf={x => String(x?.id ?? '')} labelOf={x => (x ? x.name + ' — ' + x.relation : 'تمام افراد')} onChange={setMem} />
    <Select label="کپڑا" options={garments} value={garment} keyOf={x => String(x.id)} labelOf={x => x.name} onChange={setGarment} />
    <MoneyInput label="ریٹ (روپے)" value={amount} onChange={setAmount} />
    <Input label="نوٹ" value={note} onChange={setNote} />
    <Switch label="فعال" checked={active} onChange={setActive} />
    {picking && <CustomerPicker onClose={() => setPicking(false)} onPick={c => { setCust(c); setMem(null); setPicking(false) }} />}
  </Modal>
}

function ServiceDialog({ s, onClose }: { s: ExtraService; onClose: () => void }) {
  const [x, setX] = useState(s)
  const [amount, setAmount] = useState(s.id ? Calc.moneyInput(s.amount) : '')
  return <Modal title="سروس" onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn><Btn onClick={() => act(async () => {
    const a = Calc.parseMoney(amount); if (a == null) throw new UserError('رقم درست نہیں'); await R.saveService({ ...x, name: x.name.trim(), amount: a }); onClose()
  }, 'سروس محفوظ')}>محفوظ</Btn></>}>
    <Input label="نام" value={x.name} onChange={v => setX({ ...x, name: v })} />
    <Select label="قسم" options={ServiceKind.all} value={x.kind} labelOf={ServiceKind.label} onChange={v => setX({ ...x, kind: v })} />
    <MoneyInput label="رقم (روپے)" value={amount} onChange={setAmount} />
    <Switch label="فعال" checked={x.active} onChange={v => setX({ ...x, active: v })} />
  </Modal>
}


