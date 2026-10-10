import { initializeApp, getApps } from 'firebase/app'
import type { FirebaseApp, FirebaseOptions } from 'firebase/app'
import { getAuth, signInWithEmailAndPassword, signOut, onAuthStateChanged, connectAuthEmulator } from 'firebase/auth'
import type { Auth, User } from 'firebase/auth'
import {
  initializeFirestore, persistentLocalCache, persistentMultipleTabManager, memoryLocalCache, connectFirestoreEmulator,
  collection, doc, onSnapshot, writeBatch, serverTimestamp, setDoc, deleteDoc, getDocFromServer, runTransaction,
} from 'firebase/firestore'
import type { Firestore, Unsubscribe } from 'firebase/firestore'
import { COLLS, DEFAULT_SHOP } from './types'
import type { ShopSettings } from './types'
import { store, UserError } from './store'
import type { Backend, Op } from './store'
import { firebaseConfig } from './firebase-config'

export type CloudState = { phase: 'off' | 'signedOut' | 'loading' | 'ready' | 'needsShop'; email: string; online: boolean; error: string; pending: number }
export const cloudState: CloudState = { phase: 'off', email: '', online: navigator.onLine, error: '', pending: 0 }
const stateSubs = new Set<() => void>()
export const onCloudState = (f: () => void) => { stateSubs.add(f); return () => { stateSubs.delete(f) } }
function setState(p: Partial<CloudState>) { Object.assign(cloudState, p); stateSubs.forEach(f => f()) }

let app: FirebaseApp | null = null
let auth: Auth | null = null
let db: Firestore | null = null
let uid = ''
let unsubs: Unsubscribe[] = []

/** Test hook: ?emulator=localhost runs against the Firebase emulator with a demo project. */
function emulatorHost(): string | null { return new URLSearchParams(location.search).get('emulator') }

export function cloudConfigured(): boolean { return firebaseConfig != null || emulatorHost() != null }

export function cloudInit(onUser: (u: User | null) => void) {
  const emu = emulatorHost()
  const cfg: FirebaseOptions | null = emu ? { apiKey: 'fake-api-key', projectId: 'demo-tailor', appId: '1:1:web:1', authDomain: 'localhost' } : firebaseConfig
  if (!cfg) { setState({ phase: 'off' }); return }
  app = getApps()[0] ?? initializeApp(cfg)
  auth = getAuth(app)
  let cache
  try { cache = persistentLocalCache({ tabManager: persistentMultipleTabManager() }) } catch { cache = memoryLocalCache() }
  db = initializeFirestore(app, { localCache: emu ? memoryLocalCache() : cache })
  if (emu) { connectAuthEmulator(auth, `http://${emu}:9099`, { disableWarnings: true }); connectFirestoreEmulator(db, emu, 8080) }
  setState({ phase: 'signedOut' })
  window.addEventListener('online', () => setState({ online: true }))
  window.addEventListener('offline', () => setState({ online: false }))
  onAuthStateChanged(auth, u => onUser(u))
}

function authMessage(e: unknown): string {
  const code = (e as { code?: string }).code ?? ''
  if (code.includes('invalid-email')) return 'ای میل درست نہیں'
  if (code.includes('wrong-password') || code.includes('invalid-credential') || code.includes('invalid-login')) return 'ای میل یا پاس ورڈ غلط ہے'
  if (code.includes('user-not-found')) return 'یہ ای میل رجسٹرڈ نہیں'
  if (code.includes('too-many-requests')) return 'بہت زیادہ کوششیں۔ کچھ دیر بعد دوبارہ کریں'
  if (code.includes('network')) return 'انٹرنیٹ نہیں ہے'
  return 'لاگ اِن نہیں ہو سکا: ' + code
}

export async function cloudSignIn(email: string, password: string) {
  if (!auth) throw new UserError('کلاؤڈ سیٹ نہیں')
  try { await signInWithEmailAndPassword(auth, email.trim(), password) } catch (e) { throw new UserError(authMessage(e)) }
}

export async function cloudSignOut() { stopListeners(); if (auth) await signOut(auth); store.reset(); setState({ phase: 'signedOut', email: '' }) }

function stopListeners() { unsubs.forEach(u => u()); unsubs = [] }

const deviceKey = () => 'tmp-device-' + uid

/** After sign-in: decide whether this shop already exists in the cloud, then start syncing. */
export async function cloudOpen(user: User) {
  if (!db) return
  uid = user.uid
  setState({ phase: 'loading', email: user.email ?? '', error: '' })
  const meta = doc(db, 'shops', uid, 'meta', 'devices')
  let device = Number(localStorage.getItem(deviceKey()) ?? 'NaN')
  if (!Number.isInteger(device)) {
    let exists = false
    try { exists = (await getDocFromServer(meta)).exists() } catch { setState({ phase: 'signedOut', error: 'کلاؤڈ سے رابطہ نہیں ہو سکا۔ انٹرنیٹ چیک کریں' }); return }
    if (!exists) { setState({ phase: 'needsShop' }); return }
    device = await runTransaction(db, async tr => {
      const s = await tr.get(meta)
      const n = Number(s.get('next') ?? 1)
      tr.set(meta, { next: n + 1 }, { merge: true })
      return n
    })
    localStorage.setItem(deviceKey(), String(device))
  }
  startListeners(device)
}

/** Empty cloud: start a brand-new shop from the web (the phone's data will NOT be included). */
export async function cloudCreateShop() {
  if (!db || !uid) return
  await setDoc(doc(db, 'shops', uid, 'meta', 'devices'), { next: 1, created: serverTimestamp() })
  localStorage.setItem(deviceKey(), '0')
  startListeners(0)
}

function startListeners(device: number) {
  if (!db) return
  stopListeners()
  store.reset()
  store.device = device
  store.backend = new CloudBackend()
  store.shop = { ...DEFAULT_SHOP }
  const waiting = new Set<string>([...COLLS, 'settings'])
  let scheduled = false
  const refresh = () => { if (scheduled) return; scheduled = true; requestAnimationFrame(() => { scheduled = false; store.notify() }) }
  const loaded = (k: string) => {
    if (!waiting.delete(k)) return
    if (waiting.size === 0) { store.ready = true; setState({ phase: 'ready' }); store.notify() }
  }
  for (const c of COLLS) {
    unsubs.push(onSnapshot(collection(db, 'shops', uid, c), snap => {
      snap.docChanges().forEach(ch => {
        const id = Number(ch.doc.id)
        if (!Number.isFinite(id)) return
        store.applyRemote(c, id, ch.type === 'removed' ? null : (ch.doc.data() as Record<string, unknown>))
      })
      loaded(c); refresh()
    }, err => setState({ error: err.code === 'permission-denied' ? 'کلاؤڈ نے اجازت نہیں دی (Rules)' : 'سنک میں خرابی: ' + err.code })))
  }
  unsubs.push(onSnapshot(doc(db, 'shops', uid, 'meta', 'settings'), s => {
    if (s.exists()) {
      const d = s.data() as Record<string, unknown>
      const out: Record<string, unknown> = {}
      Object.keys(DEFAULT_SHOP).forEach(k => { if (k in d) out[k] = d[k] })
      store.shop = { ...DEFAULT_SHOP, ...out } as ShopSettings
    }
    loaded('settings'); refresh()
  }))
  unsubs.push(onSnapshot(doc(db, 'shops', uid, 'meta', 'logo'), s => {
    const png = s.exists() ? (s.get('png') as string | undefined) : undefined
    store.logo = png ? 'data:image/png;base64,' + png : ''
    refresh()
  }))
}

class CloudBackend implements Backend {
  kind = 'cloud' as const
  async commit(ops: Op[]) {
    if (!db) return
    const latest = new Map<string, Op>()
    ops.forEach(o => latest.set(o.coll + '/' + o.id, o))
    const list = Array.from(latest.values())
    for (let i = 0; i < list.length; i += 400) {
      const b = writeBatch(db)
      list.slice(i, i + 400).forEach(o => {
        const data = o.data === null ? { id: o.id, _del: true } : o.data
        b.set(doc(db!, 'shops', uid, o.coll, String(o.id)), { ...data, _s: serverTimestamp(), _d: store.device })
      })
      setState({ pending: cloudState.pending + 1 })
      // Do not wait for the server: Firestore keeps the batch safely queued while offline.
      b.commit().then(() => setState({ pending: Math.max(0, cloudState.pending - 1), error: '' }))
        .catch(e => setState({ pending: Math.max(0, cloudState.pending - 1), error: 'کلاؤڈ میں محفوظ نہیں ہو سکا: ' + (e?.code ?? e) }))
    }
  }
  async saveShop(s: ShopSettings) {
    store.shop = s; store.notify()
    if (db) await setDoc(doc(db, 'shops', uid, 'meta', 'settings'), { ...s, _s: serverTimestamp() })
  }
  async saveLogo(dataUrl: string) {
    store.logo = dataUrl; store.notify()
    if (!db) return
    const ref = doc(db, 'shops', uid, 'meta', 'logo')
    if (!dataUrl) await deleteDoc(ref)
    else await setDoc(ref, { png: dataUrl.replace(/^data:image\/\w+;base64,/, ''), _s: serverTimestamp() })
  }
}
