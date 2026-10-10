// App language. Urdu is the original text in the code and the data; when English is chosen every
// text is translated just before it is shown, using i18n/en.json (the same dictionary the phone app uses).
// Lookup: exact text → text with {0} blanks → pieces split at separators → known phrases word by word.
// Anything not in the dictionary (customer names, notes) stays as written.
import en from '../../i18n/en.json'

export type Lang = 'ur' | 'en'
const KEY = 'tm-lang'

let lang: Lang = 'ur'
try { lang = localStorage.getItem(KEY) === 'en' ? 'en' : 'ur' } catch { /* private mode */ }

export const getLang = (): Lang => lang
export const isEn = () => lang === 'en'

const URDU = /[؀-ۿ]/
const PH = /\{(\d+)\}/g
const dict = new Map<string, string>()
interface Tpl { re: RegExp; order: number[]; en: string; lit: number }
let templates: Tpl[] = []
let maxWords = 1
const cache = new Map<string, string>()
const EDGE_UR = /^[\s:•—،\-]+|[\s:•—،\-]+$/g
const EDGE_EN = /^[\s:•—,\-]+|[\s:•—,\-]+$/g

function escapeRe(s: string) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }

export function loadDictionary(d: Record<string, string>) {
  dict.clear(); cache.clear()
  const t: Tpl[] = []
  for (const [k, v] of Object.entries(d)) {
    if (/\{\d+\}/.test(k)) {
      const order: number[] = []
      let src = '^', last = 0
      for (const m of k.matchAll(PH)) {
        src += escapeRe(k.slice(last, m.index)) + '([\\s\\S]*?)'
        order.push(Number(m[1]))
        last = m.index! + m[0].length
      }
      src += escapeRe(k.slice(last)) + '$'
      t.push({ re: new RegExp(src), order, en: v, lit: k.replace(PH, '').trim().length })
    } else {
      dict.set(k, v)
      const tr = k.trim()
      if (tr !== k && tr && !dict.has(tr)) dict.set(tr, v.trim())
      // "خرابی: " is also known as "خرابی", so pieces split at separators are found
      const bare = k.replace(EDGE_UR, '')
      if (bare && bare !== tr && !dict.has(bare)) dict.set(bare, v.replace(EDGE_EN, ''))
      maxWords = Math.max(maxWords, tr.split(' ').length)
    }
  }
  templates = t.sort((a, b) => b.lit - a.lit)
}
loadDictionary(en as Record<string, string>)

const SEP = / • |\n| — | ← | → |، |: | \| /g
const SEP1 = / • |\n| — | ← | → |، |: | \| /
const SEP_EN: Record<string, string> = { '، ': ', ', ' ← ': ' → ' }
const PUNCT_EN: Record<string, string> = { '۔': '.', '،': ',', '؟': '?' }

/** Translates s when English is on; returns it unchanged otherwise. */
export function tr(s: string): string {
  if (lang !== 'en' || !s || !URDU.test(s)) return s
  const c = cache.get(s)
  if (c !== undefined) return c
  const r = translate(s, 0)
  if (cache.size > 5000) cache.clear()
  cache.set(s, r)
  return r
}

/** Like tr, but whatever the current language (used by tests). */
export function toEnglish(s: string): string { return URDU.test(s) ? translate(s, 0) : s }

function translate(s: string, depth: number): string {
  if (!URDU.test(s)) return s
  const exact = dict.get(s)
  if (exact !== undefined) return exact
  const lead = s.match(/^\s*/)![0], trail = s.match(/\s*$/)![0]
  if (lead.length + trail.length >= s.length) return s
  const core = s.slice(lead.length, s.length - trail.length)
  if (core !== s) { const h = dict.get(core); if (h !== undefined) return lead + h + trail }
  if (depth < 4) {
    for (const t of templates) {
      const m = t.re.exec(core)
      // a blank must not swallow a separator, e.g. "بکنگ {0}" must not match "بکنگ محفوظ ہو گئی — B-1"
      if (!m || m.slice(1).some(g => SEP1.test(g))) continue
      let out = t.en
      m.slice(1).forEach((g, i) => { out = out.replace(`{${t.order[i]}}`, translate(g, depth + 1)) })
      return lead + out + trail
    }
    SEP.lastIndex = 0
    if (SEP.test(core)) {
      SEP.lastIndex = 0
      let out = '', pos = 0
      for (const m of core.matchAll(SEP)) {
        out += translate(core.slice(pos, m.index), depth + 1) + (SEP_EN[m[0]] ?? m[0])
        pos = m.index! + m[0].length
      }
      out += translate(core.slice(pos), depth + 1)
      return lead + out + trail
    }
  }
  // "Ali (محمد اسلم (نمونہ))": translate what is inside brackets on its own
  const plain = phrases(core)
  if (depth < 4 && /\(/.test(core) && URDU.test(plain)) {
    const inner = plain.replace(/\(((?:[^()]|\([^()]*\))*)\)/g, (_, x: string) => '(' + translate(x, depth + 1) + ')')
    if (urduCount(inner) < urduCount(plain)) return lead + inner + trail
  }
  return lead + plain + trail
}

const urduCount = (s: string) => (s.match(/[\u0600-\u06FF]/g) ?? []).length

function phrases(s: string): string {
  const words = s.split(' ')
  const out: string[] = []
  let i = 0
  while (i < words.length) {
    let done = false
    if (URDU.test(words[i])) {
      for (let n = Math.min(maxWords, words.length - i); n >= 1; n--) {
        const p = words.slice(i, i + n).join(' ')
        let hit = dict.get(p)
        if (hit === undefined) {
          const punct = p.match(/[۔،:?؟]+$/)?.[0] ?? ''
          const h2 = punct ? dict.get(p.slice(0, -punct.length)) : undefined
          if (h2 !== undefined) hit = h2 + [...punct].map(c => PUNCT_EN[c] ?? c).join('')
        }
        if (hit !== undefined) { out.push(hit); i += n; done = true; break }
      }
    }
    if (!done) { out.push(words[i]); i++ }
  }
  return out.join(' ')
}

/** Switches language; the page reloads so every screen is drawn again in the new direction. */
export function setLang(l: Lang) {
  try { localStorage.setItem(KEY, l) } catch { /* ignore */ }
  location.reload()
}

// ---------------------------------------------------------------- page translation
// React writes Urdu text; this watcher swaps it for English right after, so every screen,
// label and stored value (garment names, statuses...) appears in English without
// touching each component. React only rewrites a text node when its own value changes,
// and the watcher translates it again then.

const ATTRS = ['placeholder', 'title', 'aria-label']

function translateNode(n: Node) {
  if (n.nodeType === Node.TEXT_NODE) {
    const v = n.nodeValue ?? ''
    if (URDU.test(v)) { const t = tr(v); if (t !== v) n.nodeValue = t }
  } else if (n.nodeType === Node.ELEMENT_NODE) {
    const el = n as Element
    if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE' || el.tagName === 'TEXTAREA') return
    for (const a of ATTRS) {
      const v = el.getAttribute(a)
      if (v && URDU.test(v)) { const t = tr(v); if (t !== v) el.setAttribute(a, t) }
    }
    if (el.tagName === 'INPUT' && ((el as HTMLInputElement).type === 'button' || (el as HTMLInputElement).type === 'submit')) {
      const i = el as HTMLInputElement
      if (URDU.test(i.value)) i.value = tr(i.value)
    }
    el.childNodes.forEach(translateNode)
  }
}

/** Translates a whole document (used for print windows) and sets its direction. */
export function translateDocument(doc: Document) {
  if (!isEn()) return
  doc.documentElement.setAttribute('dir', 'ltr')
  doc.documentElement.setAttribute('lang', 'en')
  if (doc.body) { doc.body.style.direction = 'ltr'; translateNode(doc.body) }
  if (URDU.test(doc.title)) doc.title = tr(doc.title)
}

/** Translates an HTML string (receipts and reports opened for printing). */
export function translateHtml(html: string): string {
  if (!isEn()) return html
  const d = new DOMParser().parseFromString(html, 'text/html')
  translateDocument(d)
  d.querySelectorAll('style').forEach(s => { s.textContent = (s.textContent ?? '').replace(/direction:\s*rtl/g, 'direction:ltr').replace(/text-align:\s*right/g, 'text-align:left') })
  return '<!doctype html>' + d.documentElement.outerHTML
}

export function startPageTranslation() {
  document.documentElement.lang = lang
  document.documentElement.dir = lang === 'en' ? 'ltr' : 'rtl'
  if (lang !== 'en') return
  document.body.classList.add('lang-en')
  translateNode(document.body)
  const obs = new MutationObserver(list => {
    for (const m of list) {
      if (m.type === 'characterData') translateNode(m.target)
      else if (m.type === 'attributes') translateNode(m.target)
      else m.addedNodes.forEach(translateNode)
    }
  })
  obs.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS })
  if (URDU.test(document.title)) document.title = tr(document.title)
  // browser pop-ups
  const { alert, confirm, prompt } = window
  window.alert = (m?: unknown) => alert.call(window, typeof m === 'string' ? tr(m) : m)
  window.confirm = (m?: string) => confirm.call(window, m ? tr(m) : m)
  window.prompt = (m?: string, d?: string) => prompt.call(window, m ? tr(m) : m, d)
}
