package pk.tailormaster.pro

import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import pk.tailormaster.pro.i18n.I18n
import java.io.File

class I18nTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            I18n.loadJson(File("../i18n/en.json").readText(Charsets.UTF_8))
        }
    }

    private fun en(s: String): String { I18n.setLanguage(I18n.ENGLISH); return I18n.tr(s) }

    @Test fun urduModeLeavesTextAlone() {
        I18n.setLanguage(I18n.URDU)
        assertEquals("نئی بکنگ", I18n.tr("نئی بکنگ"))
    }

    @Test fun exactAndTemplates() {
        assertEquals("New booking", en("نئی بکنگ"))
        assertEquals("5 days", en("5 دن"))
        assertEquals("Items: 3 (remaining 1) • Total 4,500 Rs • Balance 1,000 Rs",
            en("کپڑے: 3 (باقی 1) • کل 4,500 روپے • بقایا 1,000 روپے"))
        assertEquals("🔔 Delivery reminders (3)", en("🔔 ڈیلیوری یاد دہانی (3)"))
    }

    @Test fun piecesAndPhrases() {
        assertEquals("Error: An internal error has occurred.", en("خرابی: An internal error has occurred."))
        assertEquals("Booking saved — B-00012", en("بکنگ محفوظ ہو گئی — B-00012"))
        assertEquals("Could not log in: timeout", en("لاگ اِن نہیں ہو سکا: timeout"))
        assertEquals("1,200 Rs", en("1,200 روپے"))
        assertEquals("Kameez shalwar (women's)", en("قمیض شلوار (زنانہ)"))
        assertEquals("Ali (Muhammad Aslam (sample))", en("Ali (محمد اسلم (نمونہ))"))
    }

    @Test fun namesStayAsWritten() {
        assertEquals("Aleem", en("Aleem"))
        assertEquals("  Garments amount:", en("  کپڑوں کی رقم:"))
    }
}
