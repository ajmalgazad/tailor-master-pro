package pk.tailormaster.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.domain.Digits
import pk.tailormaster.pro.domain.Money

class CalcTest {

    private fun rs(r: Long) = r * 100

    @Test fun lineTotal_isQuantityTimesRate() {
        assertEquals(rs(3000), Calc.lineTotal(2, rs(1500)))
    }

    @Test fun example_order_2ShalwarKameez_1Waistcoat_1Trouser() {
        val lines = listOf(Calc.Line(2, rs(1500)), Calc.Line(1, rs(2500)), Calc.Line(1, rs(800)))
        val t = Calc.totals(lines, listOf(rs(500)), false, 300.0)
        assertEquals(rs(6300), t.itemsTotal)
        assertEquals(rs(500), t.chargesTotal)
        assertEquals(rs(300), t.discount)
        assertEquals(rs(6500), t.net)
        // advance 2000 -> outstanding 4500
        assertEquals(rs(4500), Calc.outstanding(t.net, rs(2000)))
    }

    @Test fun percentDiscount_roundsHalfUp() {
        val t = Calc.totals(listOf(Calc.Line(1, rs(1999))), emptyList(), true, 10.0)
        assertEquals(19990L, t.discount) // 10% of 1999.00 = 199.90
        assertEquals(rs(1999) - 19990L, t.net)
    }

    @Test fun discountNeverExceedsSubtotal() {
        val t = Calc.totals(listOf(Calc.Line(1, rs(100))), emptyList(), false, 500.0)
        assertEquals(rs(100), t.discount)
        assertEquals(0L, t.net)
    }

    @Test fun validation_rejectsNegativesAndBadDiscount() {
        assertNotNull(Calc.validate(emptyList(), emptyList(), false, 0.0))
        assertNotNull(Calc.validate(listOf(Calc.Line(0, rs(10))), emptyList(), false, 0.0))
        assertNotNull(Calc.validate(listOf(Calc.Line(1, -5)), emptyList(), false, 0.0))
        assertNotNull(Calc.validate(listOf(Calc.Line(1, rs(10))), listOf(-1), false, 0.0))
        assertNotNull(Calc.validate(listOf(Calc.Line(1, rs(10))), emptyList(), true, 120.0))
        assertNotNull(Calc.validate(listOf(Calc.Line(1, rs(10))), emptyList(), false, 11.0))
        assertNull(Calc.validate(listOf(Calc.Line(1, rs(10))), emptyList(), false, 10.0))
    }

    @Test fun ledger_paymentsReduceBalance_refundsIncrease_reversalUndoes() {
        val pay = TxnType.effect(TxnType.PAYMENT, rs(2000))
        val refund = TxnType.effect(TxnType.REFUND, rs(500))
        val reversalOfPay = -pay
        assertEquals(rs(1000) + rs(6500) - rs(2000), Calc.customerBalance(rs(1000), listOf(rs(6500)), listOf(pay)))
        assertEquals(rs(1000) + rs(6500) - rs(2000) + rs(500), Calc.customerBalance(rs(1000), listOf(rs(6500)), listOf(pay, refund)))
        assertEquals(rs(7500), Calc.customerBalance(rs(1000), listOf(rs(6500)), listOf(pay, reversalOfPay)))
    }

    @Test fun parseMoney_handlesUrduDigitsCommasDecimals() {
        assertEquals(rs(1500), Calc.parseMoney("1500"))
        assertEquals(rs(1500), Calc.parseMoney("1,500"))
        assertEquals(150050L, Calc.parseMoney("1500.50"))
        assertEquals(rs(250), Calc.parseMoney("۲۵۰"))
        assertNull(Calc.parseMoney("-5"))
        assertNull(Calc.parseMoney("abc"))
    }

    @Test fun formatting() {
        assertEquals("12,345", Money.plain(rs(12345)))
        assertEquals("12,345.50", Money.plain(1234550))
        assertEquals("۱۲۳", Digits.toUrdu("123"))
        assertEquals("1500", Calc.moneyInput(rs(1500)))
        assertEquals("15.5", Calc.moneyInput(1550))
    }
}
