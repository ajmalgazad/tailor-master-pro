import React, { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import { store, UserError } from '../store'
import { Calc, Dates, Digits } from '../calc'
import { Fmt } from '../fmt'
import type { Customer } from '../types'
import { Q } from '../repo'
import { Codes } from '../calc'

// ---------------------------------------------------------------- store + navigation
export function useStoreVersion(): number {
  return useSyncExternalStore(f => store.subscribe(f), () => store.version)
}

export type Screen = { name: string; [k: string]: unknown }
interface Nav { stack: Screen[]; go(s: Screen): void; back(): void; replace(s: Screen): void; home(): void; jump(s: Screen): void; state<T>(init: () => T): T }
export const NavCtx = createContext<Nav>(null as unknown as Nav)
export const useNav = () => useContext(NavCtx)

export function useNavigator(): Nav {
  const [stack, setStack] = useState<Screen[]>([{ name: 'home' }])
  const states = useMemo(() => new Map<Screen, unknown>(), [])
  useEffect(() => {
    history.replaceState({ d: 1 }, '')
    // Each pushed entry stores its depth, so browser Back (or going home) trims the stack correctly.
    const onPop = (e: PopStateEvent) => { const d = Math.max(1, Number(e.state?.d ?? 1)); setStack(s => s.slice(0, d)) }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])
  return {
    stack,
    go: s => { history.pushState({ d: stack.length + 1 }, ''); setStack(st => [...st, s]); window.scrollTo(0, 0) },
    back: () => { if (stack.length > 1) history.back() },
    replace: s => { setStack(st => [...st.slice(0, -1), s]); window.scrollTo(0, 0) },
    home: () => { const n = stack.length - 1; if (n > 0) history.go(-n) },
    /** Opens a top-level section from the side menu: home → section. */
    jump: s => { history.pushState({ d: 2 }, ''); setStack(s.name === 'home' ? [{ name: 'home' }] : [{ name: 'home' }, s]); window.scrollTo(0, 0) },
    state<T>(init: () => T): T {
      const cur = stack[stack.length - 1]
      if (!states.has(cur)) states.set(cur, init())
      return states.get(cur) as T
    },
  }
}

// ---------------------------------------------------------------- messages
type Msg = { text: string; kind: 'ok' | 'warn' | 'err'; id: number }
let msgSet: ((m: Msg | null) => void) | null = null
export const msg = {
  ok: (t: string) => msgSet?.({ text: t, kind: 'ok', id: Date.now() }),
  warn: (t: string) => msgSet?.({ text: t, kind: 'warn', id: Date.now() }),
  err: (t: string) => msgSet?.({ text: t, kind: 'err', id: Date.now() }),
}
export function MessageHost() {
  const [m, setM] = useState<Msg | null>(null)
  useEffect(() => { msgSet = setM; return () => { msgSet = null } }, [])
  useEffect(() => { if (!m) return; const t = setTimeout(() => setM(null), m.kind === 'err' ? 6000 : 3000); return () => clearTimeout(t) }, [m])
  if (!m) return null
  return <div className={'toast ' + m.kind} role="status" onClick={() => setM(null)}><b>{m.kind === 'ok' ? '✓' : m.kind === 'warn' ? '!' : '✕'}</b> {m.text}</div>
}

/** Runs an action and shows a clear Urdu message for any error. */
export async function act(fn: () => Promise<unknown> | unknown, success?: string) {
  try { await fn(); if (success) msg.ok(success) } catch (e) {
    if (e instanceof UserError) msg.err(e.message)
    else { console.error(e); msg.err('خرابی: ' + ((e as Error)?.message ?? e)) }
  }
}

// ---------------------------------------------------------------- layout
export function Page({ title, children, actions, back = true }: { title: string; children: React.ReactNode; actions?: React.ReactNode; back?: boolean }) {
  const nav = useNav()
  return (
    <div className="page">
      <header className="bar">
        {back && nav.stack.length > 1 ? <button className="icon" aria-label="واپس" onClick={() => nav.back()}>→</button> : <span className="icon-sp" />}
        <h1>{title}</h1>
        <div className="bar-actions">{actions}</div>
      </header>
      <main className="content">{children}</main>
    </div>
  )
}

export function Section({ title, children, className = '' }: { title?: string; children: React.ReactNode; className?: string }) {
  return <section className={'card ' + className}>{title && <h2>{title}</h2>}{children}</section>
}

export function KV({ k, v, bold, tone }: { k: string; v: React.ReactNode; bold?: boolean; tone?: 'ok' | 'bad' | 'warn' }) {
  return <div className="kv"><span>{k}</span><b className={(bold ? 'strong ' : '') + (tone ?? '')}>{v}</b></div>
}

export function Btn({ children, onClick, kind = 'primary', disabled, wide, small, type = 'button' }:
  { children: React.ReactNode; onClick?: () => void; kind?: 'primary' | 'ok' | 'ghost' | 'danger'; disabled?: boolean; wide?: boolean; small?: boolean; type?: 'button' | 'submit' }) {
  return <button type={type} className={`btn ${kind} ${wide ? 'wide' : ''} ${small ? 'small' : ''}`} onClick={onClick} disabled={disabled}>{children}</button>
}

export function Row({ children, wrap = true }: { children: React.ReactNode; wrap?: boolean }) { return <div className={'row' + (wrap ? ' wrap' : '')}>{children}</div> }

let idSeq = 0
const useId = () => useMemo(() => 'f' + (++idSeq), [])

export function Input({ label, value, onChange, type = 'text', multiline, error, placeholder, inputMode }:
  { label: string; value: string; onChange: (v: string) => void; type?: string; multiline?: boolean; error?: string | null; placeholder?: string; inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode'] }) {
  const id = useId()
  return (
    <label className={'field' + (error ? ' has-error' : '')} htmlFor={id}>
      <span>{label}</span>
      {multiline ? <textarea id={id} value={value} onChange={e => onChange(e.target.value)} rows={2} placeholder={placeholder} />
        : <input id={id} type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} inputMode={inputMode} />}
      {error && <small className="err">{error}</small>}
    </label>
  )
}

export function MoneyInput({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  const bad = value.trim() !== '' && Calc.parseMoney(value) == null
  return <Input label={label} value={value} onChange={onChange} inputMode="decimal" error={bad ? 'درست رقم لکھیں (منفی نہیں)' : null} />
}

export function DateInput({ label, value, onChange }: { label: string; value: number; onChange: (ms: number) => void }) {
  const id = useId()
  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <input id={id} type="date" value={Dates.toInput(value)} onChange={e => e.target.value && onChange(Dates.fromInput(e.target.value, value))} />
    </label>
  )
}

export function Select<T>({ label, options, value, labelOf, onChange, keyOf }:
  { label: string; options: T[]; value: T | null | undefined; labelOf: (t: T) => string; onChange: (t: T) => void; keyOf?: (t: T) => string }) {
  const id = useId()
  const k = (t: T) => (keyOf ? keyOf(t) : String(t))
  const idx = options.findIndex(o => value !== undefined && value !== null ? k(o) === k(value) : o === value)
  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <select id={id} value={idx < 0 ? '' : String(idx)} onChange={e => { const i = Number(e.target.value); if (i >= 0) onChange(options[i]) }}>
        {idx < 0 && <option value="">منتخب کریں</option>}
        {options.map((o, i) => <option key={i} value={i}>{labelOf(o)}</option>)}
      </select>
    </label>
  )
}

export function Switch({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return <label className="switch"><input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} /><span>{label}</span></label>
}

export function Chip({ text, tone }: { text: string; tone: string }) { return <span className={'chip ' + tone}>{text}</span> }
export function Empty({ text }: { text: string }) { return <p className="empty">{text}</p> }

export function statusTone(s: string) {
  return ({ READY: 'ok', DELIVERED: 'muted', CANCELLED: 'bad', NEW: 'info' } as Record<string, string>)[s] ?? 'warn'
}

// ---------------------------------------------------------------- dialogs
export function Modal({ title, children, onClose, actions }: { title: string; children: React.ReactNode; onClose: () => void; actions?: React.ReactNode }) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', k); return () => window.removeEventListener('keydown', k)
  }, [onClose])
  return (
    <div className="modal-bg" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={e => e.stopPropagation()}>
        <h2>{title}</h2>
        <div className="modal-body">{children}</div>
        <div className="modal-actions">{actions ?? <Btn kind="ghost" onClick={onClose}>بند کریں</Btn>}</div>
      </div>
    </div>
  )
}

export function Confirm({ title, text, okText = 'ہاں', danger, onClose, onOk }: { title: string; text: string; okText?: string; danger?: boolean; onClose: () => void; onOk: () => void }) {
  return <Modal title={title} onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>نہیں</Btn><Btn kind={danger ? 'danger' : 'primary'} onClick={() => { onClose(); onOk() }}>{okText}</Btn></>}>
    <p style={{ whiteSpace: 'pre-wrap' }}>{text}</p>
  </Modal>
}

export function Prompt({ title, label, initial = '', onClose, onOk }: { title: string; label: string; initial?: string; onClose: () => void; onOk: (v: string) => void }) {
  const [v, setV] = useState(initial)
  return <Modal title={title} onClose={onClose} actions={<><Btn kind="ghost" onClick={onClose}>منسوخ</Btn><Btn onClick={() => { onClose(); onOk(v) }}>ٹھیک ہے</Btn></>}>
    <Input label={label} value={v} onChange={setV} />
  </Modal>
}

export function matchesCustomer(c: Customer, q: string) {
  if (!q.trim()) return true
  const t = Digits.toLatin(q.trim()).toLowerCase()
  return c.name.toLowerCase().includes(t) || c.mobile.includes(t) || c.whatsapp.includes(t) || Codes.code('C', c.id, 4).toLowerCase().includes(t) ||
    String(c.id) === t || Codes.parse(t) === c.id || c.address.includes(t) || c.area.includes(t) || c.city.includes(t) || c.fatherName.includes(t)
}

export function CustomerPicker({ onClose, onPick }: { onClose: () => void; onPick: (c: Customer) => void }) {
  useStoreVersion()
  const [q, setQ] = useState('')
  const list = Q.customerRows(false).filter(r => matchesCustomer(r.customer, q)).slice(0, 100)
  return <Modal title="کسٹمر منتخب کریں" onClose={onClose}>
    <input className="search" autoFocus placeholder="نام، موبائل، کوڈ یا پتہ" value={q} onChange={e => setQ(e.target.value)} aria-label="تلاش" />
    <div className="pick-list">
      {list.map(r => <button key={r.customer.id} className="pick" onClick={() => onPick(r.customer)}>
        <b>{r.customer.name}</b><small>{Fmt.customerCode(r.customer.id)} • {Fmt.d(r.customer.mobile)}</small>
      </button>)}
      {list.length === 0 && <Empty text="کوئی کسٹمر نہیں ملا" />}
    </div>
  </Modal>
}

export function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return <input className="search" value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} aria-label={placeholder} />
}

export function Tabs({ tabs, value, onChange }: { tabs: string[]; value: number; onChange: (i: number) => void }) {
  return <div className="tabs" role="tablist">{tabs.map((t, i) => <button key={i} role="tab" aria-selected={i === value} className={i === value ? 'on' : ''} onClick={() => onChange(i)}>{t}</button>)}</div>
}

export function download(name: string, content: string | Blob, type = 'text/plain') {
  const blob = typeof content === 'string' ? new Blob([content], { type }) : content
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob); a.download = name
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 2000)
}
