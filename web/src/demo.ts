import { COLLS, DEFAULT_SHOP } from './types'
import type { ShopSettings } from './types'
import { store } from './store'
import type { Backend, Op } from './store'

const KEY = 'tmp-demo-v1'

/** Demo mode: data stays only in this browser (for trying the app without an account). */
export class DemoBackend implements Backend {
  kind = 'demo' as const
  private save() {
    try {
      const out: Record<string, unknown[]> = {}
      COLLS.forEach(c => { out[c] = store.list(c) })
      localStorage.setItem(KEY, JSON.stringify({ data: out, shop: store.shop, logo: store.logo }))
    } catch { /* storage full or blocked: keep working in memory */ }
  }
  async commit(_ops: Op[]) { this.save() }
  async saveShop(s: ShopSettings) { store.shop = s; this.save(); store.notify() }
  async saveLogo(dataUrl: string) { store.logo = dataUrl; this.save(); store.notify() }

  static start() {
    store.reset()
    store.device = 0
    store.backend = new DemoBackend()
    try {
      const raw = localStorage.getItem(KEY)
      if (raw) {
        const j = JSON.parse(raw)
        COLLS.forEach(c => (j.data?.[c] ?? []).forEach((r: Record<string, unknown>) => store.applyRemote(c, Number(r.id), r)))
        store.shop = { ...DEFAULT_SHOP, ...(j.shop ?? {}) }
        store.logo = j.logo ?? ''
      } else store.shop = { ...DEFAULT_SHOP }
    } catch { store.shop = { ...DEFAULT_SHOP } }
    store.ready = true
    store.notify()
  }

  static clear() { try { localStorage.removeItem(KEY) } catch { /* ignore */ } }
}
