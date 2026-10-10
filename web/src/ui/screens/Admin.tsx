import { isEn, translateHtml } from '../../i18n'
import { useState, useSyncExternalStore } from 'react'
import { Dates } from '../../calc'
import { Fmt } from '../../fmt'
import { Q } from '../../repo'
import { REPORTS, buildReport, defaultFilter, toCsv } from '../../reports'
import type { ReportFilter, ReportKey, ReportTable } from '../../reports'
import { store } from '../../store'
import { OrderStatus } from '../../types'
import type { Customer, ShopSettings } from '../../types'
import { checkBackup, exportBackup, restoreBackup } from '../../backup'
import type { BackupCheck } from '../../backup'
import { addSample, removeSample } from '../../sample'
import { cloudSignOut, cloudState, onCloudState } from '../../cloud'
import { act, download, Btn, Confirm, CustomerPicker, DateInput, Empty, Input, KV, LangSwitch, Page, Row, Section, Select, useNav, useStoreVersion } from '../kit'

export function Reports() {
  const nav = useNav()
  return <Page title="رپورٹس">
    <div className="list">{REPORTS.map(r => <button key={r.key} className="list-row" onClick={() => nav.go({ name: 'report', key: r.key })}><div><b>{r.title}</b></div></button>)}</div>
    <p className="muted">ہر رپورٹ PDF (پرنٹ ← Save as PDF) اور CSV (ایکسل) میں محفوظ کی جا سکتی ہے۔</p>
  </Page>
}

function printTable(t: ReportTable) {
  const esc = (s: string) => s.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' } as Record<string, string>)[c])
  const f = document.createElement('iframe')
  Object.assign(f.style, { position: 'fixed', width: '0', height: '0', border: '0', left: '-1000px' })
  document.body.appendChild(f)
  const landscape = t.headers.length > 6
  const html = `<!doctype html><html lang="ur" dir="rtl"><head><meta charset="utf-8"><title>${esc(t.title)}</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Naskh+Arabic:wght@400;700&display=swap">
<style>@page{size:A4 ${landscape ? 'landscape' : 'portrait'};margin:12mm}body{font-family:'Noto Naskh Arabic',serif;font-size:10pt;direction:${isEn() ? 'ltr' : 'rtl'}}
h1{font-size:15pt;margin:0}p{color:#444;margin:2px 0 8px;white-space:pre-wrap}table{width:100%;border-collapse:collapse}th,td{border:1px solid #999;padding:3px 5px;text-align:${isEn() ? 'left' : 'right'};vertical-align:top}
th{background:#e6eef0}tfoot td{font-weight:700;background:#f4f4f4}</style></head><body>
<h1>${esc(store.shop.shopName)} — ${esc(t.title)}</h1><p>${esc(t.subtitle)}\nتیار کردہ: ${esc(Fmt.dateTime(Date.now()))}</p>
<table><thead><tr>${t.headers.map(h => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${t.rows.map(r => `<tr>${r.map(c => `<td>${esc(c)}</td>`).join('')}</tr>`).join('') || '<tr><td>کوئی ریکارڈ نہیں</td></tr>'}</tbody>
${t.totals ? `<tfoot><tr>${t.totals.map(c => `<td>${esc(c)}</td>`).join('')}</tr></tfoot>` : ''}</table></body></html>`
  const w = f.contentWindow!
  w.document.open(); w.document.write(translateHtml(html)); w.document.close()
  setTimeout(() => { w.focus(); w.print(); setTimeout(() => f.remove(), 1000) }, 700)
}

export function Report({ reportKey }: { reportKey: ReportKey }) {
  useStoreVersion()
  const meta = REPORTS.find(r => r.key === reportKey)!
  const [f, setF] = useState<ReportFilter>(defaultFilter(reportKey))
  const [cust, setCust] = useState<Customer | null>(null)
  const [picking, setPicking] = useState(false)
  const t = buildReport(reportKey, f)
  const base = 'report-' + reportKey.toLowerCase() + '-' + Dates.format(Date.now(), 'yyyyMMdd')
  const now = Date.now()
  return <Page title={meta.title}>
    <Section title="فلٹر">
      {meta.dates && <>
        <Row><Btn small kind="ghost" onClick={() => setF({ ...f, from: Dates.today(), to: Dates.endOfDay(now) })}>آج</Btn>
          <Btn small kind="ghost" onClick={() => setF({ ...f, from: Dates.startOfMonth(now), to: Dates.endOfDay(now) })}>یہ مہینہ</Btn>
          <Btn small kind="ghost" onClick={() => { const pm = Dates.startOfMonth(Dates.addDays(Dates.startOfMonth(now), -1)); setF({ ...f, from: pm, to: Dates.endOfMonth(pm) }) }}>پچھلا مہینہ</Btn></Row>
        <div className="grid2"><DateInput label="سے" value={f.from || Dates.today()} onChange={v => setF({ ...f, from: Dates.startOfDay(v) })} />
          <DateInput label="تک" value={f.to} onChange={v => setF({ ...f, to: Dates.endOfDay(v) })} /></div>
        {f.from === 0 && <p className="muted small">شروع سے</p>}
      </>}
      <div className="grid3">
        {meta.customer && <div><Btn kind="ghost" onClick={() => setPicking(true)}>کسٹمر: {cust?.name ?? 'تمام'}</Btn>{cust && <Btn small kind="ghost" onClick={() => { setCust(null); setF({ ...f, customerId: null }) }}>ہٹائیں</Btn>}</div>}
        {meta.garment && <Select label="کپڑا" options={[null, ...Q.garments()]} value={Q.garments().find(g => g.id === f.garmentId) ?? null} keyOf={g => String(g?.id ?? '')} labelOf={g => g?.name ?? 'تمام کپڑے'} onChange={g => setF({ ...f, garmentId: g?.id ?? null })} />}
        {meta.status && <Select label="اسٹیٹس" options={[null, ...OrderStatus.all]} value={f.status} keyOf={x => x ?? ''} labelOf={x => (x ? OrderStatus.label(x) : 'تمام')} onChange={v => setF({ ...f, status: v })} />}
      </div>
    </Section>
    <Row><Btn onClick={() => printTable(t)}>PDF / پرنٹ</Btn><Btn kind="ghost" onClick={() => download(base + '.csv', toCsv(t), 'text/csv;charset=utf-8')}>CSV (ایکسل)</Btn></Row>
    {t.subtitle && <p className="muted small" style={{ whiteSpace: 'pre-wrap' }}>{t.subtitle}</p>}
    <p className="muted small">ریکارڈ: {Fmt.num(t.rows.length)}</p>
    <div className="table-wrap"><table className="table">
      <thead><tr>{t.headers.map((h, i) => <th key={i}>{h}</th>)}</tr></thead>
      <tbody>{t.rows.slice(0, 1000).map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody>
      {t.totals && <tfoot><tr>{t.totals.map((c, i) => <td key={i}>{c}</td>)}</tr></tfoot>}
    </table></div>
    {t.rows.length === 0 && <Empty text="کوئی ریکارڈ نہیں" />}
    {picking && <CustomerPicker onClose={() => setPicking(false)} onPick={c => { setCust(c); setF({ ...f, customerId: c.id }); setPicking(false) }} />}
  </Page>
}

export function Settings() {
  useStoreVersion()
  const nav = useNav()
  const cs = useSyncExternalStore(onCloudState, () => cloudState.phase + cloudState.email + cloudState.error + cloudState.pending + cloudState.online)
  void cs
  const [askSample, setAskSample] = useState(false)
  const [askOut, setAskOut] = useState(false)
  const cloud = store.backend?.kind === 'cloud'
  const entries: [string, string, string][] = [
    ['دکان اور عمومی سیٹنگز', 'نام، پتہ، کرنسی، تاریخ، ہندسے', 'shopSettings'], ['رسید ڈیزائنر', 'لوگو، سرخی، فوٹر، کاغذ، فونٹ', 'designer'],
    ['بیک اپ اور ریسٹور', 'ڈیٹا فائل میں محفوظ کریں یا واپس لائیں', 'backup'], ['کپڑوں کی اقسام اور ناپ کے خانے', '', 'garments'],
    ['ریٹ ماسٹر', 'معیاری اور خصوصی ریٹ، سروسز', 'rates'], ['ڈپلیکیٹ کسٹمر ضم کریں', '', 'merge'], ['آڈٹ لاگ', 'تمام اہم تبدیلیوں کا ریکارڈ', 'audit'],
  ]
  return <Page title="بیک اپ اور سیٹنگز">
    <Section title="Language / زبان"><LangSwitch /></Section>
    <Section title={cloud ? 'کلاؤڈ اکاؤنٹ' : 'ڈیمو موڈ'}>
      {cloud ? <>
        <KV k="اکاؤنٹ" v={cloudState.email} bold />
        <KV k="انٹرنیٹ" v={cloudState.online ? 'موجود' : 'نہیں — تبدیلیاں محفوظ ہیں، انٹرنیٹ آنے پر سنک ہوں گی'} tone={cloudState.online ? 'ok' : 'warn'} />
        <KV k="بھیجنے کے لیے باقی" v={Fmt.num(cloudState.pending)} />
        <KV k="اس ڈیوائس کا نمبر" v={Fmt.num(store.device)} />
        {cloudState.error && <p className="bad">{cloudState.error}</p>}
        <Btn kind="ghost" onClick={() => setAskOut(true)}>لاگ آؤٹ</Btn>
      </> : <p className="warn">یہ ڈیمو ہے۔ ڈیٹا صرف اسی براؤزر میں ہے اور فون سے نہیں جڑا۔ اصل کام کے لیے دکان کے اکاؤنٹ سے لاگ اِن کریں۔</p>}
    </Section>
    <div className="list">{entries.map(([t, s, n]) => <button key={n} className="list-row" onClick={() => nav.go({ name: n })}><div><b>{t}</b>{s && <small>{s}</small>}</div></button>)}</div>
    <Section title="نمونہ (ٹیسٹ) ڈیٹا">
      <p className="muted small">آزمانے کے لیے نمونہ کسٹمر، آرڈر اور اخراجات۔ اصل کاروبار شروع کرنے سے پہلے انہیں ختم کر دیں۔</p>
      <Row><Btn kind="ghost" disabled={store.list('customers').some(c => c.sample)} onClick={() => act(addSample, 'نمونہ ڈیٹا شامل ہو گیا')}>نمونہ ڈیٹا شامل کریں</Btn>
        <Btn kind="ghost" onClick={() => setAskSample(true)}>نمونہ ڈیٹا ختم کریں</Btn></Row>
    </Section>
    {askSample && <Confirm title="نمونہ ڈیٹا ختم کریں؟" text="صرف '(نمونہ)' والے کسٹمر، ان کے آرڈر، ادائیگیاں، ناپ اور نمونہ اخراجات حذف ہوں گے۔" okText="ختم کریں" danger
      onClose={() => setAskSample(false)} onOk={() => act(removeSample, 'نمونہ ڈیٹا ختم ہو گیا')} />}
    {askOut && <Confirm title="لاگ آؤٹ کریں؟" text="اس براؤزر سے دکان کا ڈیٹا ہٹ جائے گا۔ کلاؤڈ اور فون پر ڈیٹا محفوظ رہے گا۔" okText="لاگ آؤٹ" onClose={() => setAskOut(false)} onOk={() => act(cloudSignOut)} />}
  </Page>
}

export function ShopSettingsScreen() {
  useStoreVersion()
  const [s, setS] = useState<ShopSettings>({ ...store.shop })
  const up = (p: Partial<ShopSettings>) => setS({ ...s, ...p })
  return <Page title="دکان اور عمومی سیٹنگز">
    <Section title="دکان">
      <Input label="دکان کا نام" value={s.shopName} onChange={v => up({ shopName: v })} />
      <Input label="رابطہ نمبر" value={s.shopPhone} onChange={v => up({ shopPhone: v })} />
      <Input label="پتہ" value={s.shopAddress} onChange={v => up({ shopAddress: v })} multiline />
      <Input label="ڈیلیوری کی شرائط" value={s.deliveryTerms} onChange={v => up({ deliveryTerms: v })} multiline />
    </Section>
    <Section title="عمومی">
      <Select label="کرنسی کا نشان" options={['روپے', 'Rs', 'PKR', '₨']} value={s.currencyLabel} labelOf={x => x} onChange={v => up({ currencyLabel: v })} />
      <Select label="تاریخ کا انداز" options={['dd/MM/yyyy', 'dd-MM-yyyy', 'yyyy-MM-dd', 'dd MMM yyyy']} value={s.dateFormat} labelOf={x => x + '  →  ' + Dates.format(Date.now(), x)} onChange={v => up({ dateFormat: v })} />
      <Select label="ہندسے" options={[false, true]} value={s.urduDigits} labelOf={x => (x ? 'اردو ہندسے (۱۲۳)' : 'انگریزی ہندسے (123)')} onChange={v => up({ urduDigits: v })} />
      <Select label="ناپ کی ڈیفالٹ اکائی" options={['انچ', 'سینٹی میٹر']} value={s.defaultUnit} labelOf={x => x} onChange={v => up({ defaultUnit: v })} />
      <Select label="ڈیلیوری یاد دہانی (دن پہلے)" options={[0, 1, 2, 3]} value={s.dueSoonDays} labelOf={x => Fmt.num(x) + ' دن'} onChange={v => up({ dueSoonDays: v })} />
    </Section>
    <Btn wide onClick={() => act(() => store.backend!.saveShop(s), 'سیٹنگز محفوظ ہو گئیں (فون پر بھی)')}>محفوظ کریں</Btn>
  </Page>
}

export function Backup() {
  useStoreVersion()
  const [check, setCheck] = useState<BackupCheck | null>(null)
  const [ask, setAsk] = useState(false)
  return <Page title="بیک اپ اور ریسٹور">
    <Section title="بیک اپ بنائیں">
      <p className="muted small">پورا ڈیٹا ایک فائل میں ڈاؤن لوڈ ہوگا۔ یہی فائل فون ایپ میں بھی ریسٹور ہو سکتی ہے۔</p>
      <Btn kind="ok" onClick={() => download('tailor-master-backup-' + Dates.format(Date.now(), 'yyyyMMdd-HHmm') + '.json', exportBackup(), 'application/json')}>بیک اپ فائل ڈاؤن لوڈ کریں</Btn>
    </Section>
    <Section title="بیک اپ سے واپس لائیں (ریسٹور)">
      <p className="bad small">ریسٹور کرنے سے موجودہ تمام ڈیٹا بیک اپ والے ڈیٹا سے بدل جائے گا{store.backend?.kind === 'cloud' ? '، کلاؤڈ اور فون پر بھی' : ''}۔ پہلے موجودہ ڈیٹا کا بیک اپ بنا لیں۔</p>
      <label className="field"><span>بیک اپ فائل منتخب کریں</span><input type="file" accept=".json,application/json" onChange={e => {
        const file = e.target.files?.[0]; if (!file) return
        file.text().then(t => setCheck(checkBackup(t)))
      }} /></label>
      {check && (check.ok ? <>
        <p className="ok">فائل درست ہے ✓ • بیک اپ کی تاریخ: {Fmt.dateTime(check.exportedAt)}</p>
        {check.summary.map(([k, v]) => <KV key={k} k={k} v={Fmt.num(v)} />)}
        <Btn kind="danger" onClick={() => setAsk(true)}>ریسٹور کریں</Btn>
      </> : <>{check.errors.map((e, i) => <p key={i} className="bad">• {e}</p>)}</>)}
    </Section>
    {ask && check?.data && <Confirm title="کیا آپ کو یقین ہے؟" text="موجودہ تمام ڈیٹا ختم ہو کر بیک اپ والا ڈیٹا آ جائے گا۔ یہ عمل واپس نہیں ہو سکتا۔" okText="ہاں، ریسٹور کریں" danger
      onClose={() => setAsk(false)} onOk={() => act(async () => { await restoreBackup(check.data!); setCheck(null) }, 'ڈیٹا کامیابی سے ریسٹور ہو گیا')} />}
  </Page>
}

export function Audit() {
  useStoreVersion()
  const list = Q.audits()
  return <Page title="آڈٹ لاگ"><div className="list">{list.map(a => <div key={a.id} className="list-row static"><div>
    <b>{Fmt.dateTime(a.at)} • {a.entity} #{a.entityId} • {a.action}</b>{a.detail && <small>{a.detail}</small>}</div></div>)}
    {list.length === 0 && <Empty text="کوئی ریکارڈ نہیں" />}</div></Page>
}


