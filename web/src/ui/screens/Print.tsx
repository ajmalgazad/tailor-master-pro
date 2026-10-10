import { useState } from 'react'
import { Codes } from '../../calc'
import { Fmt } from '../../fmt'
import { Q, R } from '../../repo'
import { buildReceipt, printReceipts, profileSlip, receiptBody, receiptCss } from '../../receipt'
import type { ReceiptDoc } from '../../receipt'
import { store } from '../../store'
import { DEFAULT_LOCAL, ReceiptKind } from '../../types'
import type { LocalSettings, ShopSettings } from '../../types'
import { act, msg, Btn, Chip, Empty, Input, Page, Row, Section, Select, Switch, useNav, useStoreVersion } from '../kit'

const LS_KEY = 'tmp-local-settings'
export function getLocal(): LocalSettings { try { return { ...DEFAULT_LOCAL, ...JSON.parse(localStorage.getItem(LS_KEY) ?? '{}') } } catch { return { ...DEFAULT_LOCAL } } }
export function setLocal(s: LocalSettings) { try { localStorage.setItem(LS_KEY, JSON.stringify(s)) } catch { /* ignore */ } }

const copyKinds = [ReceiptKind.BOOKING, ReceiptKind.PAYMENT, ReceiptKind.DELIVERY]

export function ReceiptView({ doc, ls }: { doc: ReceiptDoc; ls: LocalSettings }) {
  return <div className="receipt-frame"><style>{receiptCss(ls)}</style><div className="receipt" dangerouslySetInnerHTML={{ __html: receiptBody(doc) }} /></div>
}

export function PrintCenter() {
  useStoreVersion()
  const nav = useNav()
  const [no, setNo] = useState('')
  const id = no.trim() ? Codes.parse(no) : null
  return <Page title="رسید پرنٹنگ">
    <Section title="پرنٹر">
      <p className="muted">ویب ورژن میں رسید کمپیوٹر کے پرنٹ ڈائیلاگ سے چھپتی ہے: USB تھرمل پرنٹر (58mm / 80mm) یا عام A4 پرنٹر۔ بلوٹوتھ پرنٹر کے لیے فون ایپ استعمال کریں۔</p>
      <Row><Btn kind="ghost" onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.TEST, refId: 0 })}>ٹیسٹ پرنٹ</Btn><Btn kind="ghost" onClick={() => nav.go({ name: 'designer' })}>رسید ڈیزائن</Btn></Row>
    </Section>
    <Section title="آرڈر نمبر سے رسید">
      <Input label="آرڈر نمبر (مثلاً 12 یا B-00012)" value={no} onChange={setNo} />
      <Row><Btn small kind="ghost" disabled={id == null} onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.BOOKING, refId: id })}>بکنگ رسید</Btn>
        <Btn small kind="ghost" disabled={id == null} onClick={() => nav.go({ name: 'preview', kind: ReceiptKind.MEASUREMENT, refId: id })}>ناپ کی پرچی</Btn>
        <Btn small kind="ghost" disabled={id == null} onClick={() => nav.go({ name: 'order', id })}>آرڈر کھولیں</Btn></Row>
    </Section>
    <h2 className="menu-title">پچھلی رسیدیں (دوبارہ پرنٹ کے لیے دبائیں)</h2>
    <div className="list">{Q.printLogs().map(l => <button key={l.id} className="list-row" onClick={() => nav.go(l.kind === ReceiptKind.MEASUREMENT && l.refId < 0
      ? { name: 'preview', kind: l.kind, refId: -l.refId, profileSlip: true } : { name: 'preview', kind: l.kind, refId: l.refId })}>
      <div><b>{l.title || ReceiptKind.label(l.kind)}</b><small>{Fmt.dateTime(l.at)}{l.message ? ' • ' + l.message : ''}</small></div>
      <div className="end"><Chip text={l.status === 'OK' ? 'پرنٹ ہوئی' : l.status === 'SENT' ? 'بھیجی گئی' : 'ناکام'} tone={l.status === 'OK' ? 'ok' : l.status === 'SENT' ? 'warn' : 'bad'} /></div></button>)}
      {Q.printLogs().length === 0 && <Empty text="ابھی کوئی رسید پرنٹ نہیں ہوئی" />}</div>
  </Page>
}

export function Preview({ kind, refId, profileSlip: isSlip }: { kind: string; refId: number; profileSlip?: boolean }) {
  useStoreVersion()
  const [ls] = useState(getLocal())
  const [shopCopy, setShopCopy] = useState(ls.printShopCopy)
  let doc: ReceiptDoc | null = null, error = ''
  try { doc = isSlip ? profileSlip(refId) : buildReceipt(kind, refId) } catch (e) { error = (e as Error).message }
  return <Page title={doc?.title ?? 'رسید'}>
    {error && <p className="bad">{error}</p>}
    {doc && <>
      <div className="preview-actions">
        {copyKinds.includes(kind) && !isSlip && <Switch label="دکان کی کاپی بھی پرنٹ کریں" checked={shopCopy} onChange={setShopCopy} />}
        <Btn kind="ok" onClick={() => act(async () => {
          const docs = [doc!]
          if (shopCopy && copyKinds.includes(kind) && !isSlip) docs.push(buildReceipt(kind, refId, 'دکان کاپی'))
          await printReceipts(docs, ls)
          await R.logPrint(kind, isSlip ? -refId : refId, doc!.title, 'SENT', 'براؤزر سے پرنٹ')
          msg.ok('پرنٹ ڈائیلاگ کھل گیا۔ پرنٹر منتخب کر کے Print دبائیں')
        })}>پرنٹ کریں</Btn>
        <p className="muted small">کاغذ: {Fmt.num(ls.paperWidthMm)}mm (رسید ڈیزائن میں بدلیں)۔ رسید پرنٹ کرنے سے کوئی آرڈر یا وصولی دوبارہ درج نہیں ہوتی۔</p>
      </div>
      <ReceiptView doc={doc} ls={ls} />
    </>}
  </Page>
}

export function Designer() {
  useStoreVersion()
  const [ls, setLs] = useState(getLocal())
  const [shop, setShop] = useState<ShopSettings>({ ...store.shop })
  const [dirty, setDirty] = useState(false)
  const latest = Q.orderRows().find(r => !r.order.isDraft)?.order.id
  const [sample, setSample] = useState<'TEST' | 'BOOKING'>('TEST')
  const upShop = (p: Partial<ShopSettings>) => { setShop({ ...shop, ...p }); setDirty(true) }
  const upLs = (p: Partial<LocalSettings>) => { const n = { ...ls, ...p }; setLs(n); setLocal(n) }
  const realShop = store.shop
  store.shop = shop // preview with unsaved changes
  let doc: ReceiptDoc | null = null
  try { doc = sample === 'BOOKING' && latest ? buildReceipt(ReceiptKind.BOOKING, latest) : buildReceipt(ReceiptKind.TEST, 0) } catch { doc = null }
  store.shop = realShop
  const onLogo = (f: File | undefined) => {
    if (!f) return
    const img = new Image()
    img.onload = () => {
      const scale = Math.min(1, 384 / img.width, 200 / img.height)
      const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(img.width * scale)); c.height = Math.max(1, Math.round(img.height * scale))
      c.getContext('2d')!.drawImage(img, 0, 0, c.width, c.height)
      act(() => store.backend!.saveLogo(c.toDataURL('image/png')), 'لوگو محفوظ ہو گیا')
    }
    img.onerror = () => msg.err('تصویر نہیں کھل سکی')
    img.src = URL.createObjectURL(f)
  }
  return <Page title="رسید ڈیزائنر">
    <div className="two-col"><div className="col">
      <Section title="دکان کی معلومات (رسید کے اوپر)">
        <Input label="دکان کا نام" value={shop.shopName} onChange={v => upShop({ shopName: v })} />
        <Input label="رسید کی سرخی" value={shop.receiptHeading} onChange={v => upShop({ receiptHeading: v })} />
        <Input label="موبائل نمبر" value={shop.shopPhone} onChange={v => upShop({ shopPhone: v })} />
        <Input label="پتہ" value={shop.shopAddress} onChange={v => upShop({ shopAddress: v })} multiline />
        <label className="field"><span>لوگو</span><input type="file" accept="image/*" onChange={e => onLogo(e.target.files?.[0])} /></label>
        {store.logo && <Btn small kind="ghost" onClick={() => act(() => store.backend!.saveLogo(''), 'لوگو ہٹا دیا گیا')}>لوگو ہٹائیں</Btn>}
        <Switch label="لوگو دکھائیں" checked={shop.showLogo} onChange={v => upShop({ showLogo: v })} />
      </Section>
      <Section title="نیچے کا حصہ">
        <Input label="فوٹر پیغام" value={shop.footer} onChange={v => upShop({ footer: v })} />
        <Input label="شرائط و ضوابط" value={shop.terms} onChange={v => upShop({ terms: v })} multiline />
        <Input label="ڈیلیوری کی شرائط" value={shop.deliveryTerms} onChange={v => upShop({ deliveryTerms: v })} multiline />
        <Switch label="شرائط دکھائیں" checked={shop.showTerms} onChange={v => upShop({ showTerms: v })} />
        <Switch label="دستخط کی لائنیں" checked={shop.showSignature} onChange={v => upShop({ showSignature: v })} />
      </Section>
      <Section title="اختیاری خانے">
        <Switch label="ولدیت دکھائیں" checked={shop.showFatherName} onChange={v => upShop({ showFatherName: v })} />
        <Switch label="پتہ دکھائیں" checked={shop.showAddress} onChange={v => upShop({ showAddress: v })} />
        <Switch label="بکنگ رسید پر ناپ بھی دکھائیں" checked={shop.showMeasurementsOnBooking} onChange={v => upShop({ showMeasurementsOnBooking: v })} />
        <Select label="کاپی کی قسم" options={['کسٹمر کاپی', 'دکان کاپی', 'اصل', '']} value={shop.copyType} labelOf={x => x || 'کچھ نہ لکھیں'} onChange={v => upShop({ copyType: v })} />
      </Section>
      <Btn wide disabled={!dirty} onClick={() => act(async () => { await store.backend!.saveShop(shop); setDirty(false) }, 'رسید کی سیٹنگز محفوظ (فون پر بھی)')}>محفوظ کریں</Btn>
      <Section title="اس کمپیوٹر کا پرنٹر">
        <Select label="کاغذ" options={[58, 80, 210]} value={ls.paperWidthMm} labelOf={x => (x === 210 ? 'A4' : Fmt.num(x) + ' ملی میٹر')} onChange={v => upLs({ paperWidthMm: v })} />
        <Select label="فونٹ" options={['naskh', 'nastaliq']} value={ls.receiptFont} labelOf={x => (x === 'naskh' ? 'نسخ (صاف)' : 'نستعلیق')} onChange={v => upLs({ receiptFont: v })} />
        <Select label="حروف کا سائز" options={[10, 11, 12, 13, 14, 16, 18]} value={ls.receiptFontSize} labelOf={x => Fmt.num(x)} onChange={v => upLs({ receiptFontSize: v })} />
        <Switch label="ہمیشہ دکان کی کاپی بھی پرنٹ کریں" checked={ls.printShopCopy} onChange={v => upLs({ printShopCopy: v })} />
      </Section>
    </div><div className="col">
      <Section title="نمونہ">
        <Select label="نمونہ رسید" options={['TEST', 'BOOKING'] as const} value={sample} labelOf={x => (x === 'TEST' ? 'ٹیسٹ رسید' : 'آخری بکنگ رسید')} onChange={v => setSample(v)} />
        {doc && <ReceiptView doc={doc} ls={ls} />}
      </Section>
    </div></div>
  </Page>
}
