package pk.tailormaster.pro

import org.junit.Assert.assertEquals
import org.junit.Test
import pk.tailormaster.pro.data.JobStatus
import pk.tailormaster.pro.data.WorkJob
import pk.tailormaster.pro.data.WorkerCalc
import pk.tailormaster.pro.data.WorkerPayment

class WorkerCalcTest {
    private fun job(amount: Long, status: String) = WorkJob(workerId = 1, orderId = 1, itemId = 1, garmentName = "x", amount = amount, status = status)

    @Test fun balanceCountsOnlyFinishedWorkAndLivePayments() {
        val jobs = listOf(job(50000, JobStatus.DONE), job(30000, JobStatus.ASSIGNED), job(20000, JobStatus.CANCELLED), job(10000, JobStatus.DONE))
        val pays = listOf(WorkerPayment(workerId = 1, amount = 40000), WorkerPayment(workerId = 1, amount = 99999, voided = true))
        assertEquals(60000L, WorkerCalc.earned(jobs))
        assertEquals(30000L, WorkerCalc.pendingAmount(jobs))
        assertEquals(40000L, WorkerCalc.paid(pays))
        assertEquals(20000L, WorkerCalc.balance(jobs, pays))
    }
}
