package pk.tailormaster.pro.data

import pk.tailormaster.pro.data.SyncSchema as S

/**
 * Wraps the Room DAO: gives every new record a device-safe id and records every change
 * (insert, update, delete) so it can be sent to the cloud after the transaction commits.
 * Reads pass straight through.
 */
class TrackingDao(private val d: TailorDao, private val db: AppDatabase) : TailorDao by d {

    private fun nextId(table: String) = DeviceIds.next(db.openHelper.writableDatabase, table)

    // ---------------- single inserts ----------------
    override fun insertCustomer(c: Customer): Long { val x = if (c.id == 0L) c.copy(id = nextId("customers")) else c; d.insertCustomer(x); Changes.put(S.customers, x); return x.id }
    override fun insertMember(m: FamilyMember): Long { val x = if (m.id == 0L) m.copy(id = nextId("family_members")) else m; d.insertMember(x); Changes.put(S.members, x); return x.id }
    override fun insertGarment(g: Garment): Long { val x = if (g.id == 0L) g.copy(id = nextId("garments")) else g; d.insertGarment(x); Changes.put(S.garments, x); return x.id }
    override fun insertField(f: MeasurementField): Long { val x = if (f.id == 0L) f.copy(id = nextId("measurement_fields")) else f; d.insertField(x); Changes.put(S.fields, x); return x.id }
    override fun insertProfile(p: MeasurementProfile): Long { val x = if (p.id == 0L) p.copy(id = nextId("measurement_profiles")) else p; d.insertProfile(x); Changes.put(S.profiles, x); return x.id }
    override fun insertRate(r: CustomRate): Long { val x = if (r.id == 0L) r.copy(id = nextId("custom_rates")) else r; d.insertRate(x); Changes.put(S.customRates, x); return x.id }
    override fun insertRateHistory(h: RateHistory): Long { val x = if (h.id == 0L) h.copy(id = nextId("rate_history")) else h; d.insertRateHistory(x); Changes.put(S.rateHistory, x); return x.id }
    override fun insertService(s: ExtraService): Long { val x = if (s.id == 0L) s.copy(id = nextId("services")) else s; d.insertService(x); Changes.put(S.services, x); return x.id }
    override fun insertOrder(o: Order): Long { val x = if (o.id == 0L) o.copy(id = nextId("orders")) else o; d.insertOrder(x); Changes.put(S.orders, x); return x.id }
    override fun insertItem(i: OrderItem): Long { val x = if (i.id == 0L) i.copy(id = nextId("order_items")) else i; d.insertItem(x); Changes.put(S.items, x); return x.id }
    override fun insertStatusLog(s: StatusLog): Long { val x = if (s.id == 0L) s.copy(id = nextId("status_log")) else s; d.insertStatusLog(x); Changes.put(S.statusLogs, x); return x.id }
    override fun insertTxn(t: Txn): Long { val x = if (t.id == 0L) t.copy(id = nextId("txns")) else t; d.insertTxn(x); Changes.put(S.txns, x); return x.id }
    override fun insertDelivery(dl: Delivery): Long { val x = if (dl.id == 0L) dl.copy(id = nextId("deliveries")) else dl; d.insertDelivery(x); Changes.put(S.deliveries, x); return x.id }
    override fun insertExpense(e: Expense): Long { val x = if (e.id == 0L) e.copy(id = nextId("expenses")) else e; d.insertExpense(x); Changes.put(S.expenses, x); return x.id }
    override fun insertPrintLog(p: PrintLog): Long { val x = if (p.id == 0L) p.copy(id = nextId("print_log")) else p; d.insertPrintLog(x); Changes.put(S.printLogs, x); return x.id }
    override fun insertAudit(a: AuditLog): Long { val x = if (a.id == 0L) a.copy(id = nextId("audit_log")) else a; d.insertAudit(x); Changes.put(S.audits, x); return x.id }

    // ---------------- updates ----------------
    override fun updateCustomer(c: Customer) { d.updateCustomer(c); Changes.put(S.customers, c) }
    override fun updateMember(m: FamilyMember) { d.updateMember(m); Changes.put(S.members, m) }
    override fun updateGarment(g: Garment) { d.updateGarment(g); Changes.put(S.garments, g) }
    override fun updateField(f: MeasurementField) { d.updateField(f); Changes.put(S.fields, f) }
    override fun updateProfile(p: MeasurementProfile) { d.updateProfile(p); Changes.put(S.profiles, p) }
    override fun updateRate(r: CustomRate) { d.updateRate(r); Changes.put(S.customRates, r) }
    override fun updateService(s: ExtraService) { d.updateService(s); Changes.put(S.services, s) }
    override fun updateOrder(o: Order) { d.updateOrder(o); Changes.put(S.orders, o) }
    override fun updateItem(i: OrderItem) { d.updateItem(i); Changes.put(S.items, i) }
    override fun updateTxn(t: Txn) { d.updateTxn(t); Changes.put(S.txns, t) }
    override fun updateExpense(e: Expense) { d.updateExpense(e); Changes.put(S.expenses, e) }

    // ---------------- bulk updates (re-read what changed) ----------------
    override fun moveMembers(from: Long, to: Long) { d.moveMembers(from, to); Changes.putAll(S.members, d.members(to)) }
    override fun moveProfiles(from: Long, to: Long) { d.moveProfiles(from, to); Changes.putAll(S.profiles, d.profilesForCustomer(to)) }
    override fun moveOrders(from: Long, to: Long) { d.moveOrders(from, to); Changes.putAll(S.orders, d.ordersForCustomer(to)) }
    override fun moveTxns(from: Long, to: Long) { d.moveTxns(from, to); Changes.putAll(S.txns, d.txnsForCustomer(to)) }
    override fun moveRates(from: Long, to: Long) { d.moveRates(from, to); Changes.putAll(S.customRates, d.ratesOfCustomer(to)) }
    override fun clearDefault(memberId: Long, garmentId: Long) {
        val before = d.defaultProfiles(memberId, garmentId).map { it.id }
        d.clearDefault(memberId, garmentId)
        before.forEach { id -> d.profile(id)?.let { Changes.put(S.profiles, it) } }
    }
    override fun touchProfile(id: Long, at: Long) { d.touchProfile(id, at); d.profile(id)?.let { Changes.put(S.profiles, it) } }
    override fun deleteItems(orderId: Long) { val old = d.items(orderId); d.deleteItems(orderId); Changes.delAll(S.items, old) }

    // ---------------- restore (bulk insert / clear) ----------------
    override fun insertCustomers(l: List<Customer>) { d.insertCustomers(l); Changes.putAll(S.customers, l) }
    override fun insertMembers(l: List<FamilyMember>) { d.insertMembers(l); Changes.putAll(S.members, l) }
    override fun insertGarments(l: List<Garment>) { d.insertGarments(l); Changes.putAll(S.garments, l) }
    override fun insertFields(l: List<MeasurementField>) { d.insertFields(l); Changes.putAll(S.fields, l) }
    override fun insertProfiles(l: List<MeasurementProfile>) { d.insertProfiles(l); Changes.putAll(S.profiles, l) }
    override fun insertRates(l: List<CustomRate>) { d.insertRates(l); Changes.putAll(S.customRates, l) }
    override fun insertRateHistories(l: List<RateHistory>) { d.insertRateHistories(l); Changes.putAll(S.rateHistory, l) }
    override fun insertServices(l: List<ExtraService>) { d.insertServices(l); Changes.putAll(S.services, l) }
    override fun insertOrders(l: List<Order>) { d.insertOrders(l); Changes.putAll(S.orders, l) }
    override fun insertItems(l: List<OrderItem>) { d.insertItems(l); Changes.putAll(S.items, l) }
    override fun insertStatusLogs(l: List<StatusLog>) { d.insertStatusLogs(l); Changes.putAll(S.statusLogs, l) }
    override fun insertTxns(l: List<Txn>) { d.insertTxns(l); Changes.putAll(S.txns, l) }
    override fun insertDeliveries(l: List<Delivery>) { d.insertDeliveries(l); Changes.putAll(S.deliveries, l) }
    override fun insertExpenses(l: List<Expense>) { d.insertExpenses(l); Changes.putAll(S.expenses, l) }
    override fun insertPrintLogs(l: List<PrintLog>) { d.insertPrintLogs(l); Changes.putAll(S.printLogs, l) }
    override fun insertAudits(l: List<AuditLog>) { d.insertAudits(l); Changes.putAll(S.audits, l) }

    override fun clearCustomers() { val o = d.customers(); d.clearCustomers(); Changes.delAll(S.customers, o) }
    override fun clearMembers() { val o = d.allMembers(); d.clearMembers(); Changes.delAll(S.members, o) }
    override fun clearGarments() { val o = d.garments(); d.clearGarments(); Changes.delAll(S.garments, o) }
    override fun clearFields() { val o = d.allFields(); d.clearFields(); Changes.delAll(S.fields, o) }
    override fun clearProfiles() { val o = d.allProfiles(); d.clearProfiles(); Changes.delAll(S.profiles, o) }
    override fun clearRates() { val o = d.allCustomRates(); d.clearRates(); Changes.delAll(S.customRates, o) }
    override fun clearRateHistory() { val o = d.rateHistory(); d.clearRateHistory(); Changes.delAll(S.rateHistory, o) }
    override fun clearServices() { val o = d.services(); d.clearServices(); Changes.delAll(S.services, o) }
    override fun clearOrders() { val o = d.orders(); d.clearOrders(); Changes.delAll(S.orders, o) }
    override fun clearItems() { val o = d.allItems(); d.clearItems(); Changes.delAll(S.items, o) }
    override fun clearStatusLogs() { val o = d.allStatusLogs(); d.clearStatusLogs(); Changes.delAll(S.statusLogs, o) }
    override fun clearTxns() { val o = d.txns(); d.clearTxns(); Changes.delAll(S.txns, o) }
    override fun clearDeliveries() { val o = d.allDeliveries(); d.clearDeliveries(); Changes.delAll(S.deliveries, o) }
    override fun clearExpenses() { val o = d.expenses(); d.clearExpenses(); Changes.delAll(S.expenses, o) }
    override fun clearPrintLogs() { val o = d.allPrintLogs(); d.clearPrintLogs(); Changes.delAll(S.printLogs, o) }
    override fun clearAudits() { val o = d.allAudits(); d.clearAudits(); Changes.delAll(S.audits, o) }

    // ---------------- sample data removal ----------------
    override fun deleteCustomers(ids: List<Long>) { val o = d.customersByIds(ids); d.deleteCustomers(ids); Changes.delAll(S.customers, o) }
    override fun deleteMembersOf(ids: List<Long>) { val o = d.membersOf(ids); d.deleteMembersOf(ids); Changes.delAll(S.members, o) }
    override fun deleteProfilesOf(ids: List<Long>) { val o = d.profilesOf(ids); d.deleteProfilesOf(ids); Changes.delAll(S.profiles, o) }
    override fun deleteRatesOf(ids: List<Long>) { val o = d.ratesOf(ids); d.deleteRatesOf(ids); Changes.delAll(S.customRates, o) }
    override fun deleteOrders(orderIds: List<Long>) { val o = d.ordersByIds(orderIds); d.deleteOrders(orderIds); Changes.delAll(S.orders, o) }
    override fun deleteItemsOf(orderIds: List<Long>) { val o = d.itemsOf(orderIds); d.deleteItemsOf(orderIds); Changes.delAll(S.items, o) }
    override fun deleteStatusLogsOf(orderIds: List<Long>) { val o = d.statusLogsOf(orderIds); d.deleteStatusLogsOf(orderIds); Changes.delAll(S.statusLogs, o) }
    override fun deleteDeliveriesOf(orderIds: List<Long>) { val o = d.deliveriesOf(orderIds); d.deleteDeliveriesOf(orderIds); Changes.delAll(S.deliveries, o) }
    override fun deleteTxnsOf(ids: List<Long>) { val o = d.txnsOf(ids); d.deleteTxnsOf(ids); Changes.delAll(S.txns, o) }
    override fun deleteSampleExpenses() { val o = d.sampleExpenses(); d.deleteSampleExpenses(); Changes.delAll(S.expenses, o) }
}
