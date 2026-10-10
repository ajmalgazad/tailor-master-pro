package pk.tailormaster.pro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.tailormaster.pro.domain.Dates

/**
 * Sample records for testing only. Every sample customer and expense is flagged `sample = true`
 * so that [remove] can delete them (with all their orders, payments and measurements) before real use.
 */
object SampleData {

    suspend fun add(repo: Repository) {
        val g = repo.garments()
        fun gid(name: String) = g.firstOrNull { it.name == name } ?: g.first()
        val sk = gid("شلوار قمیض"); val wc = gid("واسکٹ"); val tr = gid("ٹراؤزر"); val fr = gid("فراک")

        val c1 = repo.saveCustomer(Customer(name = "محمد اسلم (نمونہ)", fatherName = "عبدالرشید", mobile = "03001234567",
            whatsapp = "03001234567", city = "لاہور", area = "اقبال ٹاؤن", category = "مستقل", openingBalance = 50000, sample = true))
        val c2 = repo.saveCustomer(Customer(name = "عائشہ بی بی (نمونہ)", mobile = "03111234567", city = "لاہور", area = "ماڈل ٹاؤن", sample = true))

        val self1 = repo.members(c1).first { it.isSelf }
        val son = repo.saveMember(FamilyMember(customerId = c1, name = "علی", relation = "بیٹا", ageGroup = "بچہ (۱۰ سال)"))
        val father = repo.saveMember(FamilyMember(customerId = c1, name = "عبدالرشید", relation = "والد", fittingPref = "کھلی فٹنگ"))
        val self2 = repo.members(c2).first { it.isSelf }

        suspend fun profile(cid: Long, mid: Long, garment: Garment, name: String, base: Double): Long {
            val fields = repo.fields(garment.id)
            val vals = fields.mapIndexed { i, f -> MeasureValue(f.id, f.label, String.format(java.util.Locale.US, "%.1f", base + i * 0.5)) }
            return repo.saveNewProfile(MeasurementProfile(customerId = cid, memberId = mid, garmentId = garment.id, name = name,
                valuesJson = encodeValues(vals), fitting = "عام", isDefault = true))
        }
        val p1 = profile(c1, self1.id, sk, "شلوار قمیض — عام", 14.0)
        val p2 = profile(c1, self1.id, wc, "واسکٹ", 16.0)
        val p3 = profile(c1, son, sk, "علی کا سوٹ", 9.0)
        val p4 = profile(c1, father, tr, "ٹراؤزر", 18.0)
        val p5 = profile(c2, self2.id, fr, "فراک", 13.0)

        val now = System.currentTimeMillis()
        val o1 = repo.saveOrder(OrderDraft(customerId = c1, memberId = self1.id, bookingDate = now, deliveryDate = Dates.addDays(now, 5),
            items = listOf(
                DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self1.id, memberName = self1.name, qty = 2, rate = sk.defaultRate, profileId = p1),
                DraftItem(garmentId = wc.id, garmentName = wc.name, memberId = self1.id, memberName = self1.name, qty = 1, rate = wc.defaultRate, profileId = p2),
                DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = son, memberName = "علی", qty = 1, rate = 90000, profileId = p3),
                DraftItem(garmentId = tr.id, garmentName = tr.name, memberId = father, memberName = "عبدالرشید", qty = 1, rate = tr.defaultRate, profileId = p4),
            ),
            charges = listOf(Charge("کڑھائی", 50000)), discountInput = 200.0, advance = 200000))
        repo.setOrderStatus(o1, OrderStatus.STITCHING, true, "نمونہ")

        repo.saveOrder(OrderDraft(customerId = c2, memberId = self2.id, bookingDate = Dates.addDays(now, -10), deliveryDate = Dates.addDays(now, -2),
            urgent = true,
            items = listOf(DraftItem(garmentId = fr.id, garmentName = fr.name, memberId = self2.id, memberName = self2.name, qty = 1, rate = fr.defaultRate, profileId = p5)),
            charges = listOf(Charge("ارجنٹ سلائی", 50000)), discountIsPercent = true, discountInput = 5.0, advance = 100000))

        repo.saveExpense(Expense(category = "بجلی کا بل", amount = 450000, notes = "نمونہ", sample = true))
        repo.saveExpense(Expense(category = "دھاگہ اور دیگر سامان", amount = 80000, notes = "نمونہ", sample = true))
        repo.settings.update { it.copy(sampleLoaded = true) }
    }

    suspend fun remove(repo: Repository) = withContext(Dispatchers.IO) {
        val dao = repo.rawDao()
        repo.rawDb().runInTransaction(Runnable {
            val ids = dao.sampleCustomerIds()
            if (ids.isNotEmpty()) {
                val orderIds = dao.orderIdsOf(ids)
                if (orderIds.isNotEmpty()) {
                    dao.deleteItemsOf(orderIds); dao.deleteStatusLogsOf(orderIds); dao.deleteDeliveriesOf(orderIds); dao.deleteOrders(orderIds)
                }
                dao.deleteTxnsOf(ids); dao.deleteProfilesOf(ids); dao.deleteRatesOf(ids); dao.deleteMembersOf(ids); dao.deleteCustomers(ids)
            }
            dao.deleteSampleExpenses()
            dao.insertAudit(AuditLog(entity = "sample", entityId = 0, action = "remove", detail = "customers ${ids.size}"))
        })
        repo.settings.update { it.copy(sampleLoaded = false) }
        repo.bump()
    }
}
