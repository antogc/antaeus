/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotUpdatedException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchPendingInvoicesByCustomerId(id: Int): List<Invoice> {
        return dal.fetchPendingInvoicesByCustomerId(id)
    }

    fun updateInvoiceStatus(invoice: Invoice, status: InvoiceStatus) {
        val update = dal.updateInvoice(invoice, status)
        if (update != 1) {
            throw InvoiceNotUpdatedException(invoice)
        }
    }
}
