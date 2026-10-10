import { useState } from 'react'
import { Calc } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R, UserError } from '../../repo'
import type { OrderRow } from '../../repo'
import { OrderStatus, ReceiptKind } from '../../types'
import type { Customer, FamilyMember, MeasurementProfile } from '../../types'
import { act, Btn, Chip, Confirm, CustomerPicker, DateInput, Empty, Input, KV, matchesCustomer, Modal, MoneyInput, Page, Row, SearchBox, Section, Select, statusTone, Switch, Tabs, useNav, useStoreVersion } from '../kit'

export function Customers() {
  useStoreVersion()
  const nav = useNav()
  const [q, setQ] = useState('')
  const [inactive, setInactive] = useState(false)
  const list = Q.customerRows(inactive).filter(r => matchesCustomer(r.customer, q))
  return <Page title="کسٹمر ماسٹر" actions={<><Btn small kind="ghost" onClick={() => nav.go({ name: 'merge' })}>ضم کریں</Btn><Btn small onClick={() => nav.go({ name: 'customerEdit' })}>+ نیا کسٹمر</Btn></>}>
    <SearchBox value={q} onChange={setQ} placeholder="نام، موبائل، کوڈ یا پتہ سے تلاش" />
    <Switch label="غیر فعال کسٹمر بھی دکھائیں" checked={inactive} onChange={setInactive} />
    <p className="muted">کل: {Fmt.num(list.length)}</p>
    <div className="list">
      {list.map(r => <button key={r.customer.id} className="list-row" onClick={() => nav.go({ name: 'customer', id: r.customer.id })}>
        <div><b>{r.customer.name}{!r.customer.active ? ' (غیر فعال)' : ''}</b>
          <small>{Fmt.customerCode(r.customer.id)} • {Fmt.d(r.customer.mobile)}{r.customer.area ? ' • ' + r.customer.area : ''} • فیملی ممبرز: {Fmt.num(r.members)}</small></div>
        <div className="end"><small>بقایا</small><b className={r.balance > 0 ? 'bad' : 'ok'}>{Fmt.money(r.balance)}</b></div>
      </button>)}
      {list.length === 0 && <Empty text="کوئی کسٹمر نہیں ملا" />}
    </div>
  </Page>
}

const blankCustomer = (): Customer => ({ id: 0, name: '', fatherName: '', mobile: '', whatsapp: '', address: '', city: '', area: '', category: '', regDate: Date.now(), openingBalance: 0, notes: '', active: true, mergedIntoId: null, sample: false })

export function CustomerEdit({ id }: { id?: number }) {
  const nav = useNav()
  const st = nav.state(() => { const c = id ? Q.customer(id) : undefined; return { c: c ? { ...c } : blankCustomer(), opening: c && c.openingBalance ? Calc.moneyInput(c.openingBalance) : '' } })
  const [c, setC] = useState<Customer>(st.c)
  const [opening, setOpening] = useState(st.opening)
  const [dups, setDups] = useState<Customer[]>([])
  const up = (p: Partial<Customer>) => { const n = { ...c, ...p }; st.c = n; setC(n) }
  const save = () => act(async () => {
    const ob = Calc.parseMoney(opening); if (ob == null) throw new UserError('ابتدائی بیلنس درست نہیں')
    const newId = await R.saveCustomer({ ...c, openingBalance: ob })
    if (!id) nav.replace({ name: 'customer', id: newId }); else nav.back()
  }, 'کسٹمر محفوظ ہو گیا')
  return <Page title={id ? 'کسٹمر میں ترمیم' : 'نیا کسٹمر'}>
    <Section>
      {id ? <p><b>کسٹمر کوڈ: {Fmt.customerCode(id)}</b></p> : null}
      <div className="grid2">
        <Input label="کسٹمر کا نام *" value={c.name} onChange={v => up({ name: v })} />
        <Input label="والد کا نام" value={c.fatherName} onChange={v => up({ fatherName: v })} />
        <Input label="موبائل نمبر" value={c.mobile} onChange={v => up({ mobile: v })} inputMode="tel" />
        <Input label="واٹس ایپ نمبر" value={c.whatsapp} onChange={v => up({ whatsapp: v })} inputMode="tel" />
      </div>
      <Btn small kind="ghost" onClick={() => up({ whatsapp: c.mobile })}>واٹس ایپ = موبائل</Btn>
      <Input label="مکمل پتہ" value={c.address} onChange={v => up({ address: v })} multiline />
      <div className="grid2">
        <Input label="شہر" value={c.city} onChange={v => up({ city: v })} />
        <Input label="علاقہ" value={c.area} onChange={v => up({ area: v })} />
        <Select label="کسٹمر کی قسم" options={['عام', 'مستقل', 'وی آئی پی', 'ادارہ / اسکول', 'دیگر']} value={c.category || null} labelOf={x => x} onChange={v => up({ category: v })} />
        <DateInput label="رجسٹریشن تاریخ" value={c.regDate} onChange={v => up({ regDate: v })} />
      </div>
      <MoneyInput label="ابتدائی بیلنس (پرانا بقایا)" value={opening} onChange={v => { st.opening = v; setOpening(v) }} />
      <Input label="نوٹس" value={c.notes} onChange={v => up({ notes: v })} multiline />
      <Switch label="فعال" checked={c.active} onChange={v => up({ active: v })} />
      <Btn wide onClick={() => act(() => {
        if (!c.name.trim()) throw new UserError('کسٹمر کا نام لازمی ہے')
        const d = Q.findDuplicates(c); if (d.length) setDups(d); else save()
      })}>محفوظ کریں</Btn>
    </Section>
    {dups.length > 0 && <Modal title="ملتا جلتا کسٹمر پہلے سے موجود ہے" onClose={() => setDups([])}
      actions={<><Btn kind="ghost" onClick={() => setDups([])}>واپس</Btn><Btn onClick={() => { setDups([]); save() }}>پھر بھی نیا محفوظ کریں</Btn></>}>
      <p>یہ کسٹمر شاید پہلے سے رجسٹرڈ ہے:</p>
      {dups.map(d => <button key={d.id} className="pick" onClick={() => { setDups([]); nav.replace({ name: 'customer', id: d.id }) }}><b>{d.name}</b><small>{Fmt.d(d.mobile)} • {Fmt.customerCode(d.id)}</small></button>)}
    </Modal>}
  </Page>
}

export function ProfileCard({ p, member, garment, onClick }: { p: MeasurementProfile; member: string; garment: string; onClick: () => void }) {
  return <button className={'list-row' + (p.archived ? ' dim' : '')} onClick={onClick}>
    <div><b>{p.name}</b><small>{garment} • {member}</small>
      <small>تاریخ: {Fmt.date(p.date)} • {p.unit}{p.lastUsed ? ' • آخری استعمال: ' + Fmt.date(p.lastUsed) : ''}</small></div>
    <div className="end">{p.isDefault && <Chip text="ترجیحی ★" tone="ok" />}{p.archived && <Chip text="پرانا ورژن" tone="warn" />}</div>
  </button>
}

export function OrderRowCard({ r, onClick }: { r: OrderRow; onClick: () => void }) {
  return <button className={'list-row' + (r.overdue ? ' overdue' : '')} onClick={onClick}>
    <div><b>{Fmt.orderNo(r.order.id)}{r.order.urgent ? ' ⚡ارجنٹ' : ''}{r.order.isDraft ? ' (ڈرافٹ)' : ''}</b>
      <small>{r.customerName} • {r.memberName} • {Fmt.d(r.customerMobile)}</small>
      <small className={r.overdue ? 'bad' : ''}>بکنگ: {Fmt.date(r.order.bookingDate)} • ڈیلیوری: {Fmt.date(r.order.deliveryDate)}{r.overdue ? ' • تاخیر!' : ''}</small>
      <small>کپڑے: {Fmt.num(r.itemCount)} (باقی {Fmt.num(r.pendingItems)}) • کل {Fmt.money(r.order.netAmount)} • بقایا {Fmt.money(r.outstanding)}</small></div>
    <div className="end"><Chip text={OrderStatus.label(r.order.status)} tone={statusTone(r.order.status)} /></div>
  </button>
}

export function CustomerProfile({ id, tab: initialTab = 0 }: { id: number; tab?: number }) {
  useStoreVersion()
  const nav = useNav()
  const [tab, setTab] = useState(initialTab)
  const [askDeact, setAskDeact] = useState(false)
  const [archived, setArchived] = useState(false)
  const c = Q.customer(id)
  if (!c) return <Page title="کسٹمر"><Empty text="کسٹمر نہیں ملا" /></Page>
  const bal = Q.balance(id)
  const members = Q.members(id)
  const profiles = Q.profilesForCustomer(id)
  const orders = Q.orderRows().filter(r => r.order.customerId === id)
  const gmap = new Map(Q.garments().map(g => [g.id, g.name]))
  const mmap = new Map(members.map(m => [m.id, m.name]))
  return <Page title={c.name} actions={<Btn small kind="ghost" onClick={() => nav.go({ name: 'customerEdit', id })}>ترمیم</Btn>}>
    <Section>
      {c.mergedIntoId != null && <p className="warn">یہ ریکارڈ کسی دوسرے کسٹمر میں ضم ہو چکا ہے</p>}
      <KV k="کوڈ" v={Fmt.customerCode(c.id)} bold />
      {c.fatherName && <KV k="ولدیت" v={c.fatherName} />}
      {c.mobile && <KV k="موبائل" v={Fmt.d(c.mobile)} />}
      {c.whatsapp && <KV k="واٹس ایپ" v={Fmt.d(c.whatsapp)} />}
      {c.address && <KV k="پتہ" v={c.address} />}
      {(c.area || c.city) && <KV k="علاقہ" v={[c.area, c.city].filter(Boolean).join('، ')} />}
      {c.category && <KV k="قسم" v={c.category} />}
      <KV k="رجسٹریشن" v={Fmt.date(c.regDate)} />
      <KV k="حیثیت" v={c.active ? 'فعال' : 'غیر فعال'} tone={c.active ? 'ok' : 'bad'} />
      {c.notes && <p className="muted">نوٹس: {c.notes}</p>}
      <hr />
      <KV k="موجودہ بقایا" v={Fmt.money(bal)} bold tone={bal > 0 ? 'bad' : 'ok'} />
    </Section>
    <Row>
      <Btn kind="ok" onClick={() => nav.go({ name: 'booking', customerId: id })}>نئی بکنگ</Btn>
      <Btn onClick={() => nav.go({ name: 'payments', customerId: id })}>رقم وصول</Btn>
      <Btn kind="ghost" onClick={() => nav.go({ name: 'ledger', customerId: id })}>کھاتہ</Btn>
      <Btn kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.CUSTOMER, refId: id })}>پرنٹ معلومات</Btn>
      <Btn kind="ghost" onClick={() => c.active ? setAskDeact(true) : act(() => R.setCustomerActive(id, true), 'کسٹمر فعال ہو گیا')}>{c.active ? 'غیر فعال کریں' : 'فعال کریں'}</Btn>
    </Row>
    <Tabs tabs={['فیملی ممبرز', 'ناپ', 'آرڈرز']} value={tab} onChange={setTab} />
    {tab === 0 && <>
      <Btn wide onClick={() => nav.go({ name: 'memberEdit', customerId: id })}>+ نیا فیملی ممبر</Btn>
      <div className="list">{members.map(m => <div key={m.id} className="list-row static">
        <div><b>{m.name} — {m.relation}{!m.active ? ' (غیر فعال)' : ''}</b>
          <small>{Fmt.memberCode(m.id)}{m.ageGroup ? ' • ' + m.ageGroup : ''}{m.mobile ? ' • ' + Fmt.d(m.mobile) : ''}</small>
          {m.fittingPref && <small>فٹنگ: {m.fittingPref}</small>}
          <small>ناپ: {Fmt.num(profiles.filter(p => p.memberId === m.id && !p.archived).length)} • آرڈرز: {Fmt.num(orders.filter(o => o.order.memberId === m.id).length)}</small>
          <Row><Btn small kind="ghost" onClick={() => nav.go({ name: 'profileEdit', customerId: id, memberId: m.id })}>نیا ناپ</Btn>
            <Btn small kind="ghost" onClick={() => nav.go({ name: 'booking', customerId: id, memberId: m.id })}>اس کی بکنگ</Btn>
            <Btn small kind="ghost" onClick={() => nav.go({ name: 'memberEdit', customerId: id, memberId: m.id })}>ترمیم</Btn></Row>
        </div></div>)}</div>
    </>}
    {tab === 1 && <>
      <Switch label="پرانے ورژن بھی دکھائیں" checked={archived} onChange={setArchived} />
      <div className="list">{profiles.filter(p => archived || !p.archived).map(p =>
        <ProfileCard key={p.id} p={p} member={mmap.get(p.memberId) ?? '-'} garment={gmap.get(p.garmentId) ?? '-'} onClick={() => nav.go({ name: 'profile', id: p.id })} />)}
        {profiles.length === 0 && <Empty text="ابھی کوئی ناپ محفوظ نہیں" />}</div>
    </>}
    {tab === 2 && <div className="list">{orders.map(r => <OrderRowCard key={r.order.id} r={r} onClick={() => nav.go({ name: 'order', id: r.order.id })} />)}
      {orders.length === 0 && <Empty text="ابھی کوئی آرڈر نہیں" />}</div>}
    {askDeact && <Confirm title="کسٹمر غیر فعال کریں؟" text="کسٹمر کا تمام ریکارڈ محفوظ رہے گا، صرف فہرست سے ہٹ جائے گا۔" okText="غیر فعال کریں" danger
      onClose={() => setAskDeact(false)} onOk={() => act(() => R.setCustomerActive(id, false), 'کسٹمر غیر فعال ہو گیا')} />}
  </Page>
}

const relations = ['ذاتی استعمال', 'والد', 'والدہ', 'بھائی', 'بہن', 'بیٹا', 'بیٹی', 'شوہر', 'بیوی', 'دادا', 'پوتا', 'بھتیجا', 'دیگر']

export function MemberEdit({ customerId, memberId }: { customerId: number; memberId?: number }) {
  const nav = useNav()
  const init = memberId ? Q.member(memberId) : undefined
  const [m, setM] = useState<FamilyMember>(init ? { ...init } : { id: 0, customerId, name: '', relation: 'بیٹا', ageGroup: '', mobile: '', fittingPref: '', notes: '', isSelf: false, active: true })
  const [custom, setCustom] = useState(init && !relations.includes(init.relation) ? init.relation : '')
  const up = (p: Partial<FamilyMember>) => setM({ ...m, ...p })
  const isOther = m.relation === 'دیگر' || !relations.includes(m.relation)
  return <Page title={memberId ? 'فیملی ممبر میں ترمیم' : 'نیا فیملی ممبر'}>
    <Section>
      {memberId && <p><b>ممبر کوڈ: {Fmt.memberCode(memberId)}</b></p>}
      <Input label="نام یا عرفیت *" value={m.name} onChange={v => up({ name: v })} />
      <Select label="رشتہ" options={relations} value={relations.includes(m.relation) ? m.relation : 'دیگر'} labelOf={x => x} onChange={v => up({ relation: v })} />
      {isOther && <Input label="رشتہ (اپنا لکھیں)" value={custom} onChange={setCustom} />}
      <Select label="عمر کا گروپ (اختیاری)" options={['بچہ', 'نوجوان', 'بالغ', 'بزرگ']} value={m.ageGroup || null} labelOf={x => x} onChange={v => up({ ageGroup: v })} />
      <Input label="موبائل (اختیاری)" value={m.mobile} onChange={v => up({ mobile: v })} inputMode="tel" />
      <Input label="فٹنگ کی پسند (مثلاً کھلی، تنگ، سلم)" value={m.fittingPref} onChange={v => up({ fittingPref: v })} multiline />
      <Input label="نوٹس" value={m.notes} onChange={v => up({ notes: v })} multiline />
      <Switch label="فعال" checked={m.active} onChange={v => up({ active: v })} />
      <Btn wide onClick={() => act(async () => {
        await R.saveMember({ ...m, relation: isOther && custom.trim() ? custom.trim() : m.relation }); nav.back()
      }, 'فیملی ممبر محفوظ ہو گیا')}>محفوظ کریں</Btn>
    </Section>
  </Page>
}

export function FamilyMembers() {
  useStoreVersion()
  const nav = useNav()
  const [q, setQ] = useState('')
  const [pick, setPick] = useState(false)
  const t = q.trim()
  const list = Q.membersWithCustomers().filter(([m, c]) => !t || m.name.includes(t) || m.relation.includes(t) || matchesCustomer(c, t))
  return <Page title="فیملی ممبرز" actions={<Btn small onClick={() => setPick(true)}>+ نیا ممبر</Btn>}>
    <SearchBox value={q} onChange={setQ} placeholder="ممبر یا کسٹمر کا نام / موبائل" />
    <p className="muted">ایک کسٹمر کے کئی افراد ہو سکتے ہیں۔ ہر فرد کا ناپ الگ رہتا ہے۔</p>
    <div className="list">{list.map(([m, c]) => <button key={m.id} className="list-row" onClick={() => nav.go({ name: 'customer', id: m.customerId })}>
      <div><b>{m.name} — {m.relation}</b><small>کسٹمر: {c.name} • {Fmt.memberCode(m.id)}</small></div></button>)}
      {list.length === 0 && <Empty text="کوئی ممبر نہیں ملا" />}</div>
    {pick && <CustomerPicker onClose={() => setPick(false)} onPick={c => { setPick(false); nav.go({ name: 'memberEdit', customerId: c.id }) }} />}
  </Page>
}

export function Merge() {
  const nav = useNav()
  const [from, setFrom] = useState<Customer | null>(null)
  const [to, setTo] = useState<Customer | null>(null)
  const [picking, setPicking] = useState(0)
  const [ask, setAsk] = useState(false)
  return <Page title="ڈپلیکیٹ کسٹمر ضم کریں">
    <p>اگر ایک ہی کسٹمر غلطی سے دو بار رجسٹر ہو گیا ہو تو یہاں دونوں کو ایک کریں۔ تمام ممبرز، ناپ، آرڈرز اور ادائیگیاں صحیح ریکارڈ میں منتقل ہو جائیں گی۔ کچھ بھی ڈیلیٹ نہیں ہوگا۔</p>
    <Section title="۱۔ ڈپلیکیٹ (جو ختم کرنا ہے)"><p>{from ? `${from.name} • ${Fmt.d(from.mobile)} • ${Fmt.customerCode(from.id)}` : 'منتخب نہیں'}</p><Btn small kind="ghost" onClick={() => setPicking(1)}>منتخب کریں</Btn></Section>
    <Section title="۲۔ اصل ریکارڈ (جس میں ملانا ہے)"><p>{to ? `${to.name} • ${Fmt.d(to.mobile)} • ${Fmt.customerCode(to.id)}` : 'منتخب نہیں'}</p><Btn small kind="ghost" onClick={() => setPicking(2)}>منتخب کریں</Btn></Section>
    <Btn wide disabled={!from || !to || from.id === to.id} onClick={() => setAsk(true)}>ضم کریں</Btn>
    {picking > 0 && <CustomerPicker onClose={() => setPicking(0)} onPick={c => { if (picking === 1) setFrom(c); else setTo(c); setPicking(0) }} />}
    {ask && from && to && <Confirm title="کیا آپ کو یقین ہے؟" text={`${from.name} کا سارا ریکارڈ ${to.name} میں منتقل ہو جائے گا۔`} okText="ضم کریں" onClose={() => setAsk(false)}
      onOk={() => act(async () => { await R.mergeCustomers(from.id, to.id); nav.replace({ name: 'customer', id: to.id }) }, 'ریکارڈ ضم ہو گئے')} />}
  </Page>
}
