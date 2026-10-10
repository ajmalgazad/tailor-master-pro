import { Codes, Dates, Digits, Money } from './calc'
import { store } from './store'

/** Display formatting that follows the shop settings (digits, date format, currency) — same as Fmt.kt. */
export const Fmt = {
  d: (s: string) => (store.shop.urduDigits ? Digits.toUrdu(s) : s),
  money: (p: number) => Fmt.d(Money.plain(p)) + ' ' + store.shop.currencyLabel,
  moneyPlain: (p: number) => Fmt.d(Money.plain(p)),
  date: (ms: number | null | undefined) => (!ms ? '-' : Fmt.d(Dates.format(ms, store.shop.dateFormat))),
  dateTime: (ms: number | null | undefined) => (!ms ? '-' : Fmt.d(Dates.format(ms, store.shop.dateFormat + ' hh:mm a'))),
  num: (n: number) => Fmt.d(String(n)),
  customerCode: (id: number) => Fmt.d(Codes.code('C', id, 4)),
  memberCode: (id: number) => Fmt.d(Codes.code('M', id, 4)),
  orderNo: (id: number) => Fmt.d(Codes.code('B', id, 5)),
  txnNo: (id: number) => Fmt.d(Codes.code('T', id, 5)),
  profileCode: (id: number) => Fmt.d(Codes.code('P', id, 4)),
  deliveryNo: (id: number) => Fmt.d(Codes.code('D', id, 5)),
}
