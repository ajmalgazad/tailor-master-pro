package pk.tailormaster.pro.print

import pk.tailormaster.pro.data.AppSettings
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.data.decodeCharges
import pk.tailormaster.pro.data.decodeIds
import pk.tailormaster.pro.data.decodeSnapshot
import pk.tailormaster.pro.data.decodeValues
import pk.tailormaster.pro.ui.Fmt
import pk.tailormaster.pro.print.RLine.Align

/**
 * Builds receipts from saved data only. Building or printing a receipt NEVER creates or changes
 * any order or payment, so reprinting is always safe.
 */
object ReceiptBuilder {

    private fun header(s: AppSettings, title: String, copyLabel: String?): List<RLine> = buildList {
        add(RLine.Logo)
        add(RLine.Text(s.shopName, 1.45f, true, Align.CENTER))
        if (s.receiptHeading.isNotBlank()) add(RLine.Text(s.receiptHeading, 0.9f, false, Align.CENTER))
        if (s.shopAddress.isNotBlank()) add(RLine.Text(s.shopAddress, 0.85f, false, Align.CENTER))
        if (s.shopPhone.isNotBlank()) add(RLine.Text("فون: " + Fmt.d(s.shopPhone), 0.9f, false, Align.CENTER))
        add(RLine.Space(4))
        add(RLine.Band(title))
        if (!copyLabel.isNullOrBlank()) add(RLine.Text(copyLabel, 0.85f, false, Align.CENTER))
        add(RLine.Space(4))
    }

    private fun footer(s: AppSettings, includeTerms: Boolean, signature: Boolean): List<RLine> = buildList {
        if (includeTerms && s.showTerms && s.terms.isNotBlank()) {
            add(RLine.Divider())
            add(RLine.Text("شرائط و ضوابط:", 0.85f, true))
            add(RLine.Text(s.terms, 0.8f))
        }
        if (includeTerms && s.deliveryTerms.isNotBlank()) add(RLine.Text(s.deliveryTerms, 0.8f))
        if (signature && s.showSignature) {
            add(RLine.Space(24))
            add(RLine.Text("دستخط دکاندار: ____________________", 0.85f))
            add(RLine.Space(20))
            add(RLine.Text("دستخط کسٹمر: _____________________", 0.85f))
        }
        if (s.footer.isNotBlank()) {
            add(RLine.Divider())
            add(RLine.Text(s.footer, 0.9f, false, Align.CENTER))
        }
        add(RLine.Text("پرنٹ: " + Fmt.dateTime(System.currentTimeMillis()), 0.7f, false, Align.CENTER))
    }

    private fun m(p: Long) = Fmt.money(p)

    suspend fun build(repo: Repository, kind: String, refId: Long, copyLabel: String? = null): ReceiptDoc {
        val s = repo.settings.value
        val copy = copyLabel ?: s.copyType
        return when (kind) {
            ReceiptKind.BOOKING -> booking(repo, s, refId, copy)
            ReceiptKind.MEASUREMENT -> measurementSlip(repo, s, refId)
            ReceiptKind.PAYMENT -> payment(repo, s, refId, copy)
            ReceiptKind.DELIVERY -> delivery(repo, s, refId, copy)
            ReceiptKind.LEDGER -> ledger(repo, s, refId)
            ReceiptKind.CUSTOMER -> customerInfo(repo, s, refId)
            ReceiptKind.TEST -> test(s)
            else -> throw UserError("نامعلوم رسید")
        }
    }

    /** Measurement slip for a single profile (refId negative = profile id) is built by [profileSlip]. */
    private suspend fun booking(repo: Repository, s: AppSettings, orderId: Long, copy: String): ReceiptDoc {
        val f = repo.orderFull(orderId)
        val o = f.order
        val lines = mutableListOf<RLine>()
        lines += header(s, if (o.isDraft) "بکنگ رسید (ڈرافٹ)" else "بکنگ رسید", copy)
        lines += RLine.Pair("آرڈر نمبر:", Fmt.orderNo(o.id), true)
        lines += RLine.Pair("بکنگ تاریخ:", Fmt.date(o.bookingDate))
        lines += RLine.Pair("ڈیلیوری تاریخ:", Fmt.date(o.deliveryDate), true)
        if (o.urgent) lines += RLine.Text("★ ارجنٹ آرڈر ★", 1f, true, Align.CENTER)
        lines += RLine.Pair("کسٹمر:", f.customer.name + " (" + Fmt.customerCode(f.customer.id) + ")")
        if (s.showFatherName && f.customer.fatherName.isNotBlank()) lines += RLine.Pair("ولدیت:", f.customer.fatherName)
        if (f.customer.mobile.isNotBlank()) lines += RLine.Pair("موبائل:", Fmt.d(f.customer.mobile))
        if (s.showAddress && f.customer.address.isNotBlank()) lines += RLine.Text("پتہ: " + f.customer.address, 0.85f)
        f.member?.let { if (!it.isSelf) lines += RLine.Pair("فیملی ممبر:", it.name + " (" + it.relation + ")") }
        if (o.status == OrderStatus.CANCELLED) lines += RLine.Band("یہ آرڈر منسوخ ہو چکا ہے")
        lines += RLine.Divider()
        lines += RLine.Pair("کپڑا", "رقم", true, 0.9f)
        for (it in f.items) {
            val who = if (it.memberId != o.memberId || f.items.map { x -> x.memberId }.distinct().size > 1) " — " + it.memberName else ""
            lines += RLine.Text(it.garmentName + who, 1f, true)
            lines += RLine.Pair(Fmt.num(it.qty) + " × " + Fmt.moneyPlain(it.rate), Fmt.moneyPlain(it.total))
            if (s.showMeasurementsOnBooking) {
                decodeSnapshot(it.measurementJson)?.let { snap ->
                    val v = snap.values.filter { x -> x.value.isNotBlank() }.joinToString("  ") { x -> x.label + ": " + Fmt.d(x.value) }
                    if (v.isNotBlank()) lines += RLine.Text(v, 0.8f)
                }
            }
        }
        lines += RLine.Divider()
        lines += RLine.Pair("کپڑوں کی رقم:", m(o.itemsTotal))
        decodeCharges(o.chargesJson).forEach { lines += RLine.Pair(it.name + ":", m(it.amount)) }
        if (o.discountAmount > 0) {
            val pctNum = if (o.discountInput % 1.0 == 0.0) o.discountInput.toLong().toString() else o.discountInput.toString()
            val pct = if (o.discountType == "PERCENT") " (" + Fmt.d(pctNum) + "%)" else ""
            lines += RLine.Pair("رعایت$pct:", m(o.discountAmount))
        }
        lines += RLine.Pair("کل رقم:", m(o.netAmount), true, 1.15f)
        lines += RLine.Pair("ایڈوانس / وصول شدہ:", m(f.paid))
        lines += RLine.Pair("بقایا:", m(f.outstanding), true, 1.15f)
        if (o.instructions.isNotBlank()) {
            lines += RLine.Divider()
            lines += RLine.Text("خصوصی ہدایات: " + o.instructions, 0.9f)
        }
        lines += footer(s, true, true)
        return ReceiptDoc("بکنگ رسید " + Fmt.orderNo(o.id), lines)
    }

    /** Measurement slip of an order: only the garments in that order, with the member and frozen measurements. */
    private suspend fun measurementSlip(repo: Repository, s: AppSettings, orderId: Long): ReceiptDoc {
        val f = repo.orderFull(orderId)
        val lines = mutableListOf<RLine>()
        lines += RLine.Text(s.shopName, 1.2f, true, Align.CENTER)
        lines += RLine.Band("ناپ کی پرچی")
        lines += RLine.Pair("آرڈر نمبر:", Fmt.orderNo(f.order.id), true)
        lines += RLine.Pair("کسٹمر:", f.customer.name)
        lines += RLine.Pair("ڈیلیوری:", Fmt.date(f.order.deliveryDate), true)
        if (f.order.urgent) lines += RLine.Text("★ ارجنٹ ★", 1f, true, Align.CENTER)
        for (it in f.items) {
            lines += RLine.Divider(false)
            lines += RLine.Text(it.garmentName + " × " + Fmt.num(it.qty), 1.1f, true)
            lines += RLine.Pair("فرد:", it.memberName, true)
            val snap = decodeSnapshot(it.measurementJson)
            if (snap == null || snap.values.isEmpty()) {
                lines += RLine.Text("ناپ منسلک نہیں", 0.9f)
            } else {
                lines += RLine.Text(snap.profileName + " • " + snap.unit + " • " + Fmt.date(snap.date), 0.8f)
                snap.values.filter { v -> v.value.isNotBlank() }.forEach { v -> lines += RLine.Pair(v.label, Fmt.d(v.value), false, 1.05f) }
                if (snap.fitting.isNotBlank()) lines += RLine.Text("فٹنگ: " + snap.fitting, 0.9f)
                if (snap.instructions.isNotBlank()) lines += RLine.Text("ہدایات: " + snap.instructions, 0.9f)
            }
            if (it.notes.isNotBlank()) lines += RLine.Text("نوٹ: " + it.notes, 0.9f)
        }
        if (f.order.instructions.isNotBlank()) {
            lines += RLine.Divider()
            lines += RLine.Text("آرڈر ہدایات: " + f.order.instructions, 0.9f)
        }
        lines += RLine.Text("پرنٹ: " + Fmt.dateTime(System.currentTimeMillis()), 0.7f, false, Align.CENTER)
        return ReceiptDoc("ناپ کی پرچی " + Fmt.orderNo(f.order.id), lines)
    }

    /** Slip for one measurement profile (from the measurement screen). */
    suspend fun profileSlip(repo: Repository, profileId: Long): ReceiptDoc {
        val s = repo.settings.value
        val p = repo.profile(profileId) ?: throw UserError("پروفائل نہیں ملا")
        val c = repo.customer(p.customerId)
        val mem = repo.member(p.memberId)
        val g = repo.garment(p.garmentId)
        val lines = mutableListOf<RLine>()
        lines += RLine.Text(s.shopName, 1.2f, true, Align.CENTER)
        lines += RLine.Band("ناپ کی پرچی")
        lines += RLine.Pair("کسٹمر:", (c?.name ?: "-") + " (" + Fmt.customerCode(p.customerId) + ")")
        lines += RLine.Pair("فرد:", (mem?.name ?: "-") + " — " + (mem?.relation ?: ""), true)
        lines += RLine.Pair("کپڑا:", g?.name ?: "-", true)
        lines += RLine.Pair("پروفائل:", p.name)
        lines += RLine.Pair("تاریخ / اکائی:", Fmt.date(p.date) + " • " + p.unit)
        lines += RLine.Divider()
        decodeValues(p.valuesJson).filter { it.value.isNotBlank() }.forEach { lines += RLine.Pair(it.label, Fmt.d(it.value), false, 1.05f) }
        if (p.fitting.isNotBlank()) lines += RLine.Text("فٹنگ: " + p.fitting, 0.9f)
        if (p.instructions.isNotBlank()) lines += RLine.Text("ہدایات: " + p.instructions, 0.9f)
        if (p.notes.isNotBlank()) lines += RLine.Text("نوٹ: " + p.notes, 0.9f)
        lines += RLine.Text("پرنٹ: " + Fmt.dateTime(System.currentTimeMillis()), 0.7f, false, Align.CENTER)
        return ReceiptDoc("ناپ " + p.name, lines)
    }

    private suspend fun payment(repo: Repository, s: AppSettings, txnId: Long, copy: String): ReceiptDoc {
        val t = repo.txn(txnId) ?: throw UserError("اندراج نہیں ملا")
        val c = repo.customer(t.customerId) ?: throw UserError("کسٹمر نہیں ملا")
        val balanceAfter = repo.balanceAfterTxn(txnId)
        val title = when (t.type) {
            TxnType.PAYMENT -> "وصولی رسید"
            TxnType.REFUND -> "واپسی (ریفنڈ) رسید"
            TxnType.REVERSAL -> "منسوخی (ریورسل) رسید"
            else -> "ایڈجسٹمنٹ رسید"
        }
        val lines = mutableListOf<RLine>()
        lines += header(s, title, copy)
        lines += RLine.Pair("ٹرانزیکشن نمبر:", Fmt.txnNo(t.id), true)
        lines += RLine.Pair("تاریخ:", Fmt.dateTime(t.date))
        lines += RLine.Pair("کسٹمر:", c.name + " (" + Fmt.customerCode(c.id) + ")")
        if (c.mobile.isNotBlank()) lines += RLine.Pair("موبائل:", Fmt.d(c.mobile))
        t.orderId?.let { lines += RLine.Pair("آرڈر نمبر:", Fmt.orderNo(it)) }
        if (t.cash) lines += RLine.Pair("طریقہ ادائیگی:", t.method)
        if (t.reference.isNotBlank()) lines += RLine.Pair("حوالہ:", Fmt.d(t.reference))
        lines += RLine.Divider()
        val label = when (t.type) {
            TxnType.PAYMENT -> "وصول شدہ رقم:"
            TxnType.REFUND -> "واپس کی گئی رقم:"
            else -> "رقم:"
        }
        lines += RLine.Pair(label, m(t.amount), true, 1.3f)
        if (t.reversedById != null) lines += RLine.Band("یہ اندراج منسوخ (ریورس) ہو چکا ہے")
        if (t.notes.isNotBlank()) lines += RLine.Text("تفصیل: " + t.notes, 0.85f)
        lines += RLine.Divider()
        t.orderId?.let { oid ->
            val f = repo.orderFull(oid)
            lines += RLine.Pair("آرڈر کی کل رقم:", m(if (f.order.status == OrderStatus.CANCELLED) 0 else f.order.netAmount))
            lines += RLine.Pair("آرڈر میں کل وصولی:", m(f.paid))
            lines += RLine.Pair("آرڈر کا بقایا:", m(f.outstanding), true)
        }
        lines += RLine.Pair("اس رسید کے بعد کل بقایا:", m(balanceAfter), true, 1.1f)
        lines += footer(s, false, true)
        return ReceiptDoc(title + " " + Fmt.txnNo(t.id), lines)
    }

    private suspend fun delivery(repo: Repository, s: AppSettings, deliveryId: Long, copy: String): ReceiptDoc {
        val d = repo.delivery(deliveryId) ?: throw UserError("ڈیلیوری نہیں ملی")
        val f = repo.orderFull(d.orderId)
        val ids = decodeIds(d.itemIdsJson).toSet()
        val now = f.items.filter { it.id in ids }
        val pending = f.items.filter { !it.delivered }
        val lines = mutableListOf<RLine>()
        lines += header(s, "ڈیلیوری رسید", copy)
        lines += RLine.Pair("ڈیلیوری نمبر:", Fmt.deliveryNo(d.id), true)
        lines += RLine.Pair("آرڈر نمبر:", Fmt.orderNo(f.order.id), true)
        lines += RLine.Pair("ڈیلیوری تاریخ:", Fmt.dateTime(d.date))
        lines += RLine.Pair("کسٹمر:", f.customer.name)
        f.member?.let { lines += RLine.Pair("فیملی ممبر:", it.name) }
        lines += RLine.Divider()
        lines += RLine.Text("اس بار ڈیلیور ہوئے:", 1f, true)
        now.forEach { lines += RLine.Pair(it.garmentName + " — " + it.memberName, Fmt.num(it.qty)) }
        if (pending.isNotEmpty()) {
            lines += RLine.Text("ابھی باقی ہیں:", 1f, true)
            pending.forEach { lines += RLine.Pair(it.garmentName + " — " + it.memberName, Fmt.num(it.qty)) }
        } else {
            lines += RLine.Text("✓ آرڈر مکمل ڈیلیور ہو گیا", 1f, true, Align.CENTER)
        }
        if (d.forcedComplete) lines += RLine.Text("(مالک کی تصدیق سے مکمل کیا گیا)", 0.8f)
        lines += RLine.Divider()
        lines += RLine.Pair("کل رقم:", m(f.order.netAmount))
        d.paymentTxnId?.let { tid -> repo.txn(tid)?.let { lines += RLine.Pair("ڈیلیوری پر وصولی:", m(it.amount)) } }
        lines += RLine.Pair("کل وصول شدہ:", m(f.paid))
        lines += RLine.Pair("بقایا:", m(f.outstanding), true, 1.15f)
        if (d.notes.isNotBlank()) lines += RLine.Text("نوٹ: " + d.notes, 0.85f)
        lines += footer(s, false, true)
        return ReceiptDoc("ڈیلیوری رسید " + Fmt.deliveryNo(d.id), lines)
    }

    private suspend fun ledger(repo: Repository, s: AppSettings, customerId: Long): ReceiptDoc {
        val l = repo.ledger(customerId)
        val lines = mutableListOf<RLine>()
        lines += header(s, "کھاتہ اسٹیٹمنٹ", null)
        lines += RLine.Pair("کسٹمر:", l.customer.name + " (" + Fmt.customerCode(l.customer.id) + ")", true)
        if (l.customer.mobile.isNotBlank()) lines += RLine.Pair("موبائل:", Fmt.d(l.customer.mobile))
        lines += RLine.Divider()
        val shown = if (l.entries.size > 60) l.entries.takeLast(60) else l.entries
        if (shown.size < l.entries.size) lines += RLine.Text("(صرف آخری ۶۰ اندراجات)", 0.8f)
        shown.forEach { e ->
            lines += RLine.Text(Fmt.date(e.date) + " — " + e.description, 0.85f)
            val amt = when {
                e.debit > 0 -> "+" + Fmt.moneyPlain(e.debit)
                e.credit > 0 -> "−" + Fmt.moneyPlain(e.credit)
                else -> "0"
            }
            lines += RLine.Pair(amt, "بیلنس " + Fmt.moneyPlain(e.balance), false, 0.85f)
        }
        lines += RLine.Divider()
        lines += RLine.Pair("موجودہ بقایا:", m(l.balance), true, 1.2f)
        lines += footer(s, false, false)
        return ReceiptDoc("کھاتہ " + l.customer.name, lines)
    }

    private suspend fun customerInfo(repo: Repository, s: AppSettings, customerId: Long): ReceiptDoc {
        val c = repo.customer(customerId) ?: throw UserError("کسٹمر نہیں ملا")
        val members = repo.members(customerId).filter { it.active }
        val bal = repo.balance(customerId)
        val lines = mutableListOf<RLine>()
        lines += header(s, "کسٹمر معلومات", null)
        lines += RLine.Pair("کوڈ:", Fmt.customerCode(c.id), true)
        lines += RLine.Pair("نام:", c.name, true)
        if (c.fatherName.isNotBlank()) lines += RLine.Pair("ولدیت:", c.fatherName)
        if (c.mobile.isNotBlank()) lines += RLine.Pair("موبائل:", Fmt.d(c.mobile))
        if (c.whatsapp.isNotBlank()) lines += RLine.Pair("واٹس ایپ:", Fmt.d(c.whatsapp))
        if (c.address.isNotBlank()) lines += RLine.Text("پتہ: " + c.address, 0.9f)
        val ca = listOf(c.area, c.city).filter { it.isNotBlank() }.joinToString("، ")
        if (ca.isNotBlank()) lines += RLine.Pair("علاقہ:", ca)
        lines += RLine.Pair("رجسٹریشن:", Fmt.date(c.regDate))
        lines += RLine.Divider()
        lines += RLine.Text("فیملی ممبرز:", 1f, true)
        members.forEach { lines += RLine.Pair(it.name, it.relation) }
        lines += RLine.Divider()
        lines += RLine.Pair("موجودہ بقایا:", m(bal), true)
        lines += footer(s, false, false)
        return ReceiptDoc("کسٹمر " + c.name, lines)
    }

    fun test(s: AppSettings): ReceiptDoc {
        val lines = mutableListOf<RLine>()
        lines += header(s, "ٹیسٹ پرنٹ", null)
        lines += RLine.Text("اردو حروف: ا ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن ں و ہ ھ ء ی ے")
        lines += RLine.Text("جملہ: شلوار قمیض کی سلائی مکمل ہو گئی ہے۔ شکریہ!")
        lines += RLine.Text("انگریزی ہندسے: 0123456789")
        lines += RLine.Text("اردو ہندسے: ۰۱۲۳۴۵۶۷۸۹")
        lines += RLine.Text("علامات: ، ۔ ؟ ! : ( ) - / % +")
        lines += RLine.Text("مخلوط: آرڈر B-00012 کا Total Rs 4,500 ہے، Mobile 0300-1234567")
        lines += RLine.Pair("بائیں / دائیں:", "1,234.50 روپے", true)
        lines += RLine.Text("بڑا متن", 1.6f, true, Align.CENTER)
        lines += RLine.Text("چھوٹا متن", 0.75f, false, Align.CENTER)
        lines += RLine.Divider(false)
        lines += RLine.Text("کاغذ: ${s.paperWidthMm}mm • چوڑائی: ${s.printableDots} ڈاٹس", 0.85f, false, Align.CENTER)
        lines += RLine.Text("اوپر کی لائن دونوں کناروں تک ہونی چاہیے۔ اگر کٹ رہی ہو تو سیٹنگز میں ڈاٹس کم کریں۔", 0.8f)
        lines += RLine.Text("پرنٹ: " + Fmt.dateTime(System.currentTimeMillis()), 0.7f, false, Align.CENTER)
        return ReceiptDoc("ٹیسٹ پرنٹ", lines)
    }
}
