import { useState } from 'react'
import { Calc, Digits } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, decodeValues, encodeValues, UserError } from '../../repo'
import { store } from '../../store'
import { Gender, ReceiptKind } from '../../types'
import type { Customer, FamilyMember, Garment } from '../../types'
import { act, msg, Btn, Chip, CustomerPicker, DateInput, Empty, Input, KV, matchesCustomer, Modal, Page, Prompt, Row, SearchBox, Section, Select, Switch, useNav, useStoreVersion } from '../kit'
import { ProfileCard } from './Customers'

export function MemberPicker({ onClose, onPicked }: { onClose: () => void; onPicked: (c: Customer, m: FamilyMember) => void }) {
  const [c, setC] = useState<Customer | null>(null)
  if (!c) return <CustomerPicker onClose={onClose} onPick={setC} />
  return <Modal title={'فرد منتخب کریں — ' + c.name} onClose={onClose}>
    <div className="pick-list">{Q.members(c.id).filter(m => m.active).map(m =>
      <button key={m.id} className="pick" onClick={() => onPicked(c, m)}><b>{m.name} — {m.relation}</b></button>)}</div>
  </Modal>
}

export function Measurements({ pick }: { pick?: boolean }) {
  useStoreVersion()
  const nav = useNav()
  const [q, setQ] = useState('')
  const [g, setG] = useState<Garment | null>(null)
  const [picking, setPicking] = useState(!!pick)
  const gmap = new Map(Q.garments().map(x => [x.id, x]))
  const mc = new Map(Q.membersWithCustomers().map(([m, c]) => [m.id, [m, c] as const]))
  const t = q.trim()
  const list = Q.allProfiles().filter(p => !p.archived).filter(p => {
    const x = mc.get(p.memberId)
    return (!g || p.garmentId === g.id) && (!t || p.name.includes(t) || (gmap.get(p.garmentId)?.name ?? '').includes(t) || (x?.[0].name ?? '').includes(t) || (x ? matchesCustomer(x[1], t) : false))
  })
  return <Page title="ناپ ماسٹر" actions={<Btn small onClick={() => setPicking(true)}>+ نیا ناپ</Btn>}>
    <SearchBox value={q} onChange={setQ} placeholder="کسٹمر، فرد یا کپڑے سے تلاش" />
    <Select label="کپڑے کی قسم" options={[null, ...Q.garments()]} value={g} keyOf={x => String(x?.id ?? '')} labelOf={x => x?.name ?? 'تمام'} onChange={setG} />
    <div className="list">{list.map(p => { const x = mc.get(p.memberId)
      return <ProfileCard key={p.id} p={p} member={(x?.[0].name ?? '-') + ' (' + (x?.[1].name ?? '-') + ')'} garment={gmap.get(p.garmentId)?.name ?? '-'} onClick={() => nav.go({ name: 'profile', id: p.id })} /> })}
      {list.length === 0 && <Empty text="کوئی ناپ نہیں ملا" />}</div>
    {picking && <MemberPicker onClose={() => setPicking(false)} onPicked={(c, m) => { setPicking(false); nav.go({ name: 'profileEdit', customerId: c.id, memberId: m.id }) }} />}
  </Page>
}

interface ValueRow { fieldId: number; label: string; value: string; required: boolean; custom: boolean }

export function ProfileEdit({ customerId, memberId, garmentId, profileId, mode = 'new', returnAfterSave }:
  { customerId: number; memberId: number; garmentId?: number; profileId?: number; mode?: string; returnAfterSave?: boolean }) {
  useStoreVersion()
  const nav = useNav()
  const old = profileId ? Q.profile(profileId) : undefined
  const member = Q.member(memberId)
  const build = (gid: number | undefined, existing: { fieldId: number; label: string; value: string }[]): ValueRow[] => {
    if (!gid) return []
    const fields = Q.fields(gid).filter(f => f.active)
    const rows: ValueRow[] = fields.map(f => ({ fieldId: f.id, label: f.label + (f.unit ? ` (${f.unit})` : ''), value: existing.find(e => e.fieldId === f.id)?.value ?? '', required: f.required, custom: false }))
    existing.filter(e => !fields.some(f => f.id === e.fieldId)).forEach(e => rows.push({ fieldId: e.fieldId, label: e.label, value: e.value, required: false, custom: true }))
    return rows
  }
  const st = nav.state(() => {
    const gid = old?.garmentId ?? garmentId
    const g = gid ? Q.garment(gid) : undefined
    return {
      garmentId: gid, name: old?.name ?? g?.name ?? '', unit: old?.unit ?? store.shop.defaultUnit, date: Date.now(),
      fitting: old?.fitting ?? member?.fittingPref ?? '', instructions: old?.instructions ?? '', notes: old?.notes ?? '', isDefault: old?.isDefault ?? false,
      rows: build(gid, old ? decodeValues(old.valuesJson) : []),
    }
  })
  const [, force] = useState(0)
  const set = (p: Partial<typeof st>) => { Object.assign(st, p); force(x => x + 1) }
  const [newLabel, setNewLabel] = useState('')
  const garments = Q.garments().filter(x => x.active)
  const g = st.garmentId ? Q.garment(st.garmentId) : undefined

  return <Page title={mode === 'version' ? 'ناپ تبدیل کریں (نیا ورژن)' : 'نیا ناپ'}>
    <Section>
      <p><b>فرد: {member ? member.name + ' — ' + member.relation : '-'}</b></p>
      {!old ? <Select label="کپڑے کی قسم *" options={garments} value={g ?? null} keyOf={x => String(x.id)} labelOf={x => x.name + ' (' + Gender.label(x.gender) + ')'}
        onChange={x => set({ garmentId: x.id, name: !st.name || garments.some(y => y.name === st.name) ? x.name : st.name, rows: build(x.id, []) })} />
        : <><p><b>کپڑا: {g?.name ?? '-'}</b></p><p className="warn small">پرانا ناپ محفوظ رہے گا، تبدیلی نئے ورژن کے طور پر محفوظ ہوگی۔ پرانے آرڈرز پر کوئی اثر نہیں پڑے گا۔</p></>}
      <Input label="پروفائل کا نام *" value={st.name} onChange={v => set({ name: v })} />
      <div className="grid2">
        <Select label="اکائی" options={['انچ', 'سینٹی میٹر']} value={st.unit} labelOf={x => x} onChange={v => set({ unit: v })} />
        <DateInput label="تاریخ" value={st.date} onChange={v => set({ date: v })} />
      </div>
    </Section>
    {g && <Section title={`ناپ (${st.unit})`}>
      <div className="grid3">{st.rows.map((r, i) => <div key={i} className="mrow">
        <Input label={r.label + (r.required ? ' *' : '')} value={r.value} inputMode="decimal" onChange={v => { st.rows[i] = { ...r, value: v }; force(x => x + 1) }} />
        {r.custom && <button className="icon small" aria-label="ہٹائیں" onClick={() => { st.rows.splice(i, 1); force(x => x + 1) }}>✕</button>}
      </div>)}</div>
      <p className="muted small">اضافی ناپ شامل کریں (صرف اس پروفائل کے لیے)</p>
      <Row><Input label="ناپ کا نام" value={newLabel} onChange={setNewLabel} />
        <Btn small kind="ghost" onClick={() => { if (newLabel.trim()) { st.rows.push({ fieldId: 0, label: newLabel.trim(), value: '', required: false, custom: true }); setNewLabel('') } }}>+ شامل</Btn></Row>
    </Section>}
    <Section>
      <Input label="فٹنگ کی پسند" value={st.fitting} onChange={v => set({ fitting: v })} />
      <Input label="خصوصی ہدایات" value={st.instructions} onChange={v => set({ instructions: v })} multiline />
      <Input label="نوٹس" value={st.notes} onChange={v => set({ notes: v })} multiline />
      <Switch label="اس کپڑے کے لیے ترجیحی (ڈیفالٹ) ناپ" checked={st.isDefault} onChange={v => set({ isDefault: v })} />
      <Btn wide onClick={() => act(async () => {
        if (!g) throw new UserError('کپڑے کی قسم منتخب کریں')
        if (!member) throw new UserError('فرد نہیں ملا')
        const bad = st.rows.find(r => r.value.trim() && Calc.parseDouble(r.value) == null && !r.value.includes('/'))
        if (bad) msg.warn(`'${bad.label}' میں نمبر کے علاوہ متن ہے`)
        const values = st.rows.map(r => ({ fieldId: r.fieldId, label: r.label.split(' (')[0].trim() || r.label, value: Digits.toLatin(r.value.trim()) }))
        const p = { customerId: member.customerId, memberId: member.id, garmentId: g.id, name: st.name.trim(), date: st.date, unit: st.unit, valuesJson: encodeValues(values),
          fitting: st.fitting, instructions: st.instructions, notes: st.notes, isDefault: st.isDefault, archived: false, previousId: null, lastUsed: null }
        const id = old ? await R.saveProfileVersion(old, p) : await R.saveNewProfile(p)
        if (returnAfterSave) nav.back(); else nav.replace({ name: 'profile', id })
      }, 'ناپ محفوظ ہو گیا')}>محفوظ کریں</Btn>
    </Section>
  </Page>
}

export function ProfileView({ id }: { id: number }) {
  useStoreVersion()
  const nav = useNav()
  const [dupFor, setDupFor] = useState<FamilyMember | null>(null)
  const [pickMember, setPickMember] = useState(false)
  const p = Q.profile(id)
  if (!p) return <Page title="ناپ پروفائل"><Empty text="پروفائل نہیں ملا" /></Page>
  const m = Q.member(p.memberId), g = Q.garment(p.garmentId)
  const history = Q.profileHistory(p)
  return <Page title="ناپ پروفائل">
    <Section>
      <Row><h2 style={{ flex: 1 }}>{p.name}</h2>{p.isDefault && <Chip text="ترجیحی ★" tone="ok" />}{p.archived && <Chip text="پرانا ورژن" tone="warn" />}</Row>
      <KV k="پروفائل نمبر" v={Fmt.profileCode(p.id)} />
      <KV k="فرد" v={m ? m.name + ' — ' + m.relation : '-'} />
      <KV k="کپڑا" v={g?.name ?? '-'} />
      <KV k="تاریخ" v={Fmt.date(p.date)} />
      <KV k="اکائی" v={p.unit} />
      <KV k="آخری استعمال" v={p.lastUsed ? Fmt.date(p.lastUsed) : 'ابھی استعمال نہیں ہوا'} />
    </Section>
    <Section title="ناپ">
      <div className="grid2">{decodeValues(p.valuesJson).map((v, i) => <KV key={i} k={v.label} v={v.value ? Fmt.d(v.value) : '-'} bold />)}</div>
      {p.fitting && <KV k="فٹنگ" v={p.fitting} />}
      {p.instructions && <p>ہدایات: {p.instructions}</p>}
      {p.notes && <p>نوٹس: {p.notes}</p>}
    </Section>
    <Row>
      {!p.archived && <Btn onClick={() => nav.go({ name: 'profileEdit', customerId: p.customerId, memberId: p.memberId, garmentId: p.garmentId, profileId: p.id, mode: 'version' })}>ناپ تبدیل کریں (نیا ورژن)</Btn>}
      {!p.archived && <Btn kind="ghost" onClick={() => setPickMember(true)}>کاپی بنائیں</Btn>}
      {!p.archived && !p.isDefault && <Btn kind="ghost" onClick={() => act(() => R.setDefaultProfile(p), 'ترجیحی ناپ مقرر ہو گیا')}>ترجیحی بنائیں</Btn>}
      <Btn kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.MEASUREMENT, refId: p.id, profileSlip: true })}>ناپ کی پرچی پرنٹ</Btn>
      <Btn kind="ghost" onClick={() => act(() => R.archiveProfile(p, !p.archived), 'تبدیلی محفوظ')}>{p.archived ? 'دوبارہ فعال کریں' : 'آرکائیو کریں'}</Btn>
      <Btn kind="ghost" onClick={() => nav.go({ name: 'booking', customerId: p.customerId, memberId: p.memberId })}>اس فرد کی بکنگ</Btn>
    </Row>
    {history.length > 0 && <Section title="پرانے ورژن (ہسٹری)">
      {history.map(x => <button key={x.id} className="list-row" onClick={() => nav.go({ name: 'profile', id: x.id })}><div><b>{x.name}</b><small>{Fmt.date(x.date)}</small></div></button>)}
    </Section>}
    {pickMember && <Modal title="کاپی کس فرد کے لیے؟" onClose={() => setPickMember(false)}>
      <div className="pick-list">{Q.members(p.customerId).map(x => <button key={x.id} className="pick" onClick={() => { setPickMember(false); setDupFor(x) }}><b>{x.name} — {x.relation}</b></button>)}</div>
    </Modal>}
    {dupFor && <Prompt title="نئی کاپی کا نام" label="نام" initial={p.name + ' (کاپی)'} onClose={() => setDupFor(null)}
      onOk={n => act(async () => { const nid = await R.duplicateProfile(p, n.trim() || p.name + ' (کاپی)', dupFor.id); nav.replace({ name: 'profile', id: nid }) }, 'کاپی بن گئی')} />}
  </Page>
}
