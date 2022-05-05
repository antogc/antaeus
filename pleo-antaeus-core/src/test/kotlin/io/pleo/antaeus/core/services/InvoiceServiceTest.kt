package io.pleo.antaeus.core.services

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

class InvoiceServiceTest {

    private var dal = mockk<AntaeusDal>()
    private val invoice = mockk<Invoice> {
        every { id } returns 1
    }

    private val invoiceService = InvoiceService(dal = dal)

    @Test
    fun `will throw if invoice is not found`() {
        expectFetchInvoice404()

        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `will throw if invoice is not updated`() {
        expectInvoiceIsNotUpdated()

        assertThrows<InvoiceNotUpdatedException> {
            invoiceService.updateInvoiceStatus(invoice, InvoiceStatus.PAID)
        }

        verify { dal.updateInvoice(invoice, InvoiceStatus.PAID) }
        confirmVerified(dal)
    }

    private fun expectFetchInvoice404() {
        every { dal.fetchInvoice(404) } returns null
    }

    private fun expectInvoiceIsNotUpdated() {
        every { dal.updateInvoice(any(), InvoiceStatus.PAID) } returns -1
    }
}
