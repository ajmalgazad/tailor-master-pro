import { useState } from 'react'
import { Dates } from '../../calc'
import { Fmt } from '../../fmt'
import { Q } from '../../repo'
import { store } from '../../store'
import { OrderStatus } from '../../types'
import { Btn, Chip, DateInput, Page, Section, useNav, useStoreVersion } from '../kit'

export const MENU: { n: number; title: string; screen: string; color: string }[] = [
  { n: 2, title: 'کسٹمر ماسٹر', screen: 'customers', color: '#1565c0' },
  { n: 3, title: 'فیملی ممبرز', screen: 'family', color: '#6a1b9a' },
  { n: 4, title: 'ناپ ماسٹر', screen: 'measurements', color: '#00838f' },
  { n: 5, title: 'کپڑوں کی اقسام', screen: 'garments', color: '#5d4037' },
  { n: 6, title: 'ریٹ ماسٹر', screen: 'rates', color: '#8a5a00' },
  { n: 7, title: 'نئی بکنگ', screen: 'booking', color: '#2e7d32' },
  { n: 8, title: 'آرڈر مینجمنٹ', screen: 'orders', color: '#283593' },
  { n: 9, title: 'ڈیلیوری مینجمنٹ', screen: 'deliveries', color: '#00695c' },
  { n: 10, title: 'پیمنٹ اور کھاتہ', screen: 'payments', color: '#ad1457' },
  { n: 11, title: 'اخراجات', screen: 'expenses', color: '#c62828' },
  { n: 12, title: 'رسید پرنٹنگ', screen: 'printCenter', color: '#37474f' },
  { n: 13, title: 'رپورٹس', screen: 'reports', color: '#4527a0' },
  { n: 14, title: 'بیک اپ اور سیٹنگز', screen: 'settings', color: '#455a64' },
]

function Stat({ title, value, tone, onClick }: { title: string; value: string; tone: string; onClick?: () => void }) {
  return <button className={'stat ' + tone} onClick={onClick} disabled={!onClick}><span>{title}</span><b>{value}</b></button>
}

export function Home() {
  useStoreVersion()
  const nav = useNav()
  const [day, setDay] = useState(Dates.today())
  const d = Q.dashboard(day)
  const isToday = day === Dates.today()
  return (
    <Page title={store.shop.shopName || 'ٹیلر ماسٹر پرو'} back={false}>
      <div className="row wrap" style={{ alignItems: 'end' }}>
        <div style={{ flex: 1, minWidth: 180 }}><DateInput label="تاریخ" value={day} onChange={v => setDay(Dates.startOfDay(v))} /></div>
        {!isToday && <Btn kind="ghost" small onClick={() => setDay(Dates.today())}>آج</Btn>}
      </div>
      <div className="stats">
        <Stat title="آج کی بکنگ" value={Fmt.num(d.todayBookings)} tone="blue" onClick={() => nav.go({ name: 'orders', filter: 'today' })} />
        <Stat title="آج کی ڈیلیوری" value={Fmt.num(d.todayDeliveries)} tone="teal" onClick={() => nav.go({ name: 'orders', filter: 'due_today' })} />
        <Stat title="جاری آرڈرز" value={Fmt.num(d.openOrders)} tone="amber" onClick={() => nav.go({ name: 'orders', filter: 'open' })} />
        <Stat title="تیار آرڈرز" value={Fmt.num(d.readyOrders)} tone="green" onClick={() => nav.go({ name: 'orders', filter: 'ready' })} />
        <Stat title="تاخیر والے آرڈرز" value={Fmt.num(d.overdueOrders)} tone="red" onClick={() => nav.go({ name: 'orders', filter: 'overdue' })} />
        <Stat title="کل کسٹمرز" value={Fmt.num(d.totalCustomers)} tone="purple" onClick={() => nav.go({ name: 'customers' })} />
        <Stat title={isToday ? 'آج کی وصولی' : 'اس دن کی وصولی'} value={Fmt.money(d.todayCollection)} tone="green" onClick={() => nav.go({ name: 'report', key: 'DAILY_COLLECTION' })} />
        <Stat title="ماہانہ وصولی" value={Fmt.money(d.monthCollection)} tone="teal" />
        <Stat title="کل بقایا رقم" value={Fmt.money(d.totalOutstanding)} tone="pink" onClick={() => nav.go({ name: 'report', key: 'OUTSTANDING' })} />
        <Stat title={isToday ? 'آج کے اخراجات' : 'اس دن کے اخراجات'} value={Fmt.money(d.todayExpenses)} tone="red" onClick={() => nav.go({ name: 'expenses' })} />
      </div>
      <Section title="فوری کام">
        <div className="quick">
          <Btn onClick={() => nav.go({ name: 'customerEdit' })}>نیا کسٹمر</Btn>
          <Btn onClick={() => nav.go({ name: 'measurements', pick: true })}>نیا ناپ</Btn>
          <Btn kind="ok" onClick={() => nav.go({ name: 'booking' })}>نئی بکنگ</Btn>
          <Btn onClick={() => nav.go({ name: 'payments' })}>رقم وصول کریں</Btn>
          <Btn onClick={() => nav.go({ name: 'orders' })}>آرڈر تلاش کریں</Btn>
          <Btn onClick={() => nav.go({ name: 'printCenter' })}>رسید پرنٹ</Btn>
        </div>
      </Section>
      {d.alerts.length > 0 && <Section title={`🔔 ڈیلیوری یاد دہانی (${Fmt.num(d.alerts.length)})`}>
        {d.alerts.slice(0, 15).map(r => <button key={r.order.id} className="list-row" onClick={() => nav.go({ name: 'order', id: r.order.id })}>
          <div><b>{Fmt.orderNo(r.order.id)} • {r.customerName}{r.order.urgent ? ' ⚡' : ''}</b>
            <small>ڈیلیوری: {Fmt.date(r.order.deliveryDate)} • {OrderStatus.label(r.order.status)}</small></div>
          <Chip text={r.overdue ? 'تاخیر' : 'قریب'} tone={r.overdue ? 'bad' : 'warn'} />
        </button>)}
      </Section>}
      <h2 className="menu-title">مین مینو</h2>
      <nav className="menu">
        {MENU.map(m => <button key={m.n} style={{ background: m.color }} onClick={() => nav.go({ name: m.screen })}><small>{Fmt.num(m.n)}</small>{m.title}</button>)}
      </nav>
    </Page>
  )
}
