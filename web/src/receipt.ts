// Port of print/ReceiptBuilder.kt — same receipt content; rendered as HTML for the browser's print dialog.
import { Fmt } from './fmt'
import { Q, decodeCharges, decodeIds, decodeSnapshot, decodeValues, UserError } from './repo'
import { store } from './store'
import { OrderStatus, ReceiptKind, TxnType } from './types'
import type { LocalSettings } from './types'

type Align = 'start' | 'center' | 'end'
export type RLine =
  | { t: 'text'; text: string; scale?: number; bold?: boolean; align?: Align }
  | { t: 'pair'; label: string; value: string; bold?: boolean; scale?: number }
  | { t: 'div'; dashed?: boolean }
  | { t: 'space'; px?: number }
  | { t: 'logo' }
  | { t: 'band'; text: string }
export interface ReceiptDoc { title: string; lines: RLine[] }

const T = (text: string, scale = 1, bold = false, align: Align = 'start'): RLine => ({ t: 'text', text, scale, bold, align })
const P = (label: string, value: string, bold = false, scale = 1): RLine => ({ t: 'pair', label, value, bold, scale })
const D = (dashed = true): RLine => ({ t: 'div', dashed })
const SP = (px = 8): RLine => ({ t: 'space', px })
const m = (p: number) => Fmt.money(p)

function header(title: string, copy: string | null): RLine[] {
  const s = store.shop
  const l: RLine[] = [{ t: 'logo' }, T(s.shopName, 1.45, true, 'center')]
  if (s.receiptHeading.trim()) l.push(T(s.receiptHeading, 0.9, false, 'center'))
  if (s.shopAddress.trim()) l.push(T(s.shopAddress, 0.85, false, 'center'))
  if (s.shopPhone.trim()) l.push(T('فون: ' + Fmt.d(s.shopPhone), 0.9, false, 'center'))
  l.push(SP(4), { t: 'band', text: title })
  if (copy && copy.trim()) l.push(T(copy, 0.85, false, 'center'))
  l.push(SP(4))
  return l
}

function footer(includeTerms: boolean, signature: boolean): RLine[] {
  const s = store.shop
  const l: RLine[] = []
  if (includeTerms && s.showTerms && s.terms.trim()) l.push(D(), T('شرائط و ضوابط:', 0.85, true), T(s.terms, 0.8))
  if (includeTerms && s.deliveryTerms.trim()) l.push(T(s.deliveryTerms, 0.8))
  if (signature && s.showSignature) l.push(SP(24), T('دستخط دکاندار: ____________________', 0.85), SP(20), T('دستخط کسٹمر: _____________________', 0.85))
  if (s.footer.trim()) l.push(D(), T(s.footer, 0.9, false, 'center'))
  l.push(T('پرنٹ: ' + Fmt.dateTime(Date.now()), 0.7, false, 'center'))
  return l
}

export function buildReceipt(kind: string, refId: number, copyLabel?: string): ReceiptDoc {
  const copy = copyLabel ?? store.shop.copyType
  switch (kind) {
    case ReceiptKind.BOOKING: return booking(refId, copy)
    case ReceiptKind.MEASUREMENT: return slip(refId)
    case ReceiptKind.PAYMENT: return payment(refId, copy)
    case ReceiptKind.DELIVERY: return delivery(refId, copy)
    case ReceiptKind.LEDGER: return ledger(refId)
    case ReceiptKind.CUSTOMER: return customerInfo(refId)
    case ReceiptKind.TEST: return test()
    default: throw new UserError('نامعلوم رسید')
  }
}

function booking(orderId: number, copy: string): ReceiptDoc {
  const s = store.shop
  const f = Q.orderFull(orderId), o = f.order
  const l: RLine[] = header(o.isDraft ? 'بکنگ رسید (ڈرافٹ)' : 'بکنگ رسید', copy)
  l.push(P('آرڈر نمبر:', Fmt.orderNo(o.id), true), P('بکنگ تاریخ:', Fmt.date(o.bookingDate)), P('ڈیلیوری تاریخ:', Fmt.date(o.deliveryDate), true))
  if (o.urgent) l.push(T('★ ارجنٹ آرڈر ★', 1, true, 'center'))
  l.push(P('کسٹمر:', f.customer.name + ' (' + Fmt.customerCode(f.customer.id) + ')'))
  if (s.showFatherName && f.customer.fatherName.trim()) l.push(P('ولدیت:', f.customer.fatherName))
  if (f.customer.mobile.trim()) l.push(P('موبائل:', Fmt.d(f.customer.mobile)))
  if (s.showAddress && f.customer.address.trim()) l.push(T('پتہ: ' + f.customer.address, 0.85))
  if (f.member && !f.member.isSelf) l.push(P('فیملی ممبر:', f.member.name + ' (' + f.member.relation + ')'))
  if (o.status === OrderStatus.CANCELLED) l.push({ t: 'band', text: 'یہ آرڈر منسوخ ہو چکا ہے' })
  l.push(D(), P('کپڑا', 'رقم', true, 0.9))
  const multi = new Set(f.items.map(i => i.memberId)).size > 1
  for (const it of f.items) {
    const who = it.memberId !== o.memberId || multi ? ' — ' + it.memberName : ''
    l.push(T(it.garmentName + who, 1, true), P(Fmt.num(it.qty) + ' × ' + Fmt.moneyPlain(it.rate), Fmt.moneyPlain(it.total)))
    if (s.showMeasurementsOnBooking) {
      const snap = decodeSnapshot(it.measurementJson)
      const v = snap?.values.filter(x => x.value.trim()).map(x => x.label + ': ' + Fmt.d(x.value)).join('  ')
      if (v) l.push(T(v, 0.8))
    }
  }
  l.push(D(), P('کپڑوں کی رقم:', m(o.itemsTotal)))
  decodeCharges(o.chargesJson).forEach(c => l.push(P(c.name + ':', m(c.amount))))
  if (o.discountAmount > 0) {
    const pct = o.discountType === 'PERCENT' ? ' (' + Fmt.d(String(Number(o.discountInput))) + '%)' : ''
    l.push(P('رعایت' + pct + ':', m(o.discountAmount)))
  }
  l.push(P('کل رقم:', m(o.netAmount), true, 1.15), P('ایڈوانس / وصول شدہ:', m(f.paid)), P('بقایا:', m(f.outstanding), true, 1.15))
  if (o.instructions.trim()) l.push(D(), T('خصوصی ہدایات: ' + o.instructions, 0.9))
  l.push(...footer(true, true))
  return { title: 'بکنگ رسید ' + Fmt.orderNo(o.id), lines: l }
}

function slip(orderId: number): ReceiptDoc {
  const f = Q.orderFull(orderId)
  const l: RLine[] = [T(store.shop.shopName, 1.2, true, 'center'), { t: 'band', text: 'ناپ کی پرچی' },
    P('آرڈر نمبر:', Fmt.orderNo(f.order.id), true), P('کسٹمر:', f.customer.name), P('ڈیلیوری:', Fmt.date(f.order.deliveryDate), true)]
  if (f.order.urgent) l.push(T('★ ارجنٹ ★', 1, true, 'center'))
  for (const it of f.items) {
    l.push(D(false), T(it.garmentName + ' × ' + Fmt.num(it.qty), 1.1, true), P('فرد:', it.memberName, true))
    const snap = decodeSnapshot(it.measurementJson)
    if (!snap || !snap.values.length) l.push(T('ناپ منسلک نہیں', 0.9))
    else {
      l.push(T(snap.profileName + ' • ' + snap.unit + ' • ' + Fmt.date(snap.date), 0.8))
      snap.values.filter(v => v.value.trim()).forEach(v => l.push(P(v.label, Fmt.d(v.value), false, 1.05)))
      if (snap.fitting.trim()) l.push(T('فٹنگ: ' + snap.fitting, 0.9))
      if (snap.instructions.trim()) l.push(T('ہدایات: ' + snap.instructions, 0.9))
    }
    if (it.notes.trim()) l.push(T('نوٹ: ' + it.notes, 0.9))
  }
  if (f.order.instructions.trim()) l.push(D(), T('آرڈر ہدایات: ' + f.order.instructions, 0.9))
  l.push(T('پرنٹ: ' + Fmt.dateTime(Date.now()), 0.7, false, 'center'))
  return { title: 'ناپ کی پرچی ' + Fmt.orderNo(f.order.id), lines: l }
}

export function profileSlip(profileId: number): ReceiptDoc {
  const p = Q.profile(profileId); if (!p) throw new UserError('پروفائل نہیں ملا')
  const c = Q.customer(p.customerId), mem = Q.member(p.memberId), g = Q.garment(p.garmentId)
  const l: RLine[] = [T(store.shop.shopName, 1.2, true, 'center'), { t: 'band', text: 'ناپ کی پرچی' },
    P('کسٹمر:', (c?.name ?? '-') + ' (' + Fmt.customerCode(p.customerId) + ')'), P('فرد:', (mem?.name ?? '-') + ' — ' + (mem?.relation ?? ''), true),
    P('کپڑا:', g?.name ?? '-', true), P('پروفائل:', p.name), P('تاریخ / اکائی:', Fmt.date(p.date) + ' • ' + p.unit), D()]
  decodeValues(p.valuesJson).filter(v => v.value.trim()).forEach(v => l.push(P(v.label, Fmt.d(v.value), false, 1.05)))
  if (p.fitting.trim()) l.push(T('فٹنگ: ' + p.fitting, 0.9))
  if (p.instructions.trim()) l.push(T('ہدایات: ' + p.instructions, 0.9))
  if (p.notes.trim()) l.push(T('نوٹ: ' + p.notes, 0.9))
  l.push(T('پرنٹ: ' + Fmt.dateTime(Date.now()), 0.7, false, 'center'))
  return { title: 'ناپ ' + p.name, lines: l }
}

function payment(txnId: number, copy: string): ReceiptDoc {
  const t = Q.txn(txnId); if (!t) throw new UserError('اندراج نہیں ملا')
  const c = Q.customer(t.customerId); if (!c) throw new UserError('کسٹمر نہیں ملا')
  const after = Q.balanceAfterTxn(txnId)
  const title = t.type === TxnType.PAYMENT ? 'وصولی رسید' : t.type === TxnType.REFUND ? 'واپسی (ریفنڈ) رسید' : t.type === TxnType.REVERSAL ? 'منسوخی (ریورسل) رسید' : 'ایڈجسٹمنٹ رسید'
  const l: RLine[] = header(title, copy)
  l.push(P('ٹرانزیکشن نمبر:', Fmt.txnNo(t.id), true), P('تاریخ:', Fmt.dateTime(t.date)), P('کسٹمر:', c.name + ' (' + Fmt.customerCode(c.id) + ')'))
  if (c.mobile.trim()) l.push(P('موبائل:', Fmt.d(c.mobile)))
  if (t.orderId != null) l.push(P('آرڈر نمبر:', Fmt.orderNo(t.orderId)))
  if (t.cash) l.push(P('طریقہ ادائیگی:', t.method))
  if (t.reference.trim()) l.push(P('حوالہ:', Fmt.d(t.reference)))
  l.push(D(), P(t.type === TxnType.PAYMENT ? 'وصول شدہ رقم:' : t.type === TxnType.REFUND ? 'واپس کی گئی رقم:' : 'رقم:', m(t.amount), true, 1.3))
  if (t.reversedById != null) l.push({ t: 'band', text: 'یہ اندراج منسوخ (ریورس) ہو چکا ہے' })
  if (t.notes.trim()) l.push(T('تفصیل: ' + t.notes, 0.85))
  l.push(D())
  if (t.orderId != null) {
    const f = Q.orderFull(t.orderId)
    l.push(P('آرڈر کی کل رقم:', m(f.order.status === OrderStatus.CANCELLED ? 0 : f.order.netAmount)), P('آرڈر میں کل وصولی:', m(f.paid)), P('آرڈر کا بقایا:', m(f.outstanding), true))
  }
  l.push(P('اس رسید کے بعد کل بقایا:', m(after), true, 1.1), ...footer(false, true))
  return { title: title + ' ' + Fmt.txnNo(t.id), lines: l }
}

function delivery(deliveryId: number, copy: string): ReceiptDoc {
  const d = Q.delivery(deliveryId); if (!d) throw new UserError('ڈیلیوری نہیں ملی')
  const f = Q.orderFull(d.orderId)
  const ids = new Set(decodeIds(d.itemIdsJson))
  const now = f.items.filter(i => ids.has(i.id)), pending = f.items.filter(i => !i.delivered)
  const l: RLine[] = header('ڈیلیوری رسید', copy)
  l.push(P('ڈیلیوری نمبر:', Fmt.deliveryNo(d.id), true), P('آرڈر نمبر:', Fmt.orderNo(f.order.id), true), P('ڈیلیوری تاریخ:', Fmt.dateTime(d.date)), P('کسٹمر:', f.customer.name))
  if (f.member) l.push(P('فیملی ممبر:', f.member.name))
  l.push(D(), T('اس بار ڈیلیور ہوئے:', 1, true))
  now.forEach(i => l.push(P(i.garmentName + ' — ' + i.memberName, Fmt.num(i.qty))))
  if (pending.length) { l.push(T('ابھی باقی ہیں:', 1, true)); pending.forEach(i => l.push(P(i.garmentName + ' — ' + i.memberName, Fmt.num(i.qty)))) }
  else l.push(T('✓ آرڈر مکمل ڈیلیور ہو گیا', 1, true, 'center'))
  if (d.forcedComplete) l.push(T('(مالک کی تصدیق سے مکمل کیا گیا)', 0.8))
  l.push(D(), P('کل رقم:', m(f.order.netAmount)))
  if (d.paymentTxnId != null) { const t = Q.txn(d.paymentTxnId); if (t) l.push(P('ڈیلیوری پر وصولی:', m(t.amount))) }
  l.push(P('کل وصول شدہ:', m(f.paid)), P('بقایا:', m(f.outstanding), true, 1.15))
  if (d.notes.trim()) l.push(T('نوٹ: ' + d.notes, 0.85))
  l.push(...footer(false, true))
  return { title: 'ڈیلیوری رسید ' + Fmt.deliveryNo(d.id), lines: l }
}

function ledger(customerId: number): ReceiptDoc {
  const lg = Q.ledger(customerId)
  const l: RLine[] = header('کھاتہ اسٹیٹمنٹ', null)
  l.push(P('کسٹمر:', lg.customer.name + ' (' + Fmt.customerCode(lg.customer.id) + ')', true))
  if (lg.customer.mobile.trim()) l.push(P('موبائل:', Fmt.d(lg.customer.mobile)))
  l.push(D())
  const shown = lg.entries.length > 60 ? lg.entries.slice(-60) : lg.entries
  if (shown.length < lg.entries.length) l.push(T('(صرف آخری ۶۰ اندراجات)', 0.8))
  shown.forEach(e => {
    l.push(T(Fmt.date(e.date) + ' — ' + e.description, 0.85))
    const amt = e.debit > 0 ? '+' + Fmt.moneyPlain(e.debit) : e.credit > 0 ? '−' + Fmt.moneyPlain(e.credit) : '0'
    l.push(P(amt, 'بیلنس ' + Fmt.moneyPlain(e.balance), false, 0.85))
  })
  l.push(D(), P('موجودہ بقایا:', m(lg.balance), true, 1.2), ...footer(false, false))
  return { title: 'کھاتہ ' + lg.customer.name, lines: l }
}

function customerInfo(customerId: number): ReceiptDoc {
  const c = Q.customer(customerId); if (!c) throw new UserError('کسٹمر نہیں ملا')
  const l: RLine[] = header('کسٹمر معلومات', null)
  l.push(P('کوڈ:', Fmt.customerCode(c.id), true), P('نام:', c.name, true))
  if (c.fatherName.trim()) l.push(P('ولدیت:', c.fatherName))
  if (c.mobile.trim()) l.push(P('موبائل:', Fmt.d(c.mobile)))
  if (c.whatsapp.trim()) l.push(P('واٹس ایپ:', Fmt.d(c.whatsapp)))
  if (c.address.trim()) l.push(T('پتہ: ' + c.address, 0.9))
  const ca = [c.area, c.city].filter(Boolean).join('، ')
  if (ca) l.push(P('علاقہ:', ca))
  l.push(P('رجسٹریشن:', Fmt.date(c.regDate)), D(), T('فیملی ممبرز:', 1, true))
  Q.members(customerId).filter(x => x.active).forEach(x => l.push(P(x.name, x.relation)))
  l.push(D(), P('موجودہ بقایا:', m(Q.balance(customerId)), true), ...footer(false, false))
  return { title: 'کسٹمر ' + c.name, lines: l }
}

function test(): ReceiptDoc {
  const l: RLine[] = header('ٹیسٹ پرنٹ', null)
  l.push(T('اردو حروف: ا ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن ں و ہ ھ ء ی ے'),
    T('جملہ: شلوار قمیض کی سلائی مکمل ہو گئی ہے۔ شکریہ!'), T('انگریزی ہندسے: 0123456789'), T('اردو ہندسے: ۰۱۲۳۴۵۶۷۸۹'),
    T('علامات: ، ۔ ؟ ! : ( ) - / % +'), T('مخلوط: آرڈر B-00012 کا Total Rs 4,500 ہے، Mobile 0300-1234567'),
    P('بائیں / دائیں:', '1,234.50 روپے', true), T('بڑا متن', 1.6, true, 'center'), T('چھوٹا متن', 0.75, false, 'center'), D(false),
    T('پرنٹ: ' + Fmt.dateTime(Date.now()), 0.7, false, 'center'))
  return { title: 'ٹیسٹ پرنٹ', lines: l }
}

// ---------------------------------------------------------------- HTML rendering
const esc = (s: string) => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' } as Record<string, string>)[c])

export function receiptBody(doc: ReceiptDoc): string {
  return doc.lines.map(l => {
    switch (l.t) {
      case 'text': return `<div class="t ${l.bold ? 'b' : ''} a-${l.align ?? 'start'}" style="font-size:${(l.scale ?? 1)}em">${esc(l.text)}</div>`
      case 'pair': return `<div class="p ${l.bold ? 'b' : ''}" style="font-size:${(l.scale ?? 1)}em"><span>${esc(l.label)}</span><span class="v">${esc(l.value)}</span></div>`
      case 'div': return `<hr class="${l.dashed === false ? 'solid' : ''}">`
      case 'space': return `<div style="height:${(l.px ?? 8) * 0.5}px"></div>`
      case 'logo': return store.shop.showLogo && store.logo ? `<div class="logo"><img src="${store.logo}" alt=""></div>` : ''
      case 'band': return `<div class="band">${esc(l.text)}</div>`
    }
  }).join('')
}

export function receiptCss(ls: LocalSettings): string {
  const font = ls.receiptFont === 'nastaliq' ? "'Noto Nastaliq Urdu', serif" : "'Noto Naskh Arabic', serif"
  const lh = ls.receiptFont === 'nastaliq' ? 2.1 : 1.6
  return `.receipt{direction:rtl;font-family:${font};font-size:${ls.receiptFontSize}pt;line-height:${lh};color:#000;background:#fff;width:${ls.paperWidthMm - 6}mm;padding:0 1mm}
.receipt .t{white-space:pre-wrap}.receipt .b{font-weight:700}.receipt .a-center{text-align:center}.receipt .a-end{text-align:left}
.receipt .p{display:flex;justify-content:space-between;gap:2mm}.receipt .p .v{text-align:left;direction:rtl;white-space:nowrap}
.receipt hr{border:0;border-top:1.5px dashed #000;margin:1mm 0}.receipt hr.solid{border-top-style:solid}
.receipt .band{background:#000;color:#fff;text-align:center;font-weight:700;padding:.5mm 0;margin:1mm 0;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.receipt .logo{text-align:center}.receipt .logo img{max-width:60%;max-height:22mm}`
}

/** Opens the browser print dialog for one or more receipt copies (thermal printer or normal printer). */
export function printReceipts(docs: ReceiptDoc[], ls: LocalSettings): Promise<void> {
  return new Promise(resolve => {
    const f = document.createElement('iframe')
    f.style.position = 'fixed'; f.style.width = '0'; f.style.height = '0'; f.style.border = '0'; f.style.left = '-1000px'
    document.body.appendChild(f)
    const html = `<!doctype html><html lang="ur" dir="rtl"><head><meta charset="utf-8"><title>${esc(docs[0]?.title ?? 'رسید')}</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Naskh+Arabic:wght@400;700&family=Noto+Nastaliq+Urdu:wght@500;700&display=swap">
<style>@page{size:${ls.paperWidthMm}mm auto;margin:2mm}body{margin:0}${receiptCss(ls)}.receipt{page-break-after:always}.receipt:last-child{page-break-after:auto}</style></head>
<body>${docs.map(d => `<div class="receipt">${receiptBody(d)}</div>`).join('')}</body></html>`
    const w = f.contentWindow!
    w.document.open(); w.document.write(html); w.document.close()
    const go = () => {
      w.focus(); w.print()
      setTimeout(() => { f.remove(); resolve() }, 1000)
    }
    const fonts = (w.document as Document & { fonts?: FontFaceSet }).fonts
    if (fonts?.ready) fonts.ready.then(() => setTimeout(go, 150)); else setTimeout(go, 800)
  })
}
