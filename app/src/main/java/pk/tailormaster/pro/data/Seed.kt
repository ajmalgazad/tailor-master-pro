package pk.tailormaster.pro.data

/** Default clothing types, measurement fields and services inserted on first launch. All editable later. */
object Seed {

    private val kameez = listOf("لمبائی", "چھاتی", "کمر", "ہِپ", "کندھا", "آستین", "بازو", "کف", "گلا", "دامن", "چاک")
    private val shalwar = listOf("شلوار لمبائی", "شلوار کمر", "شلوار ہِپ", "ران", "آسن", "پائنچہ", "موہری", "بیلٹ")
    private val pant = listOf("لمبائی", "کمر", "ہِپ", "ران", "گھٹنا", "پائنچہ", "فرنٹ رائز", "بیک رائز")
    private val shirt = listOf("لمبائی", "چھاتی", "کمر", "کندھا", "کالر", "آستین", "کف")
    private val coat = listOf("لمبائی", "چھاتی", "کمر", "ہِپ", "کندھا", "آستین", "فرنٹ", "بیک", "گلا")
    private val trouser = listOf("لمبائی", "کمر", "ہِپ", "ران", "پائنچہ")
    private val frock = listOf("لمبائی", "چھاتی", "کمر", "ہِپ", "کندھا", "آستین", "بازو", "گلا", "گھیرا")
    private val blouse = listOf("لمبائی", "چھاتی", "انڈر بسٹ", "کمر", "کندھا", "آستین", "بازو", "گلا آگے", "گلا پیچھے")
    private val lehnga = listOf("لمبائی", "کمر", "ہِپ", "گھیرا")
    private val kids = listOf("لمبائی", "چھاتی", "کمر", "کندھا", "آستین", "شلوار لمبائی", "پائنچہ")

    private data class G(val name: String, val gender: String, val rate: Long, val fields: List<String>, val required: List<String> = emptyList())

    private val men = listOf(
        G("شلوار قمیض", Gender.MEN, 1500, kameez + shalwar, listOf("لمبائی", "چھاتی", "شلوار لمبائی")),
        G("کُرتا شلوار", Gender.MEN, 1600, kameez + shalwar),
        G("کُرتا پاجامہ", Gender.MEN, 1600, kameez + listOf("پاجامہ لمبائی", "کمر", "ران", "پائنچہ")),
        G("پینٹ شرٹ", Gender.MEN, 2200, shirt.map { "شرٹ $it" } + pant.map { "پینٹ $it" }),
        G("پینٹ", Gender.MEN, 1200, pant),
        G("شرٹ", Gender.MEN, 1000, shirt),
        G("ٹراؤزر", Gender.MEN, 800, trouser),
        G("واسکٹ", Gender.MEN, 2500, coat - "آستین"),
        G("کوٹ", Gender.MEN, 6000, coat),
        G("بلیزر", Gender.MEN, 6500, coat),
        G("شیروانی", Gender.MEN, 9000, coat + listOf("کالر")),
        G("بچوں کا سوٹ", Gender.KIDS, 900, kids),
        G("دیگر کپڑے (مردانہ)", Gender.MEN, 0, listOf("لمبائی", "چھاتی", "کمر")),
    )

    private val women = listOf(
        G("قمیض شلوار (زنانہ)", Gender.WOMEN, 1800, kameez + shalwar),
        G("قمیض ٹراؤزر", Gender.WOMEN, 1800, kameez + trouser.map { "ٹراؤزر $it" }),
        G("لانگ شرٹ", Gender.WOMEN, 1500, kameez),
        G("شارٹ شرٹ", Gender.WOMEN, 1200, kameez),
        G("فراک", Gender.WOMEN, 3000, frock),
        G("میکسی", Gender.WOMEN, 3500, frock),
        G("بلاؤز", Gender.WOMEN, 1200, blouse),
        G("لہنگا", Gender.WOMEN, 5000, lehnga),
        G("غرارہ", Gender.WOMEN, 3000, lehnga),
        G("شرارہ", Gender.WOMEN, 3000, lehnga),
        G("عبایا", Gender.WOMEN, 2500, listOf("لمبائی", "چھاتی", "کندھا", "آستین", "بازو", "گلا")),
        G("بچوں کے کپڑے (زنانہ)", Gender.KIDS, 900, kids),
        G("دیگر کپڑے (زنانہ)", Gender.WOMEN, 0, listOf("لمبائی", "چھاتی", "کمر")),
    )

    /**
     * Ids are fixed (1, 2, 3 ...) so that every device seeds exactly the same records.
     * The web app's seed uses the same list in the same order.
     */
    fun insertDefaults(dao: TailorDao) {
        var sort = 0
        var fieldId = 0L
        (men + women).forEachIndexed { gi, g ->
            val id = (gi + 1).toLong()
            dao.insertGarment(Garment(id = id, name = g.name, gender = g.gender, sortOrder = sort++, defaultRate = g.rate * 100,
                receiptDesc = g.name))
            dao.insertRateHistory(RateHistory(id = id, garmentId = id, oldRate = 0, newRate = g.rate * 100, note = "ابتدائی ریٹ", at = 0))
            g.fields.distinct().forEachIndexed { i, label ->
                dao.insertField(MeasurementField(id = ++fieldId, garmentId = id, label = label, sortOrder = i, required = label in g.required))
            }
        }
        listOf(
            ExtraService(id = 1, name = "ارجنٹ سلائی", kind = ServiceKind.URGENT, amount = 50000),
            ExtraService(id = 2, name = "کڑھائی", kind = ServiceKind.EMBROIDERY, amount = 100000),
            ExtraService(id = 3, name = "ڈیزائن / بٹن ورک", kind = ServiceKind.EMBROIDERY, amount = 30000),
            ExtraService(id = 4, name = "آلٹریشن", kind = ServiceKind.ALTERATION, amount = 30000),
            ExtraService(id = 5, name = "مرمت", kind = ServiceKind.ALTERATION, amount = 20000),
            ExtraService(id = 6, name = "کالر / کف اسپیشل", kind = ServiceKind.OTHER, amount = 20000),
        ).forEach { dao.insertService(it) }
    }
}
