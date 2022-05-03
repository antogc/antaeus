package io.pleo.antaeus.core.exceptions

import io.pleo.antaeus.models.Invoice

class InvoiceNotUpdatedException(invoice: Invoice) : Exception("Invoice ${invoice.id} was not updated")
