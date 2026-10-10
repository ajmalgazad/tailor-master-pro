package pk.tailormaster.pro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TailorDao {

    // ---------------- Customers ----------------
    @Insert fun insertCustomer(c: Customer): Long
    @Update fun updateCustomer(c: Customer)
    @Query("SELECT * FROM customers WHERE id = :id") fun customer(id: Long): Customer?
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE") fun customers(): List<Customer>
    @Query("SELECT * FROM customers WHERE mergedIntoId IS NULL AND id != :exceptId AND (mobile = :mobile OR whatsapp = :mobile) AND :mobile != ''")
    fun customersWithMobile(mobile: String, exceptId: Long): List<Customer>
    @Query("SELECT * FROM customers WHERE mergedIntoId IS NULL AND id != :exceptId AND name = :name AND fatherName = :fatherName")
    fun customersWithName(name: String, fatherName: String, exceptId: Long): List<Customer>

    // ---------------- Family members ----------------
    @Insert fun insertMember(m: FamilyMember): Long
    @Update fun updateMember(m: FamilyMember)
    @Query("SELECT * FROM family_members WHERE id = :id") fun member(id: Long): FamilyMember?
    @Query("SELECT * FROM family_members WHERE customerId = :customerId ORDER BY isSelf DESC, id")
    fun members(customerId: Long): List<FamilyMember>
    @Query("SELECT * FROM family_members") fun allMembers(): List<FamilyMember>
    @Query("UPDATE family_members SET customerId = :to WHERE customerId = :from")
    fun moveMembers(from: Long, to: Long)

    // ---------------- Garments & measurement fields ----------------
    @Insert fun insertGarment(g: Garment): Long
    @Update fun updateGarment(g: Garment)
    @Query("SELECT * FROM garments WHERE id = :id") fun garment(id: Long): Garment?
    @Query("SELECT * FROM garments ORDER BY sortOrder, id") fun garments(): List<Garment>
    @Query("SELECT COUNT(*) FROM garments") fun garmentCount(): Int

    @Insert fun insertField(f: MeasurementField): Long
    @Update fun updateField(f: MeasurementField)
    @Query("SELECT * FROM measurement_fields WHERE garmentId = :garmentId ORDER BY sortOrder, id")
    fun fields(garmentId: Long): List<MeasurementField>
    @Query("SELECT * FROM measurement_fields") fun allFields(): List<MeasurementField>

    // ---------------- Measurement profiles ----------------
    @Insert fun insertProfile(p: MeasurementProfile): Long
    @Update fun updateProfile(p: MeasurementProfile)
    @Query("SELECT * FROM measurement_profiles WHERE id = :id") fun profile(id: Long): MeasurementProfile?
    @Query("SELECT * FROM measurement_profiles WHERE memberId = :memberId ORDER BY archived, date DESC")
    fun profilesForMember(memberId: Long): List<MeasurementProfile>
    @Query("SELECT * FROM measurement_profiles WHERE customerId = :customerId ORDER BY archived, date DESC")
    fun profilesForCustomer(customerId: Long): List<MeasurementProfile>
    @Query("SELECT * FROM measurement_profiles ORDER BY date DESC") fun allProfiles(): List<MeasurementProfile>
    @Query("UPDATE measurement_profiles SET isDefault = 0 WHERE memberId = :memberId AND garmentId = :garmentId")
    fun clearDefault(memberId: Long, garmentId: Long)
    @Query("UPDATE measurement_profiles SET customerId = :to WHERE customerId = :from")
    fun moveProfiles(from: Long, to: Long)
    @Query("UPDATE measurement_profiles SET lastUsed = :at WHERE id = :id")
    fun touchProfile(id: Long, at: Long)

    // ---------------- Rates ----------------
    @Insert fun insertRate(r: CustomRate): Long
    @Update fun updateRate(r: CustomRate)
    @Query("SELECT * FROM custom_rates ORDER BY updatedAt DESC") fun customRates(): List<CustomRate>
    @Query("SELECT * FROM custom_rates WHERE id = :id") fun customRate(id: Long): CustomRate?
    @Query("UPDATE custom_rates SET customerId = :to WHERE customerId = :from")
    fun moveRates(from: Long, to: Long)

    @Insert fun insertRateHistory(h: RateHistory): Long
    @Query("SELECT * FROM rate_history ORDER BY at DESC") fun rateHistory(): List<RateHistory>

    @Insert fun insertService(s: ExtraService): Long
    @Update fun updateService(s: ExtraService)
    @Query("SELECT * FROM services ORDER BY kind, name") fun services(): List<ExtraService>

    // ---------------- Orders ----------------
    @Insert fun insertOrder(o: Order): Long
    @Update fun updateOrder(o: Order)
    @Query("SELECT * FROM orders WHERE id = :id") fun order(id: Long): Order?
    @Query("SELECT * FROM orders ORDER BY id DESC") fun orders(): List<Order>
    @Query("SELECT * FROM orders WHERE customerId = :customerId ORDER BY id DESC")
    fun ordersForCustomer(customerId: Long): List<Order>
    @Query("UPDATE orders SET customerId = :to WHERE customerId = :from")
    fun moveOrders(from: Long, to: Long)

    @Insert fun insertItem(i: OrderItem): Long
    @Update fun updateItem(i: OrderItem)
    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id") fun items(orderId: Long): List<OrderItem>
    @Query("SELECT * FROM order_items WHERE id = :id") fun item(id: Long): OrderItem?
    @Query("SELECT * FROM order_items") fun allItems(): List<OrderItem>
    @Query("DELETE FROM order_items WHERE orderId = :orderId") fun deleteItems(orderId: Long)

    @Insert fun insertStatusLog(s: StatusLog): Long
    @Query("SELECT * FROM status_log WHERE orderId = :orderId ORDER BY at DESC") fun statusLog(orderId: Long): List<StatusLog>

    // ---------------- Money ----------------
    @Insert fun insertTxn(t: Txn): Long
    @Update fun updateTxn(t: Txn)
    @Query("SELECT * FROM txns WHERE id = :id") fun txn(id: Long): Txn?
    @Query("SELECT * FROM txns ORDER BY date DESC, id DESC") fun txns(): List<Txn>
    @Query("SELECT * FROM txns WHERE customerId = :customerId ORDER BY date, id") fun txnsForCustomer(customerId: Long): List<Txn>
    @Query("SELECT * FROM txns WHERE orderId = :orderId ORDER BY date, id") fun txnsForOrder(orderId: Long): List<Txn>
    @Query("UPDATE txns SET customerId = :to WHERE customerId = :from")
    fun moveTxns(from: Long, to: Long)

    @Insert fun insertDelivery(d: Delivery): Long
    @Query("SELECT * FROM deliveries WHERE id = :id") fun delivery(id: Long): Delivery?
    @Query("SELECT * FROM deliveries WHERE orderId = :orderId ORDER BY date") fun deliveries(orderId: Long): List<Delivery>
    @Query("SELECT * FROM deliveries ORDER BY date DESC") fun allDeliveries(): List<Delivery>

    @Insert fun insertExpense(e: Expense): Long
    @Update fun updateExpense(e: Expense)
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC") fun expenses(): List<Expense>

    // ---------------- Logs ----------------
    @Insert fun insertPrintLog(p: PrintLog): Long
    @Query("SELECT * FROM print_log ORDER BY at DESC LIMIT :limit") fun printLogs(limit: Int): List<PrintLog>
    @Query("SELECT * FROM print_log") fun allPrintLogs(): List<PrintLog>

    @Insert fun insertAudit(a: AuditLog): Long
    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit") fun audits(limit: Int): List<AuditLog>
    @Query("SELECT * FROM audit_log") fun allAudits(): List<AuditLog>

    // ---------------- Backup / restore (bulk) ----------------
    @Query("SELECT * FROM custom_rates") fun allCustomRates(): List<CustomRate>
    @Query("SELECT * FROM status_log") fun allStatusLogs(): List<StatusLog>

    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertCustomers(l: List<Customer>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertMembers(l: List<FamilyMember>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertGarments(l: List<Garment>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertFields(l: List<MeasurementField>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProfiles(l: List<MeasurementProfile>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRates(l: List<CustomRate>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRateHistories(l: List<RateHistory>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertServices(l: List<ExtraService>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertOrders(l: List<Order>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertItems(l: List<OrderItem>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertStatusLogs(l: List<StatusLog>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertTxns(l: List<Txn>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertDeliveries(l: List<Delivery>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertExpenses(l: List<Expense>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertPrintLogs(l: List<PrintLog>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAudits(l: List<AuditLog>)

    @Query("DELETE FROM customers") fun clearCustomers()
    @Query("DELETE FROM family_members") fun clearMembers()
    @Query("DELETE FROM garments") fun clearGarments()
    @Query("DELETE FROM measurement_fields") fun clearFields()
    @Query("DELETE FROM measurement_profiles") fun clearProfiles()
    @Query("DELETE FROM custom_rates") fun clearRates()
    @Query("DELETE FROM rate_history") fun clearRateHistory()
    @Query("DELETE FROM services") fun clearServices()
    @Query("DELETE FROM orders") fun clearOrders()
    @Query("DELETE FROM order_items") fun clearItems()
    @Query("DELETE FROM status_log") fun clearStatusLogs()
    @Query("DELETE FROM txns") fun clearTxns()
    @Query("DELETE FROM deliveries") fun clearDeliveries()
    @Query("DELETE FROM expenses") fun clearExpenses()
    @Query("DELETE FROM print_log") fun clearPrintLogs()
    @Query("DELETE FROM audit_log") fun clearAudits()

    // ---------------- Sample data removal ----------------
    @Query("SELECT id FROM customers WHERE sample = 1") fun sampleCustomerIds(): List<Long>
    @Query("DELETE FROM customers WHERE id IN (:ids)") fun deleteCustomers(ids: List<Long>)
    @Query("DELETE FROM family_members WHERE customerId IN (:ids)") fun deleteMembersOf(ids: List<Long>)
    @Query("DELETE FROM measurement_profiles WHERE customerId IN (:ids)") fun deleteProfilesOf(ids: List<Long>)
    @Query("DELETE FROM custom_rates WHERE customerId IN (:ids)") fun deleteRatesOf(ids: List<Long>)
    @Query("SELECT id FROM orders WHERE customerId IN (:ids)") fun orderIdsOf(ids: List<Long>): List<Long>
    @Query("DELETE FROM orders WHERE id IN (:orderIds)") fun deleteOrders(orderIds: List<Long>)
    @Query("DELETE FROM order_items WHERE orderId IN (:orderIds)") fun deleteItemsOf(orderIds: List<Long>)
    @Query("DELETE FROM status_log WHERE orderId IN (:orderIds)") fun deleteStatusLogsOf(orderIds: List<Long>)
    @Query("DELETE FROM deliveries WHERE orderId IN (:orderIds)") fun deleteDeliveriesOf(orderIds: List<Long>)
    @Query("DELETE FROM txns WHERE customerId IN (:ids)") fun deleteTxnsOf(ids: List<Long>)
    @Query("DELETE FROM expenses WHERE sample = 1") fun deleteSampleExpenses()
}
