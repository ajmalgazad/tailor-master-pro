import { useEffect, useState, useSyncExternalStore } from 'react'
import { cloudConfigured, cloudCreateShop, cloudInit, cloudOpen, cloudSignIn, cloudState, onCloudState } from './cloud'
import { DemoBackend } from './demo'
import { R } from './repo'
import { store } from './store'
import { act, Btn, Confirm, Input, LangSwitch, MessageHost, NavCtx, useNavigator, useStoreVersion } from './ui/kit'
import type { Screen } from './ui/kit'
import { Home, MENU } from './ui/screens/Home'
import { Customers, CustomerEdit, CustomerProfile, MemberEdit, FamilyMembers, Merge } from './ui/screens/Customers'
import { Measurements, ProfileEdit, ProfileView } from './ui/screens/Measurements'
import { Garments, GarmentEdit, Rates } from './ui/screens/Masters'
import { Booking } from './ui/screens/Booking'
import { Orders, OrderDetail, Deliveries, Deliver } from './ui/screens/Orders'
import { Payments, Ledger, Expenses } from './ui/screens/Money'
import { PrintCenter, Preview, Designer } from './ui/screens/Print'
import { Reports, Report, Settings, ShopSettingsScreen, Backup, Audit } from './ui/screens/Admin'
import type { ReportKey } from './reports'
import type { OrderDraft } from './repo'

/* eslint-disable @typescript-eslint/no-explicit-any */
function Router({ s }: { s: Screen }) {
  const p = s as any
  switch (s.name) {
    case 'home': return <Home />
    case 'customers': return <Customers />
    case 'customerEdit': return <CustomerEdit id={p.id} />
    case 'customer': return <CustomerProfile id={p.id} tab={p.tab} />
    case 'memberEdit': return <MemberEdit customerId={p.customerId} memberId={p.memberId} />
    case 'family': return <FamilyMembers />
    case 'merge': return <Merge />
    case 'measurements': return <Measurements pick={p.pick} />
    case 'profileEdit': return <ProfileEdit customerId={p.customerId} memberId={p.memberId} garmentId={p.garmentId} profileId={p.profileId} mode={p.mode} returnAfterSave={p.returnAfterSave} />
    case 'profile': return <ProfileView id={p.id} />
    case 'garments': return <Garments />
    case 'garmentEdit': return <GarmentEdit id={p.id} />
    case 'rates': return <Rates />
    case 'booking': return <Booking draft={p.draft as OrderDraft | undefined} customerId={p.customerId} memberId={p.memberId} />
    case 'orders': return <Orders filter={p.filter} />
    case 'order': return <OrderDetail id={p.id} />
    case 'deliveries': return <Deliveries />
    case 'deliver': return <Deliver orderId={p.orderId} />
    case 'payments': return <Payments customerId={p.customerId} orderId={p.orderId} />
    case 'ledger': return <Ledger customerId={p.customerId} />
    case 'expenses': return <Expenses />
    case 'printCenter': return <PrintCenter />
    case 'preview': return <Preview kind={p.kind} refId={p.refId} profileSlip={p.profileSlip} />
    case 'designer': return <Designer />
    case 'reports': return <Reports />
    case 'report': return <Report reportKey={p.key as ReportKey} />
    case 'settings': return <Settings />
    case 'shopSettings': return <ShopSettingsScreen />
    case 'backup': return <Backup />
    case 'audit': return <Audit />
    default: return <Home />
  }
}

function Shell() {
  useStoreVersion()
  const nav = useNavigator()
  const cur = nav.stack[nav.stack.length - 1]
  const cs = useSyncExternalStore(onCloudState, () => `${cloudState.online}|${cloudState.pending}|${cloudState.error}`)
  void cs
  const demo = store.backend?.kind === 'demo'
  return <NavCtx.Provider value={nav}>
    <div className="shell">
      <aside className="side">
        <div className="brand">ٹیلر ماسٹر پرو</div>
        <button className={cur.name === 'home' ? 'on' : ''} onClick={() => nav.jump({ name: 'home' })}>ڈیش بورڈ</button>
        {MENU.map(m => <button key={m.screen} className={cur.name === m.screen ? 'on' : ''} onClick={() => nav.jump({ name: m.screen })}>{m.title}</button>)}
        <LangSwitch compact />
      </aside>
      <div className="main">
        {demo && <div className="banner warn">ڈیمو موڈ: ڈیٹا صرف اس براؤزر میں ہے، فون سے نہیں جڑا۔</div>}
        {!demo && (!cloudState.online || cloudState.error) && <div className={'banner ' + (cloudState.error ? 'bad' : 'warn')}>
          {cloudState.error || 'انٹرنیٹ نہیں ہے۔ کام جاری رکھیں، تبدیلیاں محفوظ ہیں اور انٹرنیٹ آتے ہی سنک ہو جائیں گی۔'}</div>}
        <Router key={nav.stack.length + ':' + cur.name} s={cur} />
      </div>
    </div>
    <MessageHost />
  </NavCtx.Provider>
}

function Login({ onDemo }: { onDemo: () => void }) {
  const [email, setEmail] = useState('')
  const [pw, setPw] = useState('')
  const [busy, setBusy] = useState(false)
  return <div className="login">
    <LangSwitch compact />
    <h1>ٹیلر ماسٹر پرو</h1>
    <p>دکان کے اکاؤنٹ سے لاگ اِن کریں۔ وہی اکاؤنٹ جو فون ایپ میں (سیٹنگز ← کلاؤڈ سنک) استعمال ہوتا ہے۔</p>
    <form onSubmit={e => { e.preventDefault(); setBusy(true); act(() => cloudSignIn(email, pw)).finally(() => setBusy(false)) }}>
      <Input label="ای میل" value={email} onChange={setEmail} type="email" />
      <Input label="پاس ورڈ" value={pw} onChange={setPw} type="password" />
      {cloudState.error && <p className="bad">{cloudState.error}</p>}
      <Btn wide type="submit" disabled={busy || !email || !pw}>{busy ? 'انتظار کریں...' : 'لاگ اِن'}</Btn>
    </form>
    <button className="linkish" onClick={onDemo}>بغیر اکاؤنٹ ڈیمو آزمائیں</button>
    <MessageHost />
  </div>
}

function NeedsShop() {
  const [ask, setAsk] = useState(false)
  return <div className="login">
    <h1>اس اکاؤنٹ میں ابھی کوئی دکان نہیں</h1>
    <p>اگر آپ کی دکان کا ڈیٹا فون ایپ میں ہے تو پہلے <b>فون ایپ ← سیٹنگز ← کلاؤڈ سنک</b> سے اسی اکاؤنٹ میں لاگ اِن کریں۔ فون کا سارا ڈیٹا کلاؤڈ پر آ جائے گا، پھر یہاں دوبارہ چیک کریں۔</p>
    <Btn wide onClick={() => location.reload()}>دوبارہ چیک کریں</Btn>
    <button className="linkish" onClick={() => setAsk(true)}>فون کے بغیر نئی دکان یہاں سے شروع کریں</button>
    {ask && <Confirm title="نئی دکان شروع کریں؟" text={'دکان نئے سرے سے کلاؤڈ میں بنے گی۔\n\nدھیان دیں: اگر بعد میں فون ایپ کو اس اکاؤنٹ سے جوڑا تو فون کا موجودہ ڈیٹا ہٹ کر کلاؤڈ والا ڈیٹا آئے گا۔'}
      okText="نئی دکان شروع کریں" onClose={() => setAsk(false)} onOk={() => act(cloudCreateShop)} />}
    <MessageHost />
  </div>
}

export function App() {
  const phase = useSyncExternalStore(onCloudState, () => cloudState.phase + '|' + cloudState.error)
  const [mode, setMode] = useState<'boot' | 'demo' | 'cloud'>('boot')
  const ready = useSyncExternalStore(f => store.subscribe(f), () => store.ready)

  useEffect(() => {
    if (!cloudConfigured()) { DemoBackend.start(); setMode('demo'); return }
    cloudInit(u => { if (u) { setMode('cloud'); cloudOpen(u) } })
    setMode('cloud')
  }, [])

  useEffect(() => { if (ready && store.backend) act(() => R.seedIfNeeded()) }, [ready])

  const startDemo = () => { DemoBackend.start(); setMode('demo') }
  if (mode === 'boot') return <div className="login"><p>لوڈ ہو رہا ہے...</p></div>
  if (mode === 'cloud') {
    const p = phase.split('|')[0]
    if (p === 'signedOut' || p === 'off') return <Login onDemo={startDemo} />
    if (p === 'needsShop') return <NeedsShop />
    if (p === 'loading' || !ready) return <div className="login"><p>دکان کا ڈیٹا آ رہا ہے...</p></div>
  }
  return <Shell />
}
