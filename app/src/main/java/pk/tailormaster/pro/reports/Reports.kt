package pk.tailormaster.pro.reports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.data.decodeValues
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.ui.Fmt

data class ReportTable(
    val title: String,
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val totals: List<String>? = null,
    /** Columns holding plain numbers for CSV (without currency label). */
    val csvRows: List<List<String>> = rows,
    val csvTotals: List<String>? = totals,
)

data class ReportFilter(
    val from: Long = Dates.startOfMonth(System.currentTimeMillis()),
    val to: Long = Dates.endOfDay(System.currentTimeMillis()),
    val customerId: Long? = null,
    val garmentId: Long? = null,
    val status: String? = null,
)

enum class ReportKind(val title: String, val usesDates: Boolean, val usesCustomer: Boolean, val usesGarment: Boolean, val usesStatus: Boolean) {
    DAILY_BOOKING("روزانہ بکنگ رپورٹ", true, true, true, true),
    MONTHLY_BOOKING("ماہانہ بکنگ رپورٹ", true, true, true, true),
    CUSTOMER_HISTORY("کسٹمر ہسٹری رپورٹ", true, true, false, false),
    MEMBER_MEASUREMENTS("فیملی ممبر ناپ رپورٹ", false, true, true, false),
    GARMENT_WISE("کپڑوں کے لحاظ سے آرڈر رپورٹ", true, true, true, true),
    PENDING("زیر التوا آرڈرز رپورٹ", false, true, true, true),
    OVERDUE("تاخیر والی ڈیلیوری رپورٹ", false, true, true, false),
    DAILY_COLLECTION("روزانہ وصولی رپورٹ", true, true, false, false),
    OUTSTANDING("کسٹمر بقایا رپورٹ", false, true, false, false),
    EXPENSES("اخراجات رپورٹ", true, false, false, false),
    INCOME_EXPENSE("آمدن اور اخراجات کا خلاصہ", true, false, false, false),
    RATE_HISTORY("ریٹ ہسٹری رپورٹ", true, true, true, false),
}

object Reports {

    private fun m(p: Long) = Fmt.moneyPlain(p)
    private fun n(p: Long) = pk.tailormaster.pro.domain.Calc.moneyInput(p)

    suspend fun build(repo: Repository, kind: ReportKind, f: ReportFilter): ReportTable = withContext(Dispatchers.IO) {
        val dao = repo.rawDao()
        val customers = dao.customers().associateBy { it.id }
        val members = dao.allMembers().associateBy { it.id }
        val garments = dao.garments().associateBy { it.id }
        val range = "${Fmt.date(f.from)} تا ${Fmt.date(f.to)}"
        val filterText = buildList {
            f.customerId?.let { add("کسٹمر: " + (customers[it]?.name ?: "-")) }
            f.garmentId?.let { add("کپڑا: " + (garments[it]?.name ?: "-")) }
            f.status?.let { add("اسٹیٹس: " + OrderStatus.label(it)) }
        }.joinToString(" • ")
        fun sub(withRange: Boolean) = listOf(if (withRange) range else "", filterText).filter { it.isNotBlank() }.joinToString(" • ")

        val allItems = dao.allItems()
        val itemsByOrder = allItems.groupBy { it.orderId }
        val rows = repo.orderRowsSync().filter { !it.order.isDraft }
        fun orderMatches(r: pk.tailormaster.pro.data.OrderRow): Boolean =
            (f.customerId == null || r.order.customerId == f.customerId) &&
                (f.status == null || r.order.status == f.status) &&
                (f.garmentId == null || (itemsByOrder[r.order.id] ?: emptyList()).any { it.garmentId == f.garmentId })

        when (kind) {
            ReportKind.DAILY_BOOKING, ReportKind.MONTHLY_BOOKING -> {
                val list = rows.filter { it.order.bookingDate in f.from..f.to && orderMatches(it) }.sortedBy { it.order.bookingDate }
                val net = list.filter { it.order.status != OrderStatus.CANCELLED }
                ReportTable(kind.title, sub(true),
                    listOf("آرڈر نمبر", "تاریخ", "کسٹمر", "ممبر", "کپڑے", "کل رقم", "وصول", "بقایا", "اسٹیٹس"),
                    list.map { r -> listOf(Fmt.orderNo(r.order.id), Fmt.date(r.order.bookingDate), r.customerName, r.memberName,
                        Fmt.num(r.itemCount), m(r.order.netAmount), m(r.paid), m(r.outstanding), OrderStatus.label(r.order.status)) },
                    listOf("کل: ${Fmt.num(list.size)}", "", "", "", Fmt.num(net.sumOf { it.itemCount }), m(net.sumOf { it.order.netAmount }),
                        m(list.sumOf { it.paid }), m(list.sumOf { it.outstanding }), ""),
                    csvRows = list.map { r -> listOf("B-${r.order.id}", Dates.format(r.order.bookingDate, "yyyy-MM-dd"), r.customerName, r.memberName,
                        r.itemCount.toString(), n(r.order.netAmount), n(r.paid), n(r.outstanding), OrderStatus.label(r.order.status)) },
                    csvTotals = listOf("TOTAL", "", "", "", net.sumOf { it.itemCount }.toString(), n(net.sumOf { it.order.netAmount }),
                        n(list.sumOf { it.paid }), n(list.sumOf { it.outstanding }), ""))
            }

            ReportKind.CUSTOMER_HISTORY -> {
                val cid = f.customerId ?: return@withContext ReportTable(kind.title, "براہ کرم کسٹمر منتخب کریں", listOf("پیغام"), listOf(listOf("کسٹمر منتخب نہیں کیا گیا")))
                val l = repo.ledgerSync(cid)
                val list = l.entries.filter { it.date in f.from..f.to || it.description == "ابتدائی بیلنس" }
                ReportTable(kind.title + " — " + l.customer.name, sub(true),
                    listOf("تاریخ", "تفصیل", "بنام (واجب)", "جمع (وصول)", "بیلنس"),
                    list.map { listOf(Fmt.date(it.date), it.description, m(it.debit), m(it.credit), m(it.balance)) },
                    listOf("", "موجودہ بیلنس", "", "", m(l.balance)),
                    csvRows = list.map { listOf(Dates.format(it.date, "yyyy-MM-dd"), it.description, n(it.debit), n(it.credit), n(it.balance)) },
                    csvTotals = listOf("", "BALANCE", "", "", n(l.balance)))
            }

            ReportKind.MEMBER_MEASUREMENTS -> {
                val list = dao.allProfiles().filter { !it.archived &&
                    (f.customerId == null || it.customerId == f.customerId) && (f.garmentId == null || it.garmentId == f.garmentId) }
                    .sortedWith(compareBy({ customers[it.customerId]?.name ?: "" }, { members[it.memberId]?.name ?: "" }))
                val r = list.map { p ->
                    listOf(customers[p.customerId]?.name ?: "-", members[p.memberId]?.name ?: "-", garments[p.garmentId]?.name ?: "-",
                        p.name + if (p.isDefault) " ★" else "", Fmt.date(p.date), p.unit,
                        decodeValues(p.valuesJson).filter { it.value.isNotBlank() }.joinToString("، ") { "${it.label}: ${Fmt.d(it.value)}" })
                }
                ReportTable(kind.title, sub(false), listOf("کسٹمر", "ممبر", "کپڑا", "پروفائل", "تاریخ", "اکائی", "ناپ"), r)
            }

            ReportKind.GARMENT_WISE -> {
                val orderIds = rows.filter { it.order.bookingDate in f.from..f.to && it.order.status != OrderStatus.CANCELLED &&
                    (f.customerId == null || it.order.customerId == f.customerId) && (f.status == null || it.order.status == f.status) }
                    .map { it.order.id }.toSet()
                val grouped = allItems.filter { it.orderId in orderIds && (f.garmentId == null || it.garmentId == f.garmentId) }
                    .groupBy { it.garmentName }
                    .map { (name, its) -> Triple(name, its.sumOf { it.qty }, its.sumOf { it.total }) }
                    .sortedByDescending { it.second }
                ReportTable(kind.title, sub(true), listOf("کپڑا", "تعداد", "رقم"),
                    grouped.map { listOf(it.first, Fmt.num(it.second), m(it.third)) },
                    listOf("کل", Fmt.num(grouped.sumOf { it.second }), m(grouped.sumOf { it.third })),
                    csvRows = grouped.map { listOf(it.first, it.second.toString(), n(it.third)) },
                    csvTotals = listOf("TOTAL", grouped.sumOf { it.second }.toString(), n(grouped.sumOf { it.third })))
            }

            ReportKind.PENDING, ReportKind.OVERDUE -> {
                val today = Dates.today()
                val list = rows.filter { r ->
                    r.order.status != OrderStatus.DELIVERED && r.order.status != OrderStatus.CANCELLED && orderMatches(r) &&
                        (kind == ReportKind.PENDING || r.order.deliveryDate < today)
                }.sortedBy { it.order.deliveryDate }
                ReportTable(kind.title, sub(false),
                    listOf("آرڈر نمبر", "کسٹمر", "موبائل", "ممبر", "ڈیلیوری تاریخ", "تاخیر (دن)", "باقی کپڑے", "بقایا", "اسٹیٹس"),
                    list.map { r ->
                        val late = ((today - Dates.startOfDay(r.order.deliveryDate)) / Dates.DAY).coerceAtLeast(0)
                        listOf(Fmt.orderNo(r.order.id) + if (r.order.urgent) " ⚡" else "", r.customerName, Fmt.d(r.customerMobile), r.memberName,
                            Fmt.date(r.order.deliveryDate), Fmt.num(late), Fmt.num(r.pendingItems), m(r.outstanding), OrderStatus.label(r.order.status))
                    },
                    listOf("کل: ${Fmt.num(list.size)}", "", "", "", "", "", Fmt.num(list.sumOf { it.pendingItems }), m(list.sumOf { it.outstanding }), ""))
            }

            ReportKind.DAILY_COLLECTION -> {
                val list = dao.txns().filter { it.cash && it.date in f.from..f.to && (f.customerId == null || it.customerId == f.customerId) }
                    .sortedBy { it.date }
                val byMethod = list.groupBy { it.method }.map { (k, v) -> "$k: ${m(-v.sumOf { it.signedEffect })}" }.joinToString(" • ")
                ReportTable(kind.title, listOf(sub(true), byMethod).filter { it.isNotBlank() }.joinToString("\n"),
                    listOf("تاریخ", "ٹرانزیکشن", "کسٹمر", "آرڈر", "قسم", "طریقہ", "رقم"),
                    list.map { t -> listOf(Fmt.dateTime(t.date), Fmt.txnNo(t.id), customers[t.customerId]?.name ?: "-",
                        t.orderId?.let { Fmt.orderNo(it) } ?: "-", TxnType.label(t.type), t.method, m(-t.signedEffect)) },
                    listOf("کل وصولی", "", "", "", "", "", m(-list.sumOf { it.signedEffect })),
                    csvRows = list.map { t -> listOf(Dates.format(t.date, "yyyy-MM-dd HH:mm"), "T-${t.id}", customers[t.customerId]?.name ?: "-",
                        t.orderId?.let { "B-$it" } ?: "", TxnType.label(t.type), t.method, n(-t.signedEffect)) },
                    csvTotals = listOf("TOTAL", "", "", "", "", "", n(-list.sumOf { it.signedEffect })))
            }

            ReportKind.OUTSTANDING -> {
                val bal = repo.balancesSync()
                val list = customers.values.filter { it.mergedIntoId == null && (bal[it.id] ?: 0) != 0L &&
                    (f.customerId == null || it.id == f.customerId) }.sortedByDescending { bal[it.id] ?: 0 }
                ReportTable(kind.title, "مثبت = کسٹمر کے ذمے، منفی = کسٹمر کی پیشگی رقم",
                    listOf("کوڈ", "کسٹمر", "موبائل", "علاقہ", "بقایا"),
                    list.map { c -> listOf(Fmt.customerCode(c.id), c.name, Fmt.d(c.mobile), listOf(c.area, c.city).filter { it.isNotBlank() }.joinToString("، "), m(bal[c.id] ?: 0)) },
                    listOf("کل بقایا", "", "", "", m(list.sumOf { maxOf(0L, bal[it.id] ?: 0) })),
                    csvRows = list.map { c -> listOf("C-${c.id}", c.name, c.mobile, c.area, n(bal[c.id] ?: 0)) },
                    csvTotals = listOf("TOTAL", "", "", "", n(list.sumOf { maxOf(0L, bal[it.id] ?: 0) })))
            }

            ReportKind.EXPENSES -> {
                val list = dao.expenses().filter { !it.voided && it.date in f.from..f.to }.sortedBy { it.date }
                val byCat = list.groupBy { it.category }.map { (k, v) -> "$k: ${m(v.sumOf { it.amount })}" }.joinToString(" • ")
                ReportTable(kind.title, listOf(sub(true), byCat).filter { it.isNotBlank() }.joinToString("\n"),
                    listOf("تاریخ", "قسم", "طریقہ", "تفصیل", "رقم"),
                    list.map { listOf(Fmt.date(it.date), it.category, it.method, it.notes, m(it.amount)) },
                    listOf("کل اخراجات", "", "", "", m(list.sumOf { it.amount })),
                    csvRows = list.map { listOf(Dates.format(it.date, "yyyy-MM-dd"), it.category, it.method, it.notes, n(it.amount)) },
                    csvTotals = listOf("TOTAL", "", "", "", n(list.sumOf { it.amount })))
            }

            ReportKind.INCOME_EXPENSE -> {
                val booked = rows.filter { it.order.bookingDate in f.from..f.to && it.order.status != OrderStatus.CANCELLED }
                val revenue = booked.sumOf { it.order.netAmount }
                val cash = -dao.txns().filter { it.cash && it.date in f.from..f.to }.sumOf { it.signedEffect }
                val exp = dao.expenses().filter { !it.voided && it.date in f.from..f.to }.sumOf { it.amount }
                val outstanding = repo.balancesSync().values.filter { it > 0 }.sum()
                val lines = listOf(
                    "آرڈرز کی آمدن (بک شدہ کام)" to revenue,
                    "نقد / رقم وصول ہوئی" to cash,
                    "کاروباری اخراجات" to exp,
                    "خالص نقد (وصولی − اخراجات)" to (cash - exp),
                    "کل بقایا (آج تک، تمام کسٹمرز)" to outstanding,
                )
                ReportTable(kind.title, sub(true), listOf("مد", "رقم"), lines.map { listOf(it.first, m(it.second)) },
                    csvRows = lines.map { listOf(it.first, n(it.second)) })
            }

            ReportKind.RATE_HISTORY -> {
                val list = dao.rateHistory().filter { it.at in f.from..f.to && (f.garmentId == null || it.garmentId == f.garmentId) &&
                    (f.customerId == null || it.customerId == f.customerId) }
                ReportTable(kind.title, sub(true), listOf("تاریخ", "کپڑا", "کسٹمر / ممبر", "پرانا ریٹ", "نیا ریٹ", "نوٹ"),
                    list.map { h -> listOf(Fmt.dateTime(h.at), garments[h.garmentId]?.name ?: "-",
                        listOfNotNull(h.customerId?.let { customers[it]?.name }, h.memberId?.let { members[it]?.name }).joinToString(" / ").ifBlank { "ڈیفالٹ" },
                        m(h.oldRate), m(h.newRate), h.note) },
                    csvRows = list.map { h -> listOf(Dates.format(h.at, "yyyy-MM-dd HH:mm"), garments[h.garmentId]?.name ?: "-",
                        listOfNotNull(h.customerId?.let { customers[it]?.name }, h.memberId?.let { members[it]?.name }).joinToString(" / ").ifBlank { "ڈیفالٹ" },
                        n(h.oldRate), n(h.newRate), h.note) })
            }
        }
    }
}
