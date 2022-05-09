package io.pleo.antaeus.core.services

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotUpdatedException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val CUSTOMER_ID = 1
private const val CUSTOMER_404 = 404

class InvoiceServiceTest {
    private var dal = mockk<AntaeusDal>()
    private val anInvoice = mockk<Invoice> {
        every { id } returns 1
    }

    private val invoiceService = InvoiceService(dal = dal)

    @Test
    fun `will throw if invoice is not found`() {
        expectFetchInvoice404()

        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(CUSTOMER_404)
        }
    }

    @Test
    fun `will fetch invoices by customer id and status pending`() {
        expectInvoicesByCustomerIdAndPendingStatus()

        val invoices = invoiceService.fetchPendingInvoicesByCustomerId(CUSTOMER_ID)

        invoices shouldHaveSize 1
        invoices[0].id shouldBe CUSTOMER_ID
        verify { dal.fetchInvoicesByCustomerIdAndStatus(CUSTOMER_ID, InvoiceStatus.PENDING) }
        confirmVerified(dal)
    }

    @Test
    fun `will throw if invoice is not updated`() {
        expectInvoiceIsNotUpdated()

        assertThrows<InvoiceNotUpdatedException> {
            invoiceService.updateInvoiceStatus(anInvoice, InvoiceStatus.PAID)
        }

        verify { dal.updateInvoice(anInvoice, InvoiceStatus.PAID) }
        confirmVerified(dal)
    }

    private fun expectFetchInvoice404() {
        every { dal.fetchInvoice(CUSTOMER_404) } returns null
    }

    private fun expectInvoicesByCustomerIdAndPendingStatus() {
        every { dal.fetchInvoicesByCustomerIdAndStatus(CUSTOMER_ID, InvoiceStatus.PENDING) } returns listOf(anInvoice)
    }

    private fun expectInvoiceIsNotUpdated() {
        every { dal.updateInvoice(any(), InvoiceStatus.PAID) } returns -1
    }
}
