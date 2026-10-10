// Same records and field names as the Android app (Entities.kt) and Firestore.
// Money is integer PAISA, dates are epoch milliseconds.

export interface Customer { id: number; name: string; fatherName: string; mobile: string; whatsapp: string; address: string; city: string; area: string; category: string; regDate: number; openingBalance: number; notes: string; active: boolean; mergedIntoId: number | null; sample: boolean }
export interface FamilyMember { id: number; customerId: number; name: string; relation: string; ageGroup: string; mobile: string; fittingPref: string; notes: string; isSelf: boolean; active: boolean }
export interface Garment { id: number; name: string; gender: string; sortOrder: number; defaultRate: number; receiptDesc: string; active: boolean }
export interface MeasurementField { id: number; garmentId: number; label: string; unit: string; required: boolean; sortOrder: number; active: boolean }
export interface MeasureValue { fieldId: number; label: string; value: string }
export interface MeasurementProfile { id: number; customerId: number; memberId: number; garmentId: number; name: string; date: number; unit: string; valuesJson: string; fitting: string; instructions: string; notes: string; isDefault: boolean; archived: boolean; previousId: number | null; lastUsed: number | null }
export interface CustomRate { id: number; garmentId: number; customerId: number | null; memberId: number | null; rate: number; active: boolean; note: string; updatedAt: number }
export interface RateHistory { id: number; garmentId: number; customerId: number | null; memberId: number | null; oldRate: number; newRate: number; at: number; note: string }
export interface ExtraService { id: number; name: string; kind: string; amount: number; active: boolean }
export interface Charge { name: string; amount: number }
export interface Order { id: number; customerId: number; memberId: number; bookingDate: number; deliveryDate: number; urgent: boolean; instructions: string; internalNotes: string; status: string; isDraft: boolean; chargesJson: string; discountType: string; discountInput: number; itemsTotal: number; chargesTotal: number; discountAmount: number; netAmount: number; createdAt: number; updatedAt: number; rebookedFromId: number | null }
export interface MeasureSnapshot { profileId: number | null; profileName: string; unit: string; date: number; fitting: string; instructions: string; values: MeasureValue[] }
export interface OrderItem { id: number; orderId: number; garmentId: number; garmentName: string; memberId: number; memberName: string; qty: number; rate: number; total: number; profileId: number | null; measurementJson: string; status: string; delivered: boolean; deliveredAt: number | null; deliveryId: number | null; notes: string }
export interface StatusLog { id: number; orderId: number; itemId: number | null; fromStatus: string; toStatus: string; at: number; note: string }
export interface Txn { id: number; customerId: number; orderId: number | null; date: number; type: string; amount: number; signedEffect: number; cash: boolean; method: string; reference: string; notes: string; reversedById: number | null; reversesId: number | null; createdAt: number }
export interface Delivery { id: number; orderId: number; date: number; itemIdsJson: string; notes: string; paymentTxnId: number | null; forcedComplete: boolean }
export interface Expense { id: number; date: number; category: string; amount: number; method: string; notes: string; voided: boolean; sample: boolean }
export interface PrintLog { id: number; kind: string; refId: number; title: string; at: number; status: string; message: string }
export interface AuditLog { id: number; at: number; entity: string; entityId: number; action: string; detail: string }

export interface Tables {
  customers: Customer; members: FamilyMember; garments: Garment; fields: MeasurementField; profiles: MeasurementProfile
  customRates: CustomRate; rateHistory: RateHistory; services: ExtraService; orders: Order; items: OrderItem
  statusLogs: StatusLog; txns: Txn; deliveries: Delivery; expenses: Expense; printLogs: PrintLog; audits: AuditLog
}
export type Coll = keyof Tables
export const COLLS: Coll[] = ['customers', 'members', 'garments', 'fields', 'profiles', 'customRates', 'rateHistory', 'services',
  'orders', 'items', 'statusLogs', 'txns', 'deliveries', 'expenses', 'printLogs', 'audits']

/** Defaults so records written by older/newer app versions always have every field. */
export const DEFAULTS: { [K in Coll]: Omit<Tables[K], 'id'> } = {
  customers: { name: '', fatherName: '', mobile: '', whatsapp: '', address: '', city: '', area: '', category: '', regDate: 0, openingBalance: 0, notes: '', active: true, mergedIntoId: null, sample: false },
  members: { customerId: 0, name: '', relation: '', ageGroup: '', mobile: '', fittingPref: '', notes: '', isSelf: false, active: true },
  garments: { name: '', gender: 'MEN', sortOrder: 0, defaultRate: 0, receiptDesc: '', active: true },
  fields: { garmentId: 0, label: '', unit: '', required: false, sortOrder: 0, active: true },
  profiles: { customerId: 0, memberId: 0, garmentId: 0, name: '', date: 0, unit: 'انچ', valuesJson: '[]', fitting: '', instructions: '', notes: '', isDefault: false, archived: false, previousId: null, lastUsed: null },
  customRates: { garmentId: 0, customerId: null, memberId: null, rate: 0, active: true, note: '', updatedAt: 0 },
  rateHistory: { garmentId: 0, customerId: null, memberId: null, oldRate: 0, newRate: 0, at: 0, note: '' },
  services: { name: '', kind: 'OTHER', amount: 0, active: true },
  orders: { customerId: 0, memberId: 0, bookingDate: 0, deliveryDate: 0, urgent: false, instructions: '', internalNotes: '', status: 'NEW', isDraft: false, chargesJson: '[]', discountType: 'AMOUNT', discountInput: 0, itemsTotal: 0, chargesTotal: 0, discountAmount: 0, netAmount: 0, createdAt: 0, updatedAt: 0, rebookedFromId: null },
  items: { orderId: 0, garmentId: 0, garmentName: '', memberId: 0, memberName: '', qty: 1, rate: 0, total: 0, profileId: null, measurementJson: '', status: 'NEW', delivered: false, deliveredAt: null, deliveryId: null, notes: '' },
  statusLogs: { orderId: 0, itemId: null, fromStatus: '', toStatus: '', at: 0, note: '' },
  txns: { customerId: 0, orderId: null, date: 0, type: 'PAYMENT', amount: 0, signedEffect: 0, cash: false, method: '', reference: '', notes: '', reversedById: null, reversesId: null, createdAt: 0 },
  deliveries: { orderId: 0, date: 0, itemIdsJson: '[]', notes: '', paymentTxnId: null, forcedComplete: false },
  expenses: { date: 0, category: '', amount: 0, method: 'نقد', notes: '', voided: false, sample: false },
  printLogs: { kind: '', refId: 0, title: '', at: 0, status: '', message: '' },
  audits: { at: 0, entity: '', entityId: 0, action: '', detail: '' },
}

export const Gender = {
  all: ['MEN', 'WOMEN', 'KIDS', 'OTHER'],
  label: (g: string) => ({ MEN: 'مردانہ', WOMEN: 'زنانہ', KIDS: 'بچے' } as Record<string, string>)[g] ?? 'دیگر',
}

export const ServiceKind = {
  all: ['URGENT', 'EMBROIDERY', 'ALTERATION', 'OTHER'],
  label: (k: string) => ({ URGENT: 'ارجنٹ', EMBROIDERY: 'کڑھائی / ڈیزائن', ALTERATION: 'آلٹریشن / مرمت' } as Record<string, string>)[k] ?? 'دیگر سروس',
}

export const OrderStatus = {
  NEW: 'NEW', MEASURED: 'MEASURED', CUTTING: 'CUTTING', STITCHING: 'STITCHING', FINISHING: 'FINISHING', READY: 'READY', DELIVERED: 'DELIVERED', CANCELLED: 'CANCELLED',
  workflow: ['NEW', 'MEASURED', 'CUTTING', 'STITCHING', 'FINISHING', 'READY', 'DELIVERED'],
  all: ['NEW', 'MEASURED', 'CUTTING', 'STITCHING', 'FINISHING', 'READY', 'DELIVERED', 'CANCELLED'],
  open: ['NEW', 'MEASURED', 'CUTTING', 'STITCHING', 'FINISHING'],
  label: (s: string) => ({ NEW: 'نئی بکنگ', MEASURED: 'ناپ مکمل', CUTTING: 'کٹنگ جاری', STITCHING: 'سلائی جاری', FINISHING: 'فنشنگ', READY: 'تیار ہے', DELIVERED: 'ڈیلیور ہو گیا', CANCELLED: 'منسوخ' } as Record<string, string>)[s] ?? s,
}

export const TxnType = {
  PAYMENT: 'PAYMENT', REFUND: 'REFUND', ADJ_DEBIT: 'ADJ_DEBIT', ADJ_CREDIT: 'ADJ_CREDIT', REVERSAL: 'REVERSAL',
  label: (t: string) => ({ PAYMENT: 'وصولی', REFUND: 'واپسی (ریفنڈ)', ADJ_DEBIT: 'ایڈجسٹمنٹ (بقایا میں اضافہ)', ADJ_CREDIT: 'ایڈجسٹمنٹ (بقایا میں کمی)', REVERSAL: 'منسوخ شدہ اندراج (ریورسل)' } as Record<string, string>)[t] ?? t,
  effect: (type: string, amount: number) => (type === 'PAYMENT' || type === 'ADJ_CREDIT') ? -amount : (type === 'REFUND' || type === 'ADJ_DEBIT') ? amount : 0,
  isCash: (t: string) => t === 'PAYMENT' || t === 'REFUND',
}

export const PayMethod = { CASH: 'نقد', all: ['نقد', 'بینک ٹرانسفر', 'ڈیجیٹل (ایزی پیسہ / جاز کیش)', 'دیگر'] }
export const ExpenseCategories = ['دکان کا کرایہ', 'بجلی کا بل', 'ملازمین کی تنخواہ', 'دھاگہ اور دیگر سامان', 'مرمت', 'دیگر اخراجات']

export const ReceiptKind = {
  BOOKING: 'BOOKING', MEASUREMENT: 'MEASUREMENT', PAYMENT: 'PAYMENT', DELIVERY: 'DELIVERY', LEDGER: 'LEDGER', CUSTOMER: 'CUSTOMER', TEST: 'TEST',
  label: (k: string) => ({ BOOKING: 'بکنگ رسید', MEASUREMENT: 'ناپ کی پرچی', PAYMENT: 'وصولی رسید', DELIVERY: 'ڈیلیوری رسید', LEDGER: 'کھاتہ اسٹیٹمنٹ', CUSTOMER: 'کسٹمر معلومات', TEST: 'ٹیسٹ پرنٹ' } as Record<string, string>)[k] ?? k,
}

/** Shop-level settings shared with the phone (Firestore meta/settings). */
export interface ShopSettings {
  shopName: string; shopPhone: string; shopAddress: string; receiptHeading: string; footer: string; terms: string; deliveryTerms: string
  showLogo: boolean; showFatherName: boolean; showAddress: boolean; showMeasurementsOnBooking: boolean; showTerms: boolean; showSignature: boolean
  copyType: string; currencyLabel: string; dateFormat: string; urduDigits: boolean; defaultUnit: string; dueSoonDays: number
}
export const DEFAULT_SHOP: ShopSettings = {
  shopName: 'ٹیلر ماسٹر', shopPhone: '', shopAddress: '', receiptHeading: '', footer: 'شکریہ! دوبارہ تشریف لائیں',
  terms: 'رسید کے بغیر کپڑے نہیں دیے جائیں گے۔ ۳۰ دن بعد دکان ذمہ دار نہیں ہوگی۔', deliveryTerms: '',
  showLogo: true, showFatherName: false, showAddress: false, showMeasurementsOnBooking: false, showTerms: true, showSignature: true,
  copyType: 'کسٹمر کاپی', currencyLabel: 'روپے', dateFormat: 'dd/MM/yyyy', urduDigits: false, defaultUnit: 'انچ', dueSoonDays: 1,
}

/** This browser only. */
export interface LocalSettings { paperWidthMm: number; receiptFontSize: number; receiptFont: string; theme: string; printShopCopy: boolean }
export const DEFAULT_LOCAL: LocalSettings = { paperWidthMm: 80, receiptFontSize: 13, receiptFont: 'naskh', theme: 'system', printShopCopy: false }
