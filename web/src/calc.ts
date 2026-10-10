// Port of domain/Calc.kt, Dates.kt and Money — identical results on phone and web.

export interface Line { qty: number; rate: number }
export interface Totals { itemsTotal: number; chargesTotal: number; subTotal: number; discount: number; net: number }

export const Calc = {
  lineTotal: (qty: number, rate: number) => qty * rate,

  discountAmount(subTotal: number, isPercent: boolean, input: number): number {
    if (!(input > 0) || subTotal <= 0) return 0
    const d = isPercent ? Math.round((subTotal * input) / 100) : Math.round(input * 100)
    return Math.min(Math.max(d, 0), subTotal)
  },

  totals(lines: Line[], charges: number[], isPercent: boolean, input: number): Totals {
    const items = lines.reduce((s, l) => s + Calc.lineTotal(l.qty, l.rate), 0)
    const chg = charges.reduce((s, c) => s + c, 0)
    const sub = items + chg
    const disc = Calc.discountAmount(sub, isPercent, input)
    return { itemsTotal: items, chargesTotal: chg, subTotal: sub, discount: disc, net: sub - disc }
  },

  validate(lines: Line[], charges: number[], isPercent: boolean, input: number): string | null {
    if (lines.length === 0) return 'کم از کم ایک کپڑا شامل کریں'
    if (lines.some(l => !(l.qty > 0) || !Number.isInteger(l.qty))) return 'تعداد صفر یا منفی نہیں ہو سکتی'
    if (lines.some(l => l.rate < 0)) return 'ریٹ منفی نہیں ہو سکتا'
    if (charges.some(c => c < 0)) return 'اضافی چارجز منفی نہیں ہو سکتے'
    if (input < 0) return 'رعایت منفی نہیں ہو سکتی'
    if (isPercent && input > 100) return 'رعایت ۱۰۰ فیصد سے زیادہ نہیں ہو سکتی'
    const sub = lines.reduce((s, l) => s + l.qty * l.rate, 0) + charges.reduce((s, c) => s + c, 0)
    if (!isPercent && Math.round(input * 100) > sub) return 'رعایت کل رقم سے زیادہ نہیں ہو سکتی'
    return null
  },

  outstanding: (net: number, paid: number) => net - paid,

  customerBalance: (opening: number, orderNets: number[], effects: number[]) =>
    opening + orderNets.reduce((s, x) => s + x, 0) + effects.reduce((s, x) => s + x, 0),

  /** "1500", "1,500", "1500.50", Urdu digits → paisa. null when invalid or negative. */
  parseMoney(text: string): number | null {
    const t = Digits.toLatin(text).replace(/,/g, '').replace('Rs', '').replace('روپے', '').trim()
    if (t === '') return 0
    if (!/^\d*\.?\d*$/.test(t) || t === '.') return null
    const n = Number(t)
    if (!isFinite(n) || n < 0) return null
    return Math.round(n * 100)
  },
  parseInt(text: string): number | null { const n = Number(Digits.toLatin(text).trim()); return Number.isInteger(n) ? n : null },
  parseDouble(text: string): number | null { const t = Digits.toLatin(text).trim(); if (t === '') return 0; const n = Number(t); return isFinite(n) ? n : null },
  moneyInput(paisa: number): string { const r = paisa / 100; return Number.isInteger(r) ? String(r) : String(Number(r.toFixed(2))) },
}

export const Digits = {
  toLatin: (s: string) => s.replace(/[۰-۹]/g, c => String(c.charCodeAt(0) - 0x06F0)).replace(/[٠-٩]/g, c => String(c.charCodeAt(0) - 0x0660)).replace(/٫/g, '.'),
  toUrdu: (s: string) => s.replace(/[0-9]/g, c => String.fromCharCode(0x06F0 + Number(c))),
}

export const Money = {
  plain(paisa: number): string {
    const neg = paisa < 0
    const abs = Math.abs(paisa)
    const rupees = Math.floor(abs / 100)
    const p = abs % 100
    const r = rupees.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    const s = p === 0 ? r : r + '.' + String(p).padStart(2, '0')
    return neg ? '-' + s : s
  },
}

export const DAY = 24 * 60 * 60 * 1000
export const Dates = {
  startOfDay(ms: number) { const d = new Date(ms); d.setHours(0, 0, 0, 0); return d.getTime() },
  endOfDay(ms: number) { return Dates.startOfDay(ms) + DAY - 1 },
  startOfMonth(ms: number) { const d = new Date(Dates.startOfDay(ms)); d.setDate(1); return d.getTime() },
  endOfMonth(ms: number) { const d = new Date(Dates.startOfMonth(ms)); d.setMonth(d.getMonth() + 1); return d.getTime() - 1 },
  addDays(ms: number, n: number) { const d = new Date(ms); d.setDate(d.getDate() + n); return d.getTime() },
  today() { return Dates.startOfDay(Date.now()) },
  format(ms: number, pattern: string): string {
    const d = new Date(ms)
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
    const h = d.getHours()
    const p2 = (n: number) => String(n).padStart(2, '0')
    return pattern.replace(/yyyy|MMM|MM|dd|HH|hh|mm|a/g, tok => {
      switch (tok) {
        case 'yyyy': return String(d.getFullYear())
        case 'MMM': return months[d.getMonth()]
        case 'MM': return p2(d.getMonth() + 1)
        case 'dd': return p2(d.getDate())
        case 'HH': return p2(h)
        case 'hh': return p2(h % 12 === 0 ? 12 : h % 12)
        case 'mm': return p2(d.getMinutes())
        case 'a': return h < 12 ? 'AM' : 'PM'
        default: return tok
      }
    })
  },
  /** yyyy-mm-dd for <input type=date> */
  toInput(ms: number) { return Dates.format(ms, 'yyyy-MM-dd') },
  fromInput(v: string, keepTime?: number) {
    const [y, m, dd] = v.split('-').map(Number)
    const d = keepTime ? new Date(keepTime) : new Date()
    d.setFullYear(y, m - 1, dd)
    return d.getTime()
  },
}

/** Device id ranges and human codes — same as DeviceIds.kt. */
export const SPAN = 10_000_000
export const Codes = {
  code(prefix: string, id: number, pad: number) {
    return id < SPAN ? `${prefix}-${String(id).padStart(pad, '0')}` : `${prefix}-${Math.floor(id / SPAN)}-${String(id % SPAN).padStart(4, '0')}`
  },
  parse(text: string): number | null {
    const t = Digits.toLatin(text).trim().toUpperCase().replace(/\s/g, '')
    const body = t.replace(/^[A-Z]+-?/, '')
    const parts = body.split(/[-/]/).filter(Boolean)
    if (parts.length === 1) { const n = Number(parts[0]); return Number.isInteger(n) ? n : null }
    if (parts.length === 2) { const a = Number(parts[0]), b = Number(parts[1]); return Number.isInteger(a) && Number.isInteger(b) ? a * SPAN + b : null }
    return null
  },
}
