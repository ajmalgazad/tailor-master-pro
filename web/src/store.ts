import { COLLS, DEFAULTS, DEFAULT_SHOP } from './types'
import type { Coll, ShopSettings, Tables } from './types'
import { SPAN } from './calc'

/** One change: a full record, or null = deleted (tombstone). */
export interface Op { coll: Coll; id: number; data: Record<string, unknown> | null }

export interface Backend {
  kind: 'demo' | 'cloud'
  commit(ops: Op[]): Promise<void>
  saveShop(s: ShopSettings): Promise<void>
  saveLogo(dataUrl: string): Promise<void>
}

export class UserError extends Error {}

type Data = { [K in Coll]: Map<number, Tables[K]> }

function emptyData(): Data {
  const d = {} as Record<Coll, Map<number, unknown>>
  COLLS.forEach(c => { d[c] = new Map() })
  return d as unknown as Data
}

/** Fill missing fields so older or newer records never break the screens. */
export function normalize<K extends Coll>(coll: K, raw: Record<string, unknown>): Tables[K] {
  const out: Record<string, unknown> = { ...(DEFAULTS[coll] as object) }
  for (const k of Object.keys(raw)) if (!k.startsWith('_')) out[k] = raw[k]
  out.id = Number(raw.id)
  return out as unknown as Tables[K]
}

/**
 * In-memory copy of the shop's data. The cloud (or the demo storage) fills it; screens read it;
 * every change is made through a Tx and committed as one batch.
 */
export class Store {
  data: Data = emptyData()
  shop: ShopSettings = { ...DEFAULT_SHOP }
  logo = ''
  device = 0
  version = 0
  ready = false
  backend: Backend | null = null
  private subs = new Set<() => void>()

  subscribe(f: () => void) { this.subs.add(f); return () => { this.subs.delete(f) } }
  notify() { this.version++; this.subs.forEach(f => f()) }

  list<K extends Coll>(c: K): Tables[K][] { return Array.from(this.data[c].values()) as Tables[K][] }
  get<K extends Coll>(c: K, id: number | null | undefined): Tables[K] | undefined {
    return id == null ? undefined : (this.data[c].get(id) as Tables[K] | undefined)
  }

  applyRemote(coll: Coll, id: number, raw: Record<string, unknown> | null) {
    if (raw === null || raw._del === true) this.data[coll].delete(id)
    else (this.data[coll] as Map<number, unknown>).set(id, normalize(coll, { ...raw, id }))
  }

  reset() { this.data = emptyData(); this.notify() }

  /** Runs [body] with a transaction, then applies and saves its changes in one batch. */
  async tx<T>(body: (t: Tx) => T): Promise<T> {
    if (!this.backend) throw new UserError('ڈیٹا ابھی تیار نہیں')
    const t = new Tx(this)
    const r = body(t)
    const ops = t.ops()
    if (ops.length) {
      ops.forEach(o => this.applyRemote(o.coll, o.id, o.data))
      this.notify()
      await this.backend.commit(ops)
    }
    return r
  }
}

/** Reads see the transaction's own pending changes; new records get ids in this device's range. */
export class Tx {
  private overlay = new Map<string, Record<string, unknown> | null>()
  private order: string[] = []
  private maxIds = new Map<Coll, number>()
  constructor(private s: Store) {}

  private key(c: Coll, id: number) { return c + '/' + id }

  get<K extends Coll>(c: K, id: number | null | undefined): Tables[K] | undefined {
    if (id == null) return undefined
    const k = this.key(c, id)
    if (this.overlay.has(k)) { const v = this.overlay.get(k); return v === null ? undefined : (v as unknown as Tables[K]) }
    return this.s.get(c, id)
  }

  list<K extends Coll>(c: K): Tables[K][] {
    const base = new Map(this.s.data[c] as Map<number, Tables[K]>)
    for (const k of this.order) {
      const [cc, ids] = k.split('/')
      if (cc !== c) continue
      const v = this.overlay.get(k)
      if (v === null) base.delete(Number(ids)); else base.set(Number(ids), v as unknown as Tables[K])
    }
    return Array.from(base.values())
  }

  private nextId(c: Coll): number {
    const lo = this.s.device * SPAN, hi = lo + SPAN
    let max = this.maxIds.get(c) ?? lo
    if (!this.maxIds.has(c)) for (const id of this.s.data[c].keys()) if (id >= lo && id < hi && id > max) max = id
    const n = max + 1
    this.maxIds.set(c, n)
    return n
  }

  insert<K extends Coll>(c: K, rec: Omit<Tables[K], 'id'> & { id?: number }): Tables[K] {
    const id = rec.id && rec.id > 0 ? rec.id : this.nextId(c)
    const full = { ...rec, id } as unknown as Tables[K]
    this.put(c, full)
    return full
  }

  put<K extends Coll>(c: K, rec: Tables[K]) {
    const k = this.key(c, (rec as { id: number }).id)
    if (!this.overlay.has(k)) this.order.push(k)
    this.overlay.set(k, { ...(rec as object) } as Record<string, unknown>)
  }

  del<K extends Coll>(c: K, rec: Tables[K]) {
    const k = this.key(c, (rec as { id: number }).id)
    if (!this.overlay.has(k)) this.order.push(k)
    this.overlay.set(k, null)
  }

  ops(): Op[] {
    return this.order.map(k => { const [c, id] = k.split('/'); return { coll: c as Coll, id: Number(id), data: this.overlay.get(k) ?? null } })
  }
}

export const store = new Store()
